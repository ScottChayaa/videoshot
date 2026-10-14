package com.xenyaa.videoshot.core.player

import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import kotlin.math.abs

/**
 * 拖曳跳播的敏感度（規格第六節「播放器」）：1.0 ＝ 滑過整個播放器寬度跳整支影片。
 * 之後要調手感只改這一個數字。
 */
const val SCRUB_SENSITIVITY: Double = 1.0

/** 橫的影片的門檻：16:9、4:3 都算橫的，正方形與直的不算。 */
private const val LANDSCAPE_RATIO = 1.3

/** 大螢幕橫向時，非全螢幕播放器最多占視窗高度的比例（Android 16 以上鎖不住直立）。 */
private const val LANDSCAPE_MAX_HEIGHT_FRACTION = 0.4f

/**
 * 拖曳後要跳到的秒數：從拖曳開始時的 [startSec] 起算，夾在 0 到總長。
 * 寬度或總長不合理（0、負數）時原地不動。
 */
fun scrubTargetSec(
    startSec: Double,
    dxPx: Float,
    widthPx: Float,
    durationSec: Double,
    sensitivity: Double = SCRUB_SENSITIVITY,
): Double {
    if (widthPx <= 0f || durationSec <= 0.0) return startSec
    val delta = dxPx.toDouble() / widthPx.toDouble() * durationSec * sensitivity
    return (startSec + delta).coerceIn(0.0, durationSec)
}

/** 全螢幕要不要轉橫向。讀不到寬高（還沒開播）當成橫的——大部分影片是橫的。 */
fun isLandscapeVideo(width: Int?, height: Int?): Boolean {
    if (width == null || height == null || width <= 0 || height <= 0) return true
    return width.toDouble() / height >= LANDSCAPE_RATIO
}

/** 跟 [sec] 最接近的那一格；沒有格子回 null。 */
fun nearestFrameIndex(atSec: List<Double>, sec: Double): Int? =
    atSec.indices.minByOrNull { abs(atSec[it] - sec) }

/** 橫向視窗（寬 > 高）時播放器的高度上限；直向回 null（不限）。 */
fun playerMaxHeightDp(windowWidthDp: Float, windowHeightDp: Float): Float? =
    if (windowWidthDp > windowHeightDp) windowHeightDp * LANDSCAPE_MAX_HEIGHT_FRACTION else null

/** 詳情頁預覽用的層級：比第二步的 L3 低一階，檔案小、下載快。 */
fun previewLevelOf(spec: StoryboardSpec): StoryboardLevel? = Storyboard.pickLevel(spec, preferred = 2)
