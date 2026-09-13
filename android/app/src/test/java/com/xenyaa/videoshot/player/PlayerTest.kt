package com.xenyaa.videoshot.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTest {

    // ---- FakePlayer ----

    @Test
    fun 跳播會記錄下來也會改變目前時間() = runTest {
        val p = FakePlayer()
        p.seekTo(12.5)
        p.seekTo(30.0)
        assertEquals(listOf(12.5, 30.0), p.seeks)
        assertEquals(30.0, p.currentTime(), 0.0)
    }

    @Test
    fun 播放與暫停會改變狀態() = runTest {
        val p = FakePlayer()
        assertEquals(false, p.playing)
        p.play()
        assertEquals(true, p.playing)
        p.pause()
        assertEquals(false, p.playing)
    }

    @Test
    fun 初始時間可以指定() = runTest {
        assertEquals(42.0, FakePlayer(initialTime = 42.0).currentTime(), 0.0)
    }

    @Test
    fun 一開始就是就緒的() {
        assertEquals(true, FakePlayer().isReady.value)
    }

    // ---- 載入方式 ----

    @Test
    fun 可嵌入的影片走_embed_而且關掉控制列與字幕() {
        val url = playerUrl("aqz-KE-bpKQ", playableInEmbed = true)
        assertTrue(url, url.startsWith("https://www.youtube.com/embed/aqz-KE-bpKQ"))
        assertTrue(url, url.contains("controls=0"))
        assertTrue(url, url.contains("cc_load_policy=0"))
        assertTrue(url, url.contains("playsinline=1"))
        assertTrue(url, url.contains("rel=0"))
    }

    @Test
    fun 不可嵌入的影片退回_m_youtube_com() {
        // POC 實測：playableInEmbed=false 的影片 embed 會顯示
        //「影片擁有者已禁止在其他網站上播放這部影片」，但 m.youtube.com 播得出來（規格第二節第 5 點）
        assertEquals(
            "https://m.youtube.com/watch?v=5QLiE08LO2M",
            playerUrl("5QLiE08LO2M", playableInEmbed = false),
        )
    }
}
