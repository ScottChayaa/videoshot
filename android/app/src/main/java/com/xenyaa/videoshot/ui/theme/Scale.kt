package com.xenyaa.videoshot.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 間距尺標（手冊 §零「間距只用 4/8/12/16/24/32」）。
 * **不隨色系變** —— 版面規則與配色是兩件事。
 */
object Spacing {
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 24.dp
    val s6: Dp = 32.dp

    /** 觸控目標的最小邊長。 */
    val tap: Dp = 44.dp

    /** 底部導覽列高度（不含系統手勢區）。 */
    val navHeight: Dp = 58.dp

    /**
     * 頂欄高度（不含底部 1dp 分隔線）：44 觸控鈕＋上下各 8。
     * 原型 `--head-h: 61px` 已含分隔線；有沒有按鈕的頁面都同高。
     */
    val topBarHeight: Dp = 60.dp
}

object Radii {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val full: Dp = 999.dp
}
