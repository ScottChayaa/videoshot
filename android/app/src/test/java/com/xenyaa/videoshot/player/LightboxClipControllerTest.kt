package com.xenyaa.videoshot.player

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightboxClipControllerTest {

    private val a = ClipTarget(shotId = 1, videoId = "v1", atSec = 30.0)
    private val b = ClipTarget(shotId = 2, videoId = "v1", atSec = 90.0)
    private val c = ClipTarget(shotId = 3, videoId = "v2", atSec = 10.0)

    private var fetches = mutableListOf<String>()
    private var sourceResult: (String) -> ClipSource? = { ClipSource(playableInEmbed = true, durationSec = 600) }

    private fun TestScope.controller(player: ClipPlayer? = FakeClipPlayer()): LightboxClipController {
        val ctl = LightboxClipController(
            scope = backgroundScope,
            source = { id -> fetches += id; sourceResult(id) },
        )
        ctl.attach(player)
        return ctl
    }

    @Test
    fun 準備好就停在截圖那一秒_不循環() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
        assertEquals(listOf("load:v1", "cue:30.0", "hold:30.0"), p.calls)
        // 之後不再下任何指令：停在那一格，不播放
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, p.calls.size)
        assertEquals(ClipStatus(30.0, 4), p.status())
    }

    @Test
    fun 還沒緩衝好就繼續等_好了才播() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer().apply { readyAfterCue = false }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        p.becomeReady()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
    }

    @Test
    fun YouTube把秒數跳回片頭時重新跳一次() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer().apply { cueLandsAt = 0.0 }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(2, p.calls.count { it == "cue:30.0" })
        assertEquals(1L, ctl.showing.value)
    }

    @Test
    fun 預載中開始拖就停掉_預載作廢() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer().apply { readyAfterCue = false }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(1_000)
        ctl.onDragStart()
        p.becomeReady()
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        assertTrue("pause" in p.calls)
        assertFalse("hold:30.0" in p.calls)
    }

    @Test
    fun 定格中開始拖就換回縮圖() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        runCurrent()
        ctl.onDragStart()
        runCurrent()
        assertNull(ctl.showing.value)
        assertEquals("pause", p.calls.last())
    }

    @Test
    fun 快速換頁只定格最後一張() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer().apply { readyAfterCue = false }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(800)
        ctl.onDragStart()
        ctl.onSettled(b)
        advanceTimeBy(800)
        ctl.onDragStart()
        ctl.onSettled(c)
        p.readyAfterCue = true
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(3L, ctl.showing.value)
        assertEquals(listOf("hold:10.0"), p.calls.filter { it.startsWith("hold") })
    }

    @Test
    fun 同一支影片連續兩張不重載頁面() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        ctl.onDragStart()
        ctl.onSettled(b)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(1, p.calls.count { it.startsWith("load") })
        assertEquals(listOf("v1"), fetches)
        assertEquals(2L, ctl.showing.value)
    }

    @Test
    fun 換影片才重載頁面() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        ctl.onDragStart()
        ctl.onSettled(c)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(listOf("load:v1", "load:v2"), p.calls.filter { it.startsWith("load") })
    }

    @Test
    fun 抓不到影片就一直是縮圖_失敗不記住() = runTest(StandardTestDispatcher()) {
        sourceResult = { null }
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        assertTrue(p.calls.isEmpty())
        ctl.onDragStart()
        ctl.onSettled(a)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("v1", "v1"), fetches)
    }

    @Test
    fun 來源丟例外當成不能播_下次再試() = runTest(StandardTestDispatcher()) {
        var fail = true
        sourceResult = { if (fail) throw java.io.IOException("斷線") else ClipSource(true, 600) }
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        fail = false
        ctl.onDragStart()
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
    }

    @Test
    fun 播放器還沒來就等它() = runTest(StandardTestDispatcher()) {
        val ctl = controller(player = null)
        ctl.onSettled(a)
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        val p = FakeClipPlayer()
        ctl.attach(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
    }

    @Test
    fun 播放器拿掉後不再下指令() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer().apply { readyAfterCue = false }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(1_000)
        runCurrent()
        val before = p.calls.size
        ctl.attach(null)
        p.becomeReady()
        ctl.onDragStart()
        advanceUntilIdle()
        assertEquals(before, p.calls.size)
        assertNull(ctl.showing.value)
    }

    @Test
    fun 不經拖曳換成別張時先停掉() = runTest(StandardTestDispatcher()) {
        // 刪掉目前那張、下一張是別支影片且抓不到：上一張預載中的影片不能在縮圖後面一直播（最終審查 I-1）
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
        sourceResult = { null }
        ctl.onSettled(c)
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        assertEquals("pause", p.calls.last())
    }

    @Test
    fun 一直沒開始緩衝就重點一次封面() = runTest(StandardTestDispatcher()) {
        // 頁面剛載完封面還沒出現、或舊頁面的回呼讓 cue 打在錯的頁面上：readyState 一直是 0（最終審查 I-2）
        val p = FakeClipPlayer().apply { readyAfterCue = false }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, p.calls.count { it == "cue:30.0" })
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(p.calls.count { it == "cue:30.0" } >= 2)
    }

    @Test
    fun 太久沒準備好就重載頁面_繼續等() = runTest(StandardTestDispatcher()) {
        // 頁面載入失敗（錯誤頁）不能被記住：15 秒都沒準備好就重載，好了照樣播
        val p = FakeClipPlayer().apply { readyAfterCue = false }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(16_000)
        runCurrent()
        assertEquals(2, p.calls.count { it == "load:v1" })
        p.readyAfterCue = true
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
    }

    @Test
    fun 截圖秒數超過片長就停在片尾() = runTest(StandardTestDispatcher()) {
        sourceResult = { ClipSource(true, durationSec = 600) }
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(ClipTarget(9, "v1", 600.4))
        advanceTimeBy(2_500)
        runCurrent()
        assertTrue("hold:600.0" in p.calls)
    }

    @Test
    fun 截圖只截正在顯示影片畫面的那一張() = runTest(StandardTestDispatcher()) {
        val jpeg = byteArrayOf(1, 2, 3)
        val p = FakeClipPlayer().apply { readyAfterCue = false; frameJpeg = jpeg }
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(1_000)
        runCurrent()
        // 還是縮圖：不截
        assertNull(ctl.capture(1))
        p.becomeReady()
        advanceTimeBy(500)
        runCurrent()
        assertTrue(jpeg.contentEquals(ctl.capture(1)))
        // 別張（例如剛滑走、畫面還沒跟上）不截
        assertNull(ctl.capture(2))
        assertEquals(1, p.calls.count { it == "frame" })
    }
}
