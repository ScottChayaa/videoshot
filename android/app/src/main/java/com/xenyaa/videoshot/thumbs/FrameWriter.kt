package com.xenyaa.videoshot.thumbs

import android.graphics.Bitmap
import android.os.Build
import com.xenyaa.videoshot.core.storyboard.FramePos
import java.io.ByteArrayOutputStream

/**
 * 把 sheet 上的一格裁下來、編成 WebP、寫進 `thumbs/`（規格第七節的步驟 4、5）。
 *
 * **兩個呼叫端共用**：回填走 [SheetHarvester]（要下載），取圖第三步走 [LocalSheetCropper]
 * （sheet 已經在本機）。裁切與編碼的細節只該有一份 —— 之一改了畫質、之二沒改的話，
 * 同一支影片的縮圖會因為「是取圖時存的還是回填時存的」而不一樣。
 *
 * @return false 代表這一格沒寫成功（座標超出 sheet、編碼失敗、寫檔失敗）
 */
internal fun writeFrameToThumbs(sheet: Bitmap, pos: FramePos, key: ThumbKey, thumbs: Thumbs): Boolean {
    if (pos.x + pos.width > sheet.width || pos.y + pos.height > sheet.height) return false
    val frame = Bitmap.createBitmap(sheet, pos.x, pos.y, pos.width, pos.height)
    val out = ByteArrayOutputStream()
    @Suppress("DEPRECATION")
    val format =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    val encoded = frame.compress(format, 75, out)
    frame.recycle()
    if (!encoded) return false
    val file = thumbs.fileOf(key)
    file.parentFile?.mkdirs()
    file.writeBytes(out.toByteArray())
    return true
}
