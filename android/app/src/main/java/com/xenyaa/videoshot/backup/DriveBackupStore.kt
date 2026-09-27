package com.xenyaa.videoshot.backup

import com.xenyaa.videoshot.core.backup.nextChunk
import com.xenyaa.videoshot.core.backup.parseReceivedBytes
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private const val DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024
private const val MAX_RETRIES = 5

/**
 * Drive REST v3 的真正實作,只碰 appDataFolder(規格第十節「只要求 `drive.appdata`」)。
 * 不用 Drive SDK——跟 `OkHttpYoutube`/`OkHttpGeminiClient` 一樣直接打 REST,符合技術選型表
 * 「HTTP: OkHttp」,也少一層 SDK 相依。
 *
 * @param accessToken 每次呼叫前取得目前有效的存取權杖;這裡不快取,過期與重新取得交給呼叫端
 *        (`GoogleAuth.accessToken`)。
 * @param baseUrl 測試時指向 MockWebServer;正式環境用預設值。
 * @param chunkSize 續傳上傳每一塊的大小;測試用小值逼出多 chunk 的路徑。
 */
class DriveBackupStore(
    private val http: OkHttpClient,
    private val accessToken: suspend () -> String,
    private val io: CoroutineDispatcher,
    private val baseUrl: String = "https://www.googleapis.com",
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : BackupStore {

    override suspend fun list(): List<RemoteBackup> = withContext(io) {
        val url = "$baseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("fields", "files(id,name,createdTime,size,appProperties)")
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("pageSize", "10")
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${accessToken()}").build()
        execute(request).use { response ->
            check(response.isSuccessful) { "列出備份失敗: ${response.code}" }
            val body = Json.parseToJsonElement(response.body!!.string()).jsonObject
            body["files"]?.jsonArray.orEmpty().map { it.jsonObject.toRemoteBackup() }
        }
    }

    override suspend fun upload(
        backup: NewBackup,
        onProgress: (Long, Long) -> Unit,
    ): RemoteBackup = withContext(io) {
        val total = backup.file.length()
        val metadata = buildJsonObject {
            put("name", backup.file.name)
            putJsonArray("parents") { add(JsonPrimitive("appDataFolder")) }
            putJsonObject("appProperties") {
                put("schemaVersion", backup.schemaVersion.toString())
                put("shotCount", backup.shotCount.toString())
                put("deviceName", backup.deviceName)
                put("sha256", backup.sha256)
            }
        }
        val initRequest = Request.Builder()
            .url("$baseUrl/upload/drive/v3/files?uploadType=resumable")
            .header("Authorization", "Bearer ${accessToken()}")
            .header("X-Upload-Content-Type", "application/gzip")
            .header("X-Upload-Content-Length", total.toString())
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        val sessionUri = execute(initRequest).use { response ->
            check(response.isSuccessful) { "初始化續傳上傳失敗: ${response.code}" }
            response.header("Location") ?: error("Drive 沒有回傳續傳網址")
        }

        var offset = 0L
        var attempt = 0
        var resultJson: JsonObject? = null
        while (resultJson == null) {
            try {
                val chunk = nextChunk(offset, total, chunkSize)
                val bytes = backup.file.readRange(chunk.start, chunk.length)
                val putRequest = Request.Builder()
                    .url(sessionUri)
                    .header("Content-Range", chunk.contentRangeHeader())
                    .put(bytes.toRequestBody("application/gzip".toMediaType()))
                    .build()
                execute(putRequest).use { response ->
                    when {
                        response.code == 308 -> {
                            offset = parseReceivedBytes(response.header("Range"))
                            onProgress(offset, total)
                        }
                        response.isSuccessful -> {
                            resultJson = Json.parseToJsonElement(response.body!!.string()).jsonObject
                            onProgress(total, total)
                        }
                        else -> error("上傳失敗: ${response.code}")
                    }
                }
            } catch (e: IOException) {
                attempt++
                if (attempt > MAX_RETRIES) throw e
                when (val query = queryResumeOffset(sessionUri, total)) {
                    // Drive 其實已經收滿全部位元組(通常是最後一個 chunk 剛好在讀回應時斷線)——
                    // 直接把查詢回應裡的檔案資源當作上傳結果,不要再繞回 nextChunk(total, total, ...)
                    // 否則會因為 fromByte >= total 丟 IllegalArgumentException,把一次「其實已經
                    // 成功」的上傳誤判成失敗。
                    is ResumeQuery.Completed -> resultJson = query.result
                    is ResumeQuery.StillUploading -> offset = query.receivedBytes
                }
            }
        }
        checkNotNull(resultJson).toRemoteBackupFromUploadResult(backup)
    }

    /** [queryResumeOffset] 的查詢結果:Drive 可能回報「還沒收完,從這裡繼續」,也可能回報「其實已經收完了」。 */
    private sealed interface ResumeQuery {
        data class StillUploading(val receivedBytes: Long) : ResumeQuery
        data class Completed(val result: JsonObject) : ResumeQuery
    }

    private suspend fun queryResumeOffset(sessionUri: String, total: Long): ResumeQuery {
        val request = Request.Builder()
            .url(sessionUri)
            .header("Content-Range", "bytes */$total")
            .put(ByteArray(0).toRequestBody(null))
            .build()
        return execute(request).use { response ->
            if (response.isSuccessful) {
                ResumeQuery.Completed(Json.parseToJsonElement(response.body!!.string()).jsonObject)
            } else {
                ResumeQuery.StillUploading(parseReceivedBytes(response.header("Range")))
            }
        }
    }

    override suspend fun download(
        id: String,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(io) {
        val request = Request.Builder()
            .url("$baseUrl/drive/v3/files/$id?alt=media")
            .header("Authorization", "Bearer ${accessToken()}")
            .build()
        execute(request).use { response ->
            check(response.isSuccessful) { "下載失敗: ${response.code}" }
            val body = checkNotNull(response.body)
            val total = body.contentLength()
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var received = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }
        }
    }

    override suspend fun delete(id: String) = withContext(io) {
        val request = Request.Builder()
            .url("$baseUrl/drive/v3/files/$id")
            .header("Authorization", "Bearer ${accessToken()}")
            .delete()
            .build()
        execute(request).use { response ->
            check(response.isSuccessful || response.code == 404) { "刪除失敗: ${response.code}" }
        }
    }

    private fun execute(request: Request): Response = http.newCall(request).execute()

    private fun JsonObject.toRemoteBackup(): RemoteBackup {
        val props = this["appProperties"]?.jsonObject
        return RemoteBackup(
            id = this["id"]!!.jsonPrimitive.content,
            name = this["name"]!!.jsonPrimitive.content,
            createdAtEpochSec = Instant.parse(this["createdTime"]!!.jsonPrimitive.content).epochSecond,
            sizeBytes = this["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            schemaVersion = props?.get("schemaVersion")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            shotCount = props?.get("shotCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            deviceName = props?.get("deviceName")?.jsonPrimitive?.content ?: "",
            sha256 = props?.get("sha256")?.jsonPrimitive?.content ?: "",
        )
    }

    /** 上傳完成後的 Drive 回應通常只含少數欄位(除非額外要求 `fields`)——直接用本機已知的中繼資料組回傳值,只取回應裡的 `id`。 */
    private fun JsonObject.toRemoteBackupFromUploadResult(source: NewBackup): RemoteBackup = RemoteBackup(
        id = this["id"]!!.jsonPrimitive.content,
        name = source.file.name,
        createdAtEpochSec = source.createdAtEpochSec,
        sizeBytes = source.file.length(),
        schemaVersion = source.schemaVersion,
        shotCount = source.shotCount,
        deviceName = source.deviceName,
        sha256 = source.sha256,
    )
}

private fun File.readRange(start: Long, length: Long): ByteArray =
    RandomAccessFile(this, "r").use { raf ->
        raf.seek(start)
        val buffer = ByteArray(length.toInt())
        raf.readFully(buffer)
        buffer
    }
