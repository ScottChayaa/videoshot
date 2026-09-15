package com.xenyaa.videoshot.core.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 門檻取自規格第五節（2026-09-13 實機校準）：亮度平均 < 16 「且」標準差 < 8。
 * 兩組實測值原樣寫成測試 —— 它們就是當初決定「標準差不能省」的證據。
 */
class BlackFrameTest {

    /** 造一張每個值都一樣的灰階圖。 */
    private fun flat(value: Int, n: Int = 576) = IntArray(n) { value }

    /** 平均在 [mean] 附近、振幅約 [spread] 的可重現分佈。 */
    private fun noisyAround(mean: Int, spread: Int) =
        IntArray(576) { (mean + if (it % 2 == 0) spread else -spread).coerceIn(0, 255) }

    /** 黑底白字：絕大多數是 0，少數是 255 —— 平均很低但標準差很大。 */
    private fun darkTitleCard() = IntArray(576) { if (it % 128 == 0) 255 else 0 }

    @Test
    fun 全黑的圖平均與標準差都是0() {
        val stats = lumaStats(flat(0))
        assertEquals(0.0, stats.mean, 0.001)
        assertEquals(0.0, stats.stdDev, 0.001)
    }

    @Test
    fun 平均與標準差算得對() {
        // 一半 0、一半 10 → 平均 5、標準差 5
        val gray = IntArray(100) { if (it < 50) 0 else 10 }
        val stats = lumaStats(gray)
        assertEquals(5.0, stats.mean, 0.001)
        assertEquals(5.0, stats.stdDev, 0.001)
    }

    @Test
    fun 真的黑畫面判為黑() {
        // 規格記載的實測值：平均 3.0、標準差 2.1
        assertTrue(isBlackFrame(noisyAround(mean = 3, spread = 2)))
    }

    @Test
    fun 電影的深色開場不判為黑() {
        // 規格記載的實測值：平均 2.2、標準差 13.7 —— 比真黑畫面「還暗」，
        // 只看平均一定誤判。黑底白字的開場字卡就是這種分佈
        assertFalse(isBlackFrame(darkTitleCard()))
    }

    @Test
    fun 標準差大但很亮的正常畫面不判為黑() {
        assertFalse(isBlackFrame(noisyAround(mean = 120, spread = 40)))
    }

    @Test
    fun 空陣列不當機() {
        // 取樣不到任何像素時（理論上不該發生）也不該炸掉呼叫端
        val stats = lumaStats(IntArray(0))
        assertEquals(0.0, stats.mean, 0.001)
        assertEquals(0.0, stats.stdDev, 0.001)
    }
}
