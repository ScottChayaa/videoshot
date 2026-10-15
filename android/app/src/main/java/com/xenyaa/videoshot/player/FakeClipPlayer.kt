package com.xenyaa.videoshot.player

/**
 * 測試用的片段播放器：不碰網路、立刻回應。放在 main 的理由同 [FakePlayer]。
 *
 * 播放中每讀一次 [status]，目前秒數前進 0.1 秒——控制器靠「秒數在前進」判斷畫面真的出來了。
 */
class FakeClipPlayer : ClipPlayer {

    /**
     * 依序記錄每一個呼叫，例如 `load:v1`、`cue:30.0`、`hold:30.0`、`loop:30.0-33.0`（靜音時多 `:muted`）、
     * `mute:true`、`pause`、`resume`。
     */
    val calls: MutableList<String> = mutableListOf()

    /** false：[cue] 之後 readyState 停在 0，模擬網路慢、一直沒緩衝好。 */
    var readyAfterCue: Boolean = true

    /** 非 null：[cue] 之後秒數停在這裡（模擬 YouTube 自己跳回片頭），直到下一次 [cue]。 */
    var cueLandsAt: Double? = null

    /** [status] 回報的影片寬高。 */
    var videoWidth: Int = 1280
    var videoHeight: Int = 720

    private var time = 0.0
    private var ready = 0
    private var paused = true

    override suspend fun load(videoId: String, playableInEmbed: Boolean) {
        calls += "load:$videoId"
        ready = 0
        paused = true
    }

    override suspend fun cue(startSec: Double) {
        calls += "cue:$startSec"
        time = cueLandsAt ?: startSec
        cueLandsAt = null
        ready = if (readyAfterCue) 4 else 0
        paused = false
    }

    override suspend fun hold(startSec: Double) {
        calls += "hold:$startSec"
        time = startSec
        paused = true
    }

    override suspend fun startLoop(startSec: Double, endSec: Double, muted: Boolean) {
        calls += "loop:$startSec-$endSec" + if (muted) ":muted" else ""
        time = startSec
        paused = false
    }

    override suspend fun setMuted(muted: Boolean) {
        calls += "mute:$muted"
    }

    override suspend fun pause() {
        calls += "pause"
        paused = true
    }

    override suspend fun resume() {
        calls += "resume"
        paused = false
    }

    /** 測試中途讓它緩衝好。 */
    fun becomeReady() { ready = 4 }

    override suspend fun status(): ClipStatus {
        if (!paused && ready > 0) time += 0.1
        return ClipStatus(time, ready, paused, videoWidth, videoHeight)
    }
}
