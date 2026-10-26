package com.xenyaa.videoshot.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 素材格子的縮圖：按需解碼，記憶體裡留一份 LRU。
 *
 * 做法同 `ui/thumb/ThumbLoader`（in-flight 表＋LRU），只是來源是手機的 MediaStore，
 * 快取鍵是 [MediaItem.id]。解碼函式由外面注入，測試不必準備真的檔案；
 * 正式環境用 [mediaStoreThumbDecoder]。
 *
 * @param maxEntries 記憶體 LRU 的容量
 */
class MediaThumbLoader(
    private val decode: suspend (MediaItem) -> ImageBitmap?,
    private val maxEntries: Int = 200,
) {

    /** 管 [inFlight]。[cache] 另外用 `synchronized(cache)`：[peek] 是在主執行緒同步呼叫的，拿不了 [Mutex]。 */
    private val lock = Mutex()

    private val cache = object : LinkedHashMap<Long, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>) = size > maxEntries
    }

    /** 進行中的請求。捲動時同一格會被問很多次，沒有這張表就會重複解碼同一張圖。 */
    private val inFlight = mutableMapOf<Long, CompletableDeferred<ImageBitmap?>>()

    /** 解碼失敗回 null（不寫進快取，下次再試）——呼叫端畫中性的預留色塊，不是破圖。 */
    suspend fun load(item: MediaItem): ImageBitmap? {
        var mine = false
        val pending = lock.withLock {
            peek(item.id)?.let { return it }
            inFlight[item.id] ?: CompletableDeferred<ImageBitmap?>().also {
                inFlight[item.id] = it
                mine = true
            }
        }
        if (!mine) return pending.await()

        val bitmap = try {
            decode(item)
        } catch (c: CancellationException) {
            // 捲動離開畫面是常態，不是解碼失敗。清理用 NonCancellable 包住，
            // 否則鎖剛好被占用時會留下孤兒的 inFlight 紀錄。
            // 對其他等待者用 complete(null) 而不是 cancel：別人的取消不該傳染給我，
            // 他們這一輪先看到預留色塊，下一次 load() 會重新解碼。
            withContext(NonCancellable) { lock.withLock { inFlight.remove(item.id) } }
            pending.complete(null)
            throw c
        } catch (t: Throwable) {
            // 壞掉的一張圖不該讓整面縮圖牆掛掉
            null
        }
        lock.withLock {
            if (bitmap != null) synchronized(cache) { cache[item.id] = bitmap }
            inFlight.remove(item.id)
        }
        pending.complete(bitmap)
        return bitmap
    }

    /** 同步查記憶體快取，沒有就回 null，**不解碼**。給畫面當第一幀的初始值。 */
    fun peek(id: Long): ImageBitmap? = synchronized(cache) { cache[id] }
}

/**
 * 正式環境的縮圖解碼：API 29 起用 `loadThumbnail`（系統會快取、也處理旋轉）；
 * 29 以下用舊的 `Thumbnails.getThumbnail`。任何例外（檔案不見了、權限被收回）都回 null。
 */
fun mediaStoreThumbDecoder(
    resolver: ContentResolver,
    io: CoroutineDispatcher,
): suspend (MediaItem) -> ImageBitmap? = { item ->
    withContext(io) {
        try {
            val bitmap: Bitmap? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.loadThumbnail(Uri.parse(item.uri), Size(256, 256), null)
                } else if (item.kind == MediaKind.VIDEO) {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        resolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(
                        resolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null,
                    )
                }
            bitmap?.asImageBitmap()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            null
        }
    }
}
