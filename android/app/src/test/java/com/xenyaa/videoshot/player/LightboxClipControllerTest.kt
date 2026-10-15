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
    fun 兩秒前不播() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(1_999)
        runCurrent()
        assertNull(ctl.showing.value)
        assertFalse(p.calls.any { it.startsWith("loop") })
    }

    @Test
    fun 兩秒到而且準備好就從截圖秒數循環三秒() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
        assertEquals(listOf("load:v1", "cue:30.0", "hold:30.0", "loop:30.0-33.0"), p.calls)
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
    fun 開始拖就暫停_倒數作廢() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(1_000)
        ctl.onDragStart()
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        assertTrue("pause" in p.calls)
        assertFalse(p.calls.any { it.startsWith("loop") })
    }

    @Test
    fun 播放中開始拖就換回縮圖() = runTest(StandardTestDispatcher()) {
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
    fun 快速換頁只播最後一張() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(800)
        ctl.onDragStart()
        ctl.onSettled(b)
        advanceTimeBy(800)
        ctl.onDragStart()
        ctl.onSettled(c)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(3L, ctl.showing.value)
        assertEquals(listOf("loop:10.0-13.0"), p.calls.filter { it.startsWith("loop") })
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
    fun 點一下暫停_再點一下繼續() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        ctl.onTap()
        runCurrent()
        assertTrue(ctl.userPaused.value)
        assertEquals("pause", p.calls.last())
        ctl.onTap()
        runCurrent()
        assertFalse(ctl.userPaused.value)
        assertEquals("resume", p.calls.last())
    }

    @Test
    fun 還是縮圖時點一下沒反應() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(1_000)
        ctl.onTap()
        runCurrent()
        assertFalse(ctl.userPaused.value)
        assertFalse("pause" in p.calls)
    }

    @Test
    fun 被蓋住時不開播_拿掉後才播() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.setCovered(true)
        ctl.onSettled(a)
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(ctl.showing.value)
        assertFalse(p.calls.any { it.startsWith("loop") })
        ctl.setCovered(false)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1L, ctl.showing.value)
    }

    @Test
    fun 播放中被蓋住就暫停_拿掉繼續() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        ctl.setCovered(true)
        runCurrent()
        assertEquals("pause", p.calls.last())
        ctl.setCovered(false)
        runCurrent()
        assertEquals("resume", p.calls.last())
    }

    @Test
    fun 使用者暫停的_蓋住再拿掉仍維持暫停() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        ctl.onTap()
        ctl.setCovered(true)
        ctl.setCovered(false)
        runCurrent()
        assertFalse("resume" in p.calls)
        assertTrue(ctl.userPaused.value)
    }

    @Test
    fun 換一張就清掉使用者暫停() = runTest(StandardTestDispatcher()) {
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(a)
        advanceTimeBy(2_500)
        ctl.onTap()
        ctl.onDragStart()
        ctl.onSettled(b)
        advanceTimeBy(2_500)
        runCurrent()
        assertFalse(ctl.userPaused.value)
        assertEquals(2L, ctl.showing.value)
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
        ctl.onTap()
        ctl.setCovered(true)
        ctl.onDragStart()
        advanceUntilIdle()
        assertEquals(before, p.calls.size)
        assertNull(ctl.showing.value)
    }

    @Test
    fun 片尾的截圖用往前推的範圍() = runTest(StandardTestDispatcher()) {
        sourceResult = { ClipSource(true, durationSec = 600) }
        val p = FakeClipPlayer()
        val ctl = controller(p)
        ctl.onSettled(ClipTarget(9, "v1", 599.8))
        advanceTimeBy(2_500)
        runCurrent()
        assertTrue("loop:597.0-600.0" in p.calls)
    }
}
