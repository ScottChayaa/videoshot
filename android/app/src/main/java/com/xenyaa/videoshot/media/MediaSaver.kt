package com.xenyaa.videoshot.media

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 相簿裡 app 專屬的資料夾（在 `Movies/` 底下）。 */
private const val GALLERY_FOLDER = "videoshot"

/**
 * 剪輯成品在相簿裡的檔名：標題裡檔名不能用的字元（`/ \ : * ? " < > |`）換成 `-`，再加 `.mp4`。
 * 預設標題有「/」（例如「videoshot 2026/10/20～10/27」），不換掉會變成子資料夾或寫入失敗。
 *
 * 檔名上限是 255 bytes（UTF-8，中文字一個 3 bytes）：標題太長就整個字整個字截掉，
 * 留下 `.mp4` 與同名時相簿接上的「 (999)」的空間。
 */
fun galleryFileName(title: String): String {
    val base = title.replace(Regex("""[/\\:*?"<>|]"""), "-")
    return truncateUtf8(base, MAX_NAME_BYTES - ".mp4".length - " (999)".length) + ".mp4"
}

/** 檔名（含副檔名）的位元組上限。 */
private const val MAX_NAME_BYTES = 255

/** [text] 的 UTF-8 不超過 [maxBytes]：從頭一個字（code point）一個字收，不會切開半個字或半個代理對。 */
private fun truncateUtf8(text: String, maxBytes: Int): String {
    var bytes = 0
    var end = 0
    while (end < text.length) {
        val cp = text.codePointAt(end)
        val size = when {
            cp < 0x80 -> 1
            cp < 0x800 -> 2
            cp < 0x10000 -> 3
            else -> 4
        }
        if (bytes + size > maxBytes) break
        bytes += size
        end += Character.charCount(cp)
    }
    return text.substring(0, end)
}

/** 把 app 輸出的影片存進手機相簿。畫面與輸出流程只碰這個介面，不直接碰 `ContentResolver`。 */
interface MediaSaver {
    /**
     * 把 [file] 存進相簿 `Movies/videoshot/`，檔名由 [title] 決定（見 [galleryFileName]）。
     * 成功回 content URI 字串；失敗回 null，不丟例外（取消例外照樣往外丟）。
     */
    suspend fun saveVideo(file: File, title: String): String?
}

/**
 * 以 MediaStore 實作的 [MediaSaver]。
 *
 * - API 29 起：插入一列（`RELATIVE_PATH`＝`Movies/videoshot`、`IS_PENDING`＝1）→ 寫入內容 → `IS_PENDING` 改 0，
 *   寫完之前相簿不會顯示半成品；寫失敗就把那一列刪掉。不需要儲存權限。
 * - API 28 以下：自己把檔案複製到 `Movies/videoshot/`，再用 `DATA` 插入一列讓相簿看得到（要 `WRITE_EXTERNAL_STORAGE`）。
 */
class MediaStoreSaver(
    private val resolver: ContentResolver,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MediaSaver {

    override suspend fun saveVideo(file: File, title: String): String? = withContext(io) {
        try {
            if (sdkInt >= Build.VERSION_CODES.Q) saveScoped(file, title) else saveLegacy(file, title)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 沒權限、空間不足、提供者出錯……都回 null，由呼叫端決定怎麼告訴使用者
            null
        }
    }

    private fun saveScoped(file: File, title: String): String? {
        val table = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, galleryFileName(title))
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_MP4)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$GALLERY_FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val row = resolver.insert(table, values) ?: return null
        try {
            val out = resolver.openOutputStream(row) ?: throw IOException("開不了輸出串流")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            resolver.update(row, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return row.toString()
        } catch (e: Throwable) {
            // 寫失敗（含被取消）：不留下 pending 的空殼
            runCatching { resolver.delete(row, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION") // DATA 在 API 29 起棄用；這條路只給 API 28 以下
    private fun saveLegacy(file: File, title: String): String? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), GALLERY_FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val name = galleryFileName(title)
        // 同名檔案已存在就加 (1)、(2)…，不蓋掉先前存的
        var target = File(dir, name)
        var n = 1
        while (target.exists()) target = File(dir, "${name.removeSuffix(".mp4")} (${n++}).mp4")
        try {
            file.copyTo(target)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, target.absolutePath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
                put(MediaStore.MediaColumns.TITLE, target.nameWithoutExtension)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_MP4)
                put(MediaStore.MediaColumns.SIZE, target.length())
            }
            val row: Uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("相簿不收這個檔案")
            return row.toString()
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    private companion object {
        const val MIME_MP4 = "video/mp4"
    }
}
