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
}

object Radii {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val full: Dp = 999.dp
}
