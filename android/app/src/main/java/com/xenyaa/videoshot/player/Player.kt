package com.xenyaa.videoshot.player

import kotlinx.coroutines.flow.StateFlow

/**
 * 控制層畫面要的播放狀態（規格第六節「播放器」），一次讀完、避免六次往返 WebView。
 * 讀不到（頁面還沒好、沒有 `<video>`）時是 [Unknown]。
 */
data class PlaybackStatus(
    val currentSec: Double,
    /** `<video>.duration`；還沒有媒體來源時是 NaN，這裡換成 0，呼叫端要退回 watch page 的總長 */
    val durationSec: Double,
    val paused: Boolean,
    /** `readyState > 0`：媒體接上了。還沒開播時點兩下要走點封面那條路（[TOGGLE_PLAY_JS]） */
    val started: Boolean,
    val videoWidth: Int,
    val videoHeight: Int,
) {
    companion object {
        val Unknown = PlaybackStatus(0.0, 0.0, paused = true, started = false, 0, 0)
    }
}

/**
 * 播放器的最小介面。**縮圖牆與詳情頁只認識這個介面** ——
 * 背後是 WebView 載 embed、載 m.youtube.com、還是測試用的假實作，呼叫端不必知道。
 *
 * 之所以要這一層：規格第十三節要求儀器測試用「假播放器」，
 * 否則每個測試都得等 YouTube 真的載入，既慢又不穩。
 */
interface Player {
    /** 播放器準備好了沒。WebView 要等頁面載入，假實作一開始就是 true。 */
    val isReady: StateFlow<Boolean>

    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(sec: Double)

    /** 目前播放到第幾秒。截圖時「圖與秒數在同一瞬間取得」靠的就是它（規格第五節）。 */
    suspend fun currentTime(): Double

    /** 播放中就暫停、停著就播；還沒開播時點封面開始播（點兩下用）。 */
    suspend fun togglePlay()

    suspend fun status(): PlaybackStatus
}
