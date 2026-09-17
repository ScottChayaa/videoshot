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

/**
 * Material3 的 `Typography` 有 15 個 slot，比 6 階多 —— **每一個都要指到這 6 階之一**，
 * 沒指到的維持 Material 內建預設，那些預設值不在 13／15／17／20／24／28 裡
 * （例如 `labelMedium` 預設是 12sp）。底部導覽列的文字就是走 `labelMedium`，
 * 是全 app 最常被看到的文字之一，漏掉它字級尺標就形同虛設
 * （見階段 7 全盤覆查第 8 點第 2 項）。
 */
val Typography = Typography(
    // 13：徽章、時間碼、輔助說明、底部導覽列文字
    labelSmall = style(13, 18, FontWeight.Medium),
    bodySmall = style(13, 18),
    labelMedium = style(13, 18, FontWeight.Medium),
    // 15：次要文字、清單副標、次要標題
    bodyMedium = style(15, 22),
    titleSmall = style(15, 22, FontWeight.Medium),
    // 17：正文、按鈕、清單主標
    bodyLarge = style(17, 24),
    labelLarge = style(17, 24, FontWeight.Medium),
    // 20：區塊標題
    titleMedium = style(20, 26, FontWeight.Medium),
    headlineSmall = style(20, 26, FontWeight.Medium),
    // 24：頁面標題
    titleLarge = style(24, 30, FontWeight.Bold),
    displaySmall = style(24, 30, FontWeight.Bold),
    headlineLarge = style(24, 30, FontWeight.Bold),
    // 28：數字強調
    headlineMedium = style(28, 34, FontWeight.Bold),
    displayMedium = style(28, 34, FontWeight.Bold),
    displayLarge = style(28, 34, FontWeight.Bold),
)
