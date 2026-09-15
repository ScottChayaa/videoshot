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

    // ---- 啟動播放（規格第二節第 5 點）----

    @Test
    fun 播放先點封面覆蓋層的播放鍵() {
        // 2026-09-15 實機實測：embed 載完時播放器停在 unstarted，<video> 存在但
        // readyState=0、networkState=0、src 是空的 —— 根本還沒有媒體來源。
        // 這時 v.play() 只會把 paused 翻成 false，畫面完全不動。
        assertTrue(PLAY_VIDEO_JS, PLAY_VIDEO_JS.contains("CuedOverlayPlayButton"))
    }

    @Test
    fun 覆蓋層要比_video_play_先試() {
        // 順序反過來就等於沒修：unstarted 的 <video> 會把 v.play() 吃掉且不報錯
        // 用 `in 0 until` 而不是單純比大小：找不到時 indexOf 回 -1，
        // 「-1 < 正數」會讓這條測試在覆蓋層根本不存在時假性通過
        val overlayAt = PLAY_VIDEO_JS.indexOf("CuedOverlayPlayButton")
        val playAt = PLAY_VIDEO_JS.indexOf(".play()")
        assertTrue(PLAY_VIDEO_JS, overlayAt in 0 until playAt)
    }

    @Test
    fun 已經開始播之後仍然要能用_video_play_續播() {
        // 覆蓋層點掉就不見了，之後的每一次跳播都只剩 <video> 這條路
        assertTrue(PLAY_VIDEO_JS, PLAY_VIDEO_JS.contains(".play()"))
    }
}
