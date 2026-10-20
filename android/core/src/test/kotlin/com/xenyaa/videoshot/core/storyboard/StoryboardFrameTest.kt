package com.xenyaa.videoshot.core.storyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardFrameTest {

    private val spec = Storyboard.parse(REAL_SPEC)!!
    private val l3 = spec.levels[3] // 320x180、3x3、每格 1 秒、共 25 格

    private fun FramePos.grid() = listOf(frameIndex, sheetIndex, col, row)

    // ---- pickLevel ----

    @Test
    fun `預設取 level 3`() {
        assertEquals(3, Storyboard.pickLevel(spec)!!.level)
    }

    @Test
    fun `指定的 level 不存在時退回最高可用的`() {
        assertEquals(3, Storyboard.pickLevel(spec, preferred = 9)!!.level)
    }

    @Test
    fun `沒有任何 level 時回傳 null`() {
        assertNull(Storyboard.pickLevel(StoryboardSpec("x", "", emptyList())))
    }

    @Test
    fun `frameCount 為 0 的 level 會被過濾掉`() {
        val zero = StoryboardLevel(0, 100, 100, 0, 3, 3, 1000, "test")
        assertNull(Storyboard.pickLevel(StoryboardSpec("x", "", listOf(zero))))
    }

    @Test
    fun `intervalMs 為 0 的 L0 不會被選到`() {
        val onlyL0 = StoryboardSpec("x", "", listOf(spec.levels[0]))
        assertNull(Storyboard.pickLevel(onlyL0))
    }

    // ---- frameAt ----

    @Test
    fun `t 為 0 是第 0 張的左上角`() {
        val p = Storyboard.frameAt(l3, 0.0)
        assertEquals(listOf(0, 0, 0, 0), p.grid())
        assertEquals(0, p.x)
        assertEquals(0, p.y)
    }

    @Test
    fun `t 為 4 是第 0 張的第 1 列第 1 欄`() {
        val p = Storyboard.frameAt(l3, 4.0)
        assertEquals(listOf(4, 0, 1, 1), p.grid())
        assertEquals(320, p.x)
        assertEquals(180, p.y)
    }

    @Test
    fun `t 為 9 跨到第 1 張的左上角`() {
        assertEquals(listOf(9, 1, 0, 0), Storyboard.frameAt(l3, 9.0).grid())
    }

    // 取最近的一格，不是之前的一格。對照 YouTube 播放器 hover 預覽驗證過的規則。
    @Test
    fun `t 為 4 點 6 取最近的第 5 格而不是無條件捨去的第 4 格`() {
        assertEquals(listOf(5, 0, 2, 1), Storyboard.frameAt(l3, 4.6).grid())
    }

    @Test
    fun `t 為 4 點 4 仍落在第 4 格`() {
        assertEquals(listOf(4, 0, 1, 1), Storyboard.frameAt(l3, 4.4).grid())
    }

    @Test
    fun `接近下一張 sheet 時提前跨過去`() {
        assertEquals(listOf(9, 1, 0, 0), Storyboard.frameAt(l3, 8.7).grid())
    }

    @Test
    fun `超過 frameCount 時夾到最後一格`() {
        assertEquals(listOf(24, 2, 0, 2), Storyboard.frameAt(l3, 9999.0).grid())
    }

    @Test
    fun `負的秒數視為 0`() {
        assertEquals(listOf(0, 0, 0, 0), Storyboard.frameAt(l3, -3.0).grid())
    }

    @Test
    fun `帶出整張 sheet 的尺寸`() {
        val p = Storyboard.frameAt(l3, 0.0)
        assertEquals(960, p.sheetWidth)
        assertEquals(540, p.sheetHeight)
        assertEquals(320, p.width)
        assertEquals(180, p.height)
    }

    @Test
    fun `frameCount 為 0 時回傳第 0 格而不是負數`() {
        val zero = StoryboardLevel(0, 320, 180, 0, 3, 3, 1000, "test")
        val p = Storyboard.frameAt(zero, 5.0)
        assertEquals(listOf(0, 0, 0, 0), p.grid())
        assertEquals(0, p.x)
        assertEquals(0, p.y)
    }

    @Test
    fun `intervalMs 為 0 時回傳第 0 格`() {
        val noInterval = l3.copy(intervalMs = 0)
        assertEquals(0, Storyboard.frameIndexAt(noInterval, 0.0))
        assertEquals(0, Storyboard.frameIndexAt(noInterval, 12.0))
    }

    // ---- framePosition 與 frameTimeSec ----

    @Test
    fun `由格號求位置與由秒數求位置一致`() {
        for (i in 0 until l3.frameCount) {
            assertEquals(Storyboard.framePosition(l3, i), Storyboard.frameAt(l3, Storyboard.frameTimeSec(l3, i)))
        }
    }

    @Test
    fun `frameTimeSec 由格號推回影片秒數`() {
        assertEquals(12.0, Storyboard.frameTimeSec(l3, 12), 0.0)
        assertEquals(12.5, Storyboard.frameTimeSec(l3.copy(intervalMs = 2500), 5), 0.0)
    }

    // ---- sheetUrl ----

    @Test
    fun `sheetUrl 換掉 level 與 sheet 編號並補上 sqp 與 sigh`() {
        val url = Storyboard.sheetUrl(spec, l3, 1)
        assertTrue(url, url.contains("/storyboard3_L3/M1.jpg"))
        assertTrue(url, url.contains("sigh=rs\$AOn4CLAF8rkqvc6h6mM0WUjOJy55DJC1vA"))
        assertTrue(url, url.contains("sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjTpKzTBg=="))
    }

    // ---- frameCountWithin ----

    // YouTube 給的格數一律是 ceil(片長 / 間隔) + 1，最後一格的時間點總是超過片長
    private fun level(frameCount: Int, intervalMs: Int) = StoryboardLevel(3, 101, 180, frameCount, 3, 3, intervalMs, "s")

    @Test
    fun `超過片長的最後一格不算`() {
        // -8Irr9Ah2i4：178.845 秒、每 2 秒一格、spec 寫 91 格 → 第 90 格是 180 秒
        assertEquals(90, Storyboard.frameCountWithin(level(91, 2000), 178.845))
        // OPf0YbXqDm0：270.395 秒 → 第 135 格（270 秒）還在片長內，要留
        assertEquals(136, Storyboard.frameCountWithin(level(137, 2000), 270.395))
    }

    @Test
    fun `片長剛好落在格上時那一格要留`() {
        assertEquals(91, Storyboard.frameCountWithin(level(91, 2000), 180.0))
    }

    @Test
    fun `不會比 spec 寫的格數多`() {
        assertEquals(91, Storyboard.frameCountWithin(level(91, 2000), 999.0))
    }

    @Test
    fun `讀不到片長時照 spec 的格數`() {
        assertEquals(91, Storyboard.frameCountWithin(level(91, 2000), 0.0))
    }
}
