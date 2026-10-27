package com.xenyaa.videoshot.ui.clip

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.jpeg.JpegExtractor

/**
 * 先試檔案類型（[mimeOf]，MediaStore 回報的 `image/jpeg`、`video/mp4`…）對應的格式辨識器，其餘照 Media3 的預設順序。
 *
 * 相簿的 `content://` 網址沒有副檔名，Media3 只能照固定順序逐一試，MP3 排在圖片前面。JPEG 壓縮資料裡剛好出現
 * 「ID3」三個字時，MP3 辨識器會照它宣稱的大小要幾百 MB 記憶體而失敗，預覽停在錯誤狀態
 * （2026-10-27 實機：`OutOfMemoryError` in `Id3Peeker`）。Media3 認得 `Content-Type` 標頭，借它告訴預設工廠檔案類型。
 */
@UnstableApi
class MimeFirstExtractorsFactory(private val mimeOf: (Uri) -> String?) : ExtractorsFactory {
    // 圖片要靠 JPEG 辨識器的「讀成圖片」旗標。DefaultMediaSourceFactory 只在工廠本身是 DefaultExtractorsFactory 時
    // 才會幫忙打開（包一層就沒了，照片會整個認不出來），所以這裡自己打開；影片不是 JPEG，不受影響
    private val delegate = DefaultExtractorsFactory().setJpegExtractorFlags(JpegExtractor.FLAG_READ_IMAGE)

    override fun createExtractors(): Array<Extractor> = delegate.createExtractors()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        val mime = runCatching { mimeOf(uri) }.getOrNull() ?: return delegate.createExtractors(uri, responseHeaders)
        return delegate.createExtractors(uri, responseHeaders + (CONTENT_TYPE to listOf(mime)))
    }

    private companion object {
        const val CONTENT_TYPE = "Content-Type"
    }
}
