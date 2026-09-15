package com.xenyaa.videoshot.core.similarity

/**
 * 灰階的整數權重（總和 256）。每格平均要掃過整格的像素 —— L3 一格是 320×180，
 * 148 格就是 850 萬次 —— 用浮點乘法做這件事沒有道理。
 */
private const val R_WEIGHT = 77
private const val G_WEIGHT = 150
private const val B_WEIGHT = 29

/**
 * 把一格畫面取樣成 9×8 灰階，給 [dHash] 用（規格第五節第二步）。
 *
 * **每一格取的是該區域所有像素的平均，不是單一像素。** 這不是精緻化，是正確性：
 * 2026-09-15 實測，單點取樣在真實 storyboard 上把取樣座標挪一個像素，指紋就差**中位數 7 個位元**
 * （p90 是 13）—— 比 [FilterStrength.MEDIUM] 的門檻還大。也就是說門檻比對到的是 WebP 的區塊雜訊，
 * 不是畫面內容，收斂因此幾乎藏不掉任何東西。改成區域平均後底噪降到中位數 1～2 個位元。
 *
 * 取樣格會蓋滿整格、彼此不重疊；寬高除不盡時各格寬度差一個像素，這對平均值沒有影響。
 *
 * @param pixels 一格畫面的 ARGB 像素，列優先，長度必須是 [width] × [height]
 */
fun grayscale9x8(pixels: IntArray, width: Int, height: Int): IntArray {
    require(width > 0 && height > 0) { "寬高必須是正數，收到 ${width}×$height" }
    require(pixels.size == width * height) {
        "像素數量要等於 ${width}×$height = ${width * height}，收到 ${pixels.size} 個"
    }

    val out = IntArray(DHASH_WIDTH * DHASH_HEIGHT)
    for (cellY in 0 until DHASH_HEIGHT) {
        val y0 = height * cellY / DHASH_HEIGHT
        // 畫面比取樣格還小時（極短影片的低層級）上界會跟下界重疊 —— 至少取一個像素，不要除以零
        val y1 = (height * (cellY + 1) / DHASH_HEIGHT).coerceIn(y0 + 1, height)
        for (cellX in 0 until DHASH_WIDTH) {
            val x0 = width * cellX / DHASH_WIDTH
            val x1 = (width * (cellX + 1) / DHASH_WIDTH).coerceIn(x0 + 1, width)

            var sum = 0L
            for (y in y0 until y1) {
                val row = y * width
                for (x in x0 until x1) {
                    val p = pixels[row + x]
                    sum += R_WEIGHT * ((p shr 16) and 0xFF) +
                        G_WEIGHT * ((p shr 8) and 0xFF) +
                        B_WEIGHT * (p and 0xFF)
                }
            }
            out[cellY * DHASH_WIDTH + cellX] = (sum / ((x1 - x0) * (y1 - y0)) / 256).toInt()
        }
    }
    return out
}
