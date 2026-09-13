package com.xenyaa.videoshot.player

import kotlinx.coroutines.flow.StateFlow

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
}
