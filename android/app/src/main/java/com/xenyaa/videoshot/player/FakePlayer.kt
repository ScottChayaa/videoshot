package com.xenyaa.videoshot.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 測試用的播放器：可控、不碰網路、立刻就緒。
 * 對應 web 版的 `PUBLIC_PLAYER_MODE=fake`（規格第十三節）。
 *
 * 放在 main 而不是測試目錄，是因為階段 4b、5、9 的測試都要用它，
 * 而不同的測試 source set 之間不能互相引用。
 */
class FakePlayer(initialTime: Double = 0.0) : Player {

    override val isReady: StateFlow<Boolean> = MutableStateFlow(true)

    /** 收到過的所有跳播請求，依序記錄 —— 測試用它驗「長按有沒有跳到正確的秒數」。 */
    val seeks: MutableList<Double> = mutableListOf()

    var playing: Boolean = false
        private set

    private var time: Double = initialTime

    override suspend fun play() { playing = true }

    override suspend fun pause() { playing = false }

    override suspend fun seekTo(sec: Double) {
        seeks += sec
        time = sec
    }

    override suspend fun currentTime(): Double = time
}
