package com.xenyaa.videoshot.ui.thumb

import androidx.compose.ui.graphics.ImageBitmap
import com.xenyaa.videoshot.thumbs.coverUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 影片封面（`i.ytimg.com/vi/{id}/hqdefault.jpg`）。縮圖還沒回填時頂著用（規格第五節）。
 *
 * 磁碟快取放 **cacheDir**（不是 filesDir）—— 封面隨時抓得回來，是典型可重建的資料，
 * 系統清快取清掉它完全沒關係。同一支影片的所有格子共用一張封面，所以以 videoId 為檔名。
 */
class CoverFetcher(
    private val client: OkHttpClient,
    private val dir: File,
    private val io: CoroutineDispatcher,
    private val decode: (ByteArray) -> ImageBitmap?,
) {
    suspend operator fun invoke(videoId: String): ImageBitmap? = withContext(io) {
        val cached = File(dir, "$videoId.jpg")
        if (cached.exists()) return@withContext decode(cached.readBytes())
        val bytes = try {
            client.newCall(Request.Builder().url(coverUrl(videoId)).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body.bytes()
            }
        } catch (e: java.io.IOException) {
            // 沒網路就先沒有封面，下次捲到還會再試 —— 這裡不該讓首頁出現錯誤訊息
            return@withContext null
        }
        runCatching {
            dir.mkdirs()
            cached.writeBytes(bytes)
        }
        decode(bytes)
    }
}
