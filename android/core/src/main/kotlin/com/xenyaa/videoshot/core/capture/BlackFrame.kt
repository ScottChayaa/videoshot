package com.xenyaa.videoshot.core.capture

import kotlin.math.sqrt

/** 一張圖的亮度分佈。 */
data class LumaStats(val mean: Double, val stdDev: Double)

/** 平均亮度的上限。低於它**而且** [STD_DEV_LIMIT] 也成立，才算黑畫面。 */
const val MEAN_LIMIT = 16.0

/** 標準差的上限。**這一項不能省** —— 見 [isBlackFrame]。 */
const val STD_DEV_LIMIT = 8.0

/** @param gray 取樣到的灰階值，值域 0～255。空陣列回 0／0。 */
fun lumaStats(gray: IntArray): LumaStats {
    if (gray.isEmpty()) return LumaStats(0.0, 0.0)
    val mean = gray.sumOf { it.toLong() }.toDouble() / gray.size
    val variance = gray.sumOf { val d = it - mean; d * d } / gray.size
    return LumaStats(mean, sqrt(variance))
}

/**
 * 截圖失敗時常靜悄悄回一張全黑的圖，所以截到之後要自己驗（規格第五節）。
 *
 * **兩個條件必須同時成立。** 2026-09-13 實機校準：真黑畫面的亮度平均是 3.0（標準差 2.1），
 * 而電影的深色開場（黑底白字）平均只有 2.2（標準差 13.7）—— **比黑畫面還暗**。
 * 只看平均會把有效畫面誤判掉，標準差才分得出「整片黑」與「黑底有東西」。
 */
fun isBlackFrame(gray: IntArray): Boolean {
    val stats = lumaStats(gray)
    return stats.mean < MEAN_LIMIT && stats.stdDev < STD_DEV_LIMIT
}
