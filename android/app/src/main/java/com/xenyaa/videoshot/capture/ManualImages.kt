package com.xenyaa.videoshot.capture

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** 手動圖的尺寸與 storyboard 的 L3 單格一致，縮圖牆才不會一格大一格小。 */
const val MANUAL_WIDTH = 320
const val MANUAL_HEIGHT = 180

/**
 * 把截到的畫面縮成 320×180 的 WebP（規格第四節 `shot_image`：約 6 KB）。
 *
 * @return 編碼失敗回 null —— 呼叫端當成一種失敗型態處理，不要拋例外
 */
fun encodeManualWebp(source: Bitmap): ByteArray? {
    val scaled = Bitmap.createScaledBitmap(source, MANUAL_WIDTH, MANUAL_HEIGHT, true)
    val out = ByteArrayOutputStream()
    @Suppress("DEPRECATION")
    val format =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    val ok = scaled.compress(format, 75, out)
    // createScaledBitmap 在尺寸剛好相同時會回原物件 —— 那就不是我們造的，不能回收
    if (scaled !== source) scaled.recycle()
    return if (ok) out.toByteArray() else null
}

/**
 * 草稿期間的手動圖存放處：`drafts/{videoId}/manual/{uuid}.webp`（規格第四節）。
 *
 * **不可用 `cacheDir`** —— 系統或使用者的「清除快取」會把還沒入庫的補圖清掉。
 * 精靈完成或捨棄草稿時整個 `drafts/{videoId}/` 一起刪，所以這裡只需要 [clear]。
 */
class ManualImageStore(private val dir: File) {

    /** @return 寫好的檔案。目錄建不出來或寫不進去時往上拋，由呼叫端降級。 */
    fun save(webp: ByteArray): File {
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.webp")
        file.writeBytes(webp)
        return file
    }

    fun clear() {
        dir.deleteRecursively()
    }
}
