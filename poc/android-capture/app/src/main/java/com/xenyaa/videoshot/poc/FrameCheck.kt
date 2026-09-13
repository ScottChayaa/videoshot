package com.xenyaa.videoshot.poc

import android.graphics.Bitmap
import kotlin.math.sqrt

/** 亮度統計。isBlack 的門檻是暫定值，實測後依紀錄調整並寫回規格第五節。 */
object FrameCheck {
    data class Luma(val mean: Double, val std: Double) {
        val isBlack: Boolean get() = mean < 16.0 && std < 8.0
    }

    fun luma(bmp: Bitmap): Luma {
        val stepX = maxOf(1, bmp.width / 64)
        val stepY = maxOf(1, bmp.height / 36)
        var n = 0
        var sum = 0.0
        var sumSq = 0.0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                val l = 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
                sum += l
                sumSq += l * l
                n++
                x += stepX
            }
            y += stepY
        }
        val mean = sum / n
        return Luma(mean, sqrt(maxOf(0.0, sumSq / n - mean * mean)))
    }
}
