package com.xenyaa.videoshot.player

/** watch page 裡影片定格要的兩件事（規格第六節「影片定格」）。 */
data class ClipSource(val playableInEmbed: Boolean, val durationSec: Int)

/** 要播哪一張：用 [shotId] 分辨「同一張」，[videoId] 決定要不要換頁面。 */
data class ClipTarget(val shotId: Long, val videoId: String, val atSec: Double)

/** 預載要讀的值。讀不到（頁面還沒好、沒有 `<video>`）時是 [Unknown]。 */
data class ClipStatus(val currentSec: Double, val readyState: Int) {
    companion object {
        val Unknown = ClipStatus(0.0, 0)
    }
}

/**
 * Lightbox 大圖換成影片畫面用的播放器（規格第六節「影片定格」）。比取圖第二步與原片播放的 [Player] 簡單：
 * 只要「靜音預載」然後停在那一格，不播放、不出聲。
 *
 * 呼叫順序：[load]（換影片才需要）→ [cue] → 輪詢 [status] 到緩衝好 → [hold]。
 */
interface ClipPlayer {
    /** 載入這支影片的頁面，**頁面載完才回來**。 */
    suspend fun load(videoId: String, playableInEmbed: Boolean)

    /** 停掉上一段的循環、靜音、跳到 [startSec]、開始播放讓它緩衝。可以重複呼叫。 */
    suspend fun cue(startSec: Double)

    /** 緩衝好了：暫停並跳回 [startSec]，仍然靜音。 */
    suspend fun hold(startSec: Double)

    suspend fun pause()

    suspend fun status(): ClipStatus

    /** 目前停住的那一格，原尺寸 JPEG（Lightbox【截圖】）；截不到回 null。 */
    suspend fun frame(): ByteArray?
}
