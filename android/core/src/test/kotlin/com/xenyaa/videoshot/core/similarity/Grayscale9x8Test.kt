package com.xenyaa.videoshot.core.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 取樣是 dHash 的前半段，而且是 2026-09-15 那個「收斂藏不掉東西」的真正原因：
 * 原本每格只取**一個**像素，壓縮雜訊直接變成指紋的位元。這組測試守住區域平均的行為。
 */
class Grayscale9x8Test {

    private val w = 320
    private val h = 180

    /** 依 (x, y) 算灰階值，組成一張 [w]×[h] 的 ARGB 圖。offset 用來模擬取樣窗整體平移。 */
    private fun frame(dx: Int = 0, dy: Int = 0, gray: (Int, Int) -> Int): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val g = gray(x + dx, y + dy).coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        return out
    }

    /**
     * 低頻內容：平滑起伏，週期約 4～5 個取樣格。這是「畫面真正長什麼樣」的部分。
     *
     * **刻意不用硬邊方塊。** 硬邊在取樣窗平移一個像素時會真的改變該格的平均值 ——
     * 那是內容變了，不是雜訊，測到的就不是底噪了。
     */
    private fun lowFrequency(x: Int, y: Int): Int =
        (128 + 60 * kotlin.math.sin(x / 25.0) + 40 * kotlin.math.cos(y / 18.0)).toInt()

    /** 高頻雜訊：模擬 WebP／JPEG 的區塊雜訊。同一個 (x, y) 永遠給同一個值，測試才可重現。 */
    private fun noise(x: Int, y: Int): Int = Random(x * 73856093 xor y * 19349663).nextInt(-40, 41)

    @Test
    fun 整片同色時沒有任何位元被設起來() {
        val hash = dHash(grayscale9x8(frame { _, _ -> 128 }, w, h))
        assertEquals(0L, hash)
    }

    @Test
    fun 由左至右變亮時每個位元都是1() {
        val hash = dHash(grayscale9x8(frame { x, _ -> x * 255 / w }, w, h))
        assertEquals(64, java.lang.Long.bitCount(hash))
    }

    @Test
    fun 像素數量與寬高對不上就拒絕() {
        val e = runCatching { grayscale9x8(IntArray(10), w, h) }.exceptionOrNull()
        assertTrue("應該要拋 IllegalArgumentException，實際是 $e", e is IllegalArgumentException)
    }

    @Test
    fun 只有高頻雜訊不同的兩張圖指紋相同() {
        val clean = grayscale9x8(frame { x, y -> lowFrequency(x, y) }, w, h)
        val noisy = grayscale9x8(frame { x, y -> lowFrequency(x, y) + noise(x, y) }, w, h)
        assertEquals(dHash(clean), dHash(noisy))
    }

    /**
     * 底噪回歸：同一個畫面，取樣窗只挪一個像素，指紋不該跟著跳。
     * 2026-09-15 實測單點取樣在真實 storyboard 上的中位數是 7 個位元、p90 是 13 ——
     * 那比 [FilterStrength.MEDIUM] 的門檻還大，收斂因此完全失效。
     */
    @Test
    fun 取樣窗挪一個像素時指紋幾乎不動() {
        val a = dHash(grayscale9x8(frame(0, 0) { x, y -> lowFrequency(x, y) + noise(x, y) }, w, h))
        val b = dHash(grayscale9x8(frame(1, 1) { x, y -> lowFrequency(x, y) + noise(x, y) }, w, h))
        val distance = hammingDistance(a, b)
        assertTrue(
            "挪一個像素就差 $distance 個位元，已經吃掉最低門檻 ${FilterStrength.LOW.threshold}",
            distance <= FilterStrength.LOW.threshold,
        )
    }
}
