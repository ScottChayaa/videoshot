package com.xenyaa.videoshot.thumbs

import com.xenyaa.videoshot.data.repo.model.ShotRow
import java.io.File

/**
 * 一張圖從哪裡來。畫面只管畫，不必知道背後是檔案、BLOB 還是降級的替代圖
 * （規格第三節模組邊界第 2 條）。
 */
sealed interface ThumbSource {
    /** storyboard 裁出來的單格，存在 thumbs/ 底下 */
    data class LocalFile(val file: File) : ThumbSource

    /** 手動補圖，存在 library.db 的 shot_image */
    data class Bytes(val webp: ByteArray) : ThumbSource {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Bytes && webp.contentEquals(other.webp))

        override fun hashCode(): Int = webp.contentHashCode()
    }

    /** 縮圖還沒回填：先顯示影片封面（無簽章、不會過期） */
    data class Cover(val videoId: String) : ThumbSource

    /** 無法取回：顯示預留圖（規格第七節、第十一節的 lost 狀態） */
    data object Placeholder : ThumbSource
}

/** **縮圖的讀寫只走這裡**（規格第三節模組邊界第 2 條）。 */
interface Thumbs {
    suspend fun thumbFor(shot: ShotRow): ThumbSource
    fun fileOf(key: ThumbKey): File
    fun exists(key: ThumbKey): Boolean
}

/** 封面圖的網址。無簽章、不會過期，所以缺圖時拿它頂著是安全的（規格第五節）。 */
fun coverUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
