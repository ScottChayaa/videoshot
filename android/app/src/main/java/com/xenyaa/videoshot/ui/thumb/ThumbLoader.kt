package com.xenyaa.videoshot.ui.thumb

import androidx.compose.ui.graphics.ImageBitmap
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 一張圖從 [ThumbSource] 到畫得出來的 [ImageBitmap]。
 *
 * 為什麼自己寫而不是用 Coil：`thumbFor(shot)` 是規格第三節訂的**唯一入口**，
 * 而 Coil 的資料來源是 URL／File／ByteArray —— 要保住那個邊界就得替它寫一層自訂 Fetcher，
 * 省下來的並不比這個檔案多。首頁、階段 8 的資料夾拼貼、階段 9 的收藏網格共用這一份。
 *
 * 解碼函式全部由外面注入：測試不必準備真的檔案，正式環境才綁 BitmapFactory 與 OkHttp。
 *
 * @param maxEntries 記憶體 LRU 的容量。由 AppContainer 依 `ActivityManager.memoryClass` 估算
 */
class ThumbLoader(
    private val thumbs: Thumbs,
    private val decodeFile: suspend (File) -> ImageBitmap?,
    private val decodeBytes: suspend (ByteArray) -> ImageBitmap?,
    private val cover: suspend (String) -> ImageBitmap?,
    private val maxEntries: Int = 120,
) {

    private val lock = Mutex()

    private val cache = object : LinkedHashMap<Long, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>) = size > maxEntries
    }

    /** 進行中的請求。捲動時同一格會被問很多次，沒有這張表就會重複解碼同一張圖。 */
    private val inFlight = mutableMapOf<Long, CompletableDeferred<ImageBitmap?>>()

    /** 拿不到圖時回 null —— 呼叫端畫中性的預留圖，不是破圖（手冊 §一）。 */
    suspend fun load(shot: ShotRow): ImageBitmap? {
        var mine = false
        val pending = lock.withLock {
            cache[shot.id]?.let { return it }
            inFlight[shot.id] ?: CompletableDeferred<ImageBitmap?>().also {
                inFlight[shot.id] = it
                mine = true
            }
        }
        if (!mine) return pending.await()

        val bitmap = try {
            decode(shot)
        } catch (t: Throwable) {
            // 壞掉的一張圖不該讓整個縮圖牆掛掉（規格第七節：降級不當機）
            null
        }
        lock.withLock {
            if (bitmap != null) cache[shot.id] = bitmap
            inFlight.remove(shot.id)
        }
        pending.complete(bitmap)
        return bitmap
    }

    /** 刪掉一張圖之後叫一次，否則同一個 id 的舊圖會留在記憶體裡。 */
    suspend fun evict(shotId: Long) {
        lock.withLock { cache.remove(shotId) }
    }

    private suspend fun decode(shot: ShotRow): ImageBitmap? = when (val source = thumbs.thumbFor(shot)) {
        is ThumbSource.LocalFile -> decodeFile(source.file)
        is ThumbSource.Bytes -> decodeBytes(source.webp)
        is ThumbSource.Cover -> cover(source.videoId)
        is ThumbSource.Placeholder -> null
    }
}
