package com.xenyaa.videoshot.player

/** watch page 裡片段播放要的兩件事（規格第六節「自動播放片段」）。 */
data class ClipSource(val playableInEmbed: Boolean, val durationSec: Int)

/** 要播哪一張：用 [shotId] 分辨「同一張」，[videoId] 決定要不要換頁面。 */
data class ClipTarget(val shotId: Long, val videoId: String, val atSec: Double)

/**
 * 片段播放要讀的值。讀不到（頁面還沒好、沒有 `<video>`）時是 [Unknown]。
 * [videoWidth]／[videoHeight] 給全螢幕決定要不要轉橫向；還沒開播時是 0。
 */
data class ClipStatus(
    val currentSec: Double,
    val readyState: Int,
    val paused: Boolean,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
) {
    companion object {
        val Unknown = ClipStatus(0.0, 0, paused = true)
    }
}

/**
 * Lightbox 自動播放片段的播放器（規格第六節「自動播放片段」）。比取圖第二步與原片播放的 [Player] 簡單：
 * 沒有進度條與拖曳跳播，但要能「靜音預載」與「在頁面裡循環」。
 *
 * 呼叫順序：[load]（換影片才需要）→ [cue] → 輪詢 [status] 到緩衝好 → [hold] → [startLoop]。
 */
interface ClipPlayer {
    /** 載入這支影片的頁面，**頁面載完才回來**。 */
    suspend fun load(videoId: String, playableInEmbed: Boolean)

    /** 停掉上一段的循環、靜音、跳到 [startSec]、開始播放讓它緩衝。可以重複呼叫。 */
    suspend fun cue(startSec: Double)

    /** 緩衝好了：暫停並跳回 [startSec]，仍然靜音。 */
    suspend fun hold(startSec: Double)

    /** 從 [startSec] 開始在 [startSec, endSec) 之間循環。[muted] false 才取消預載時的靜音。 */
    suspend fun startLoop(startSec: Double, endSec: Double, muted: Boolean)

    /** 使用者切換【聲音】（循環中才會呼叫）。 */
    suspend fun setMuted(muted: Boolean)

    suspend fun pause()

    suspend fun resume()

    suspend fun status(): ClipStatus
}
