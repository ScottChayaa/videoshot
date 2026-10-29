package com.xenyaa.videoshot.publish

import com.xenyaa.videoshot.core.backup.UploadChunk
import com.xenyaa.videoshot.core.backup.parseReceivedBytes
import com.xenyaa.videoshot.core.publish.UploadErrorKind
import com.xenyaa.videoshot.core.publish.classifyUploadError
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private const val VIDEO_MIME = "video/mp4"

/**
 * [YoutubeUploader] 的真正實作：跟 `DriveBackupStore` 一樣直接用 OkHttp 打 REST。
 *
 * @param baseUrl 測試時指向 MockWebServer；正式環境用預設值。
 */
class OkHttpYoutubeUploader(
    private val http: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val baseUrl: String = "https://www.googleapis.com",
) : YoutubeUploader {

    override suspend fun startSession(token: String, title: String, sizeBytes: Long): String = withContext(io) {
        val metadata = buildJsonObject {
            putJsonObject("snippet") {
                put("title", title)
                put("description", "")
                put("categoryId", "22")
            }
            putJsonObject("status") {
                put("privacyStatus", "unlisted")
                put("selfDeclaredMadeForKids", false)
            }
        }
        val request = Request.Builder()
            .url("$baseUrl/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", VIDEO_MIME)
            .header("X-Upload-Content-Length", sizeBytes.toString())
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        call(request) { response ->
            if (!response.isSuccessful) throw response.toException()
            response.header("Location") ?: throw UploadHttpException(response.code, UploadErrorKind.FATAL)
        }
    }

    override suspend fun query(sessionUrl: String, token: String, sizeBytes: Long): UploadStatus = withContext(io) {
        val request = Request.Builder()
            .url(sessionUrl)
            .header("Authorization", "Bearer $token")
            .header("Content-Range", "bytes */$sizeBytes")
            .put(ByteArray(0).toRequestBody(null))
            .build()
        call(request) { it.toStatus() }
    }

    override suspend fun putChunk(
        sessionUrl: String,
        token: String,
        file: File,
        chunk: UploadChunk,
    ): UploadStatus = withContext(io) {
        val bytes = file.readRange(chunk.start, chunk.length)
        val request = Request.Builder()
            .url(sessionUrl)
            .header("Authorization", "Bearer $token")
            .header("Content-Range", chunk.contentRangeHeader())
            .put(bytes.toRequestBody(VIDEO_MIME.toMediaType()))
            .build()
        call(request) { it.toStatus() }
    }

    /**
     * 送出請求並在 [block] 裡讀回應。協程取消時立刻 `call.cancel()`，進行中的請求（含讀 body）不用等傳完；
     * 取消造成的 IOException 會換成 `CancellationException`，其餘 IOException 原樣往外丟。
     */
    private suspend fun <T> call(request: Request, block: (Response) -> T): T = withContext(io) {
        coroutineScope {
            val call = http.newCall(request)
            // Unconfined：取消當下就在取消者的執行緒上 cancel，不必等 io 執行緒（它正卡在阻塞的 execute）
            val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use(block)
            } catch (e: IOException) {
                ensureActive()
                throw e
            } finally {
                watcher.cancel()
            }
        }
    }

    private fun Response.toStatus(): UploadStatus = when (code) {
        308 -> UploadStatus.Partial(parseReceivedBytes(header("Range")))
        200, 201 -> {
            // 讀 body 的 IOException 要往外丟（流程層當斷網處理）；只有 JSON 解析失敗才算沒有 id
            val text = body.string()
            val id = runCatching {
                Json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.content
            }.getOrNull()
            UploadStatus.Done(id ?: throw UploadHttpException(code, UploadErrorKind.FATAL))
        }
        else -> throw toException()
    }

    private fun Response.toException(): UploadHttpException {
        val text = body.string()
        return UploadHttpException(code, classifyUploadError(code, text))
    }
}

private fun File.readRange(start: Long, length: Long): ByteArray =
    RandomAccessFile(this, "r").use { raf ->
        raf.seek(start)
        val buffer = ByteArray(length.toInt())
        raf.readFully(buffer)
        buffer
    }
