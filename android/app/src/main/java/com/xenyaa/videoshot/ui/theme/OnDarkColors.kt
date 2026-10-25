package com.xenyaa.videoshot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 固定深底（Lightbox 的 `lightboxBg`、帳號頁頭像、縮圖上的 scrim）上的前景色。對應原型 `.lb` 的 `color: #fff`
 * 與 `rgba(255,255,255,…)` 各階。
 *
 * **不放進 [Palette]**：Lightbox 的底色不隨主題變，前景也就不該隨主題變——
 * 色系的 `accentInk` 不保證是淺色，拿來當圖示色疊在黑底上可能看不見。
 */
object OnDarkColors {
    val primary = Color(0xFFFFFFFF)
    val secondary = Color(0xCCFFFFFF)
    val tertiary = Color(0xA6FFFFFF)
    val fill = Color(0x1FFFFFFF)
    val hint = Color(0x8C000000)
}
