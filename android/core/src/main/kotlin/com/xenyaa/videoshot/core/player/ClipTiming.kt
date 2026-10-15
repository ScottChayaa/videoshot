package com.xenyaa.videoshot.core.player

/**
 * Lightbox 自動播放片段的手感（規格第六節「自動播放片段」）：停在一張圖多久才開始播。
 * 要調手感只改這裡。
 */
const val CLIP_DELAY_MS: Long = 2000L

/** 循環一段多長（秒）。 */
const val CLIP_LENGTH_SEC: Double = 3.0

/** 循環最短要多長；片尾剩不到這麼多時整段往前推。 */
const val CLIP_MIN_LENGTH_SEC: Double = 1.0

/** 片段循環的範圍 [startSec, endSec)。 */
data class ClipRange(val startSec: Double, val endSec: Double)

/**
 * 片段從哪裡播到哪裡。
 *
 * 一般是 [atSec] 起 [CLIP_LENGTH_SEC] 秒；超過片尾只播到片尾。片尾剩不到 [CLIP_MIN_LENGTH_SEC]
 * （片尾截的圖、或截圖秒數比 watch page 的整數總長還大）時整段往前推成最後 [CLIP_LENGTH_SEC] 秒，
 * 否則循環長度趨近 0，畫面會一直跳回。[durationSec] ≤ 0（讀不到總長）時不夾。
 */
fun clipRange(atSec: Double, durationSec: Double): ClipRange {
    val start = atSec.coerceAtLeast(0.0)
    if (durationSec <= 0.0) return ClipRange(start, start + CLIP_LENGTH_SEC)
    val end = minOf(start + CLIP_LENGTH_SEC, durationSec)
    if (end - start >= CLIP_MIN_LENGTH_SEC) return ClipRange(start, end)
    return ClipRange((durationSec - CLIP_LENGTH_SEC).coerceAtLeast(0.0), durationSec)
}
