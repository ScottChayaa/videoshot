package com.xenyaa.videoshot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字級**只有 6 階**（手冊 §零）：13／15／17／20／24／28。
 * 對應 mockup 的 --t-xs ~ --t-2xl。
 *
 * Material3 的 slot 比 6 個多，所以有幾個 slot 共用同一個尺寸
 * （例如按鈕的 labelLarge 與正文的 bodyLarge 都是 17）—— 這是刻意的：
 * 用途不同但大小相同，比多開一階更容易維持一致。
 */
private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

val Typography = Typography(
    // 13：徽章、時間碼、輔助說明
    labelSmall = style(13, 18, FontWeight.Medium),
    bodySmall = style(13, 18),
    // 15：次要文字、清單副標
    bodyMedium = style(15, 22),
    // 17：正文、按鈕、清單主標
    bodyLarge = style(17, 24),
    labelLarge = style(17, 24, FontWeight.Medium),
    // 20：區塊標題
    titleMedium = style(20, 26, FontWeight.Medium),
    // 24：頁面標題
    titleLarge = style(24, 30, FontWeight.Bold),
    // 28：數字強調
    headlineMedium = style(28, 34, FontWeight.Bold),
)
