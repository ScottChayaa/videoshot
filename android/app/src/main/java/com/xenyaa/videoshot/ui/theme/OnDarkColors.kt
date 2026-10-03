package com.xenyaa.videoshot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 固定深底（Lightbox 的 `lightboxBg` 深淺模式同值）上的前景色。對應原型 `.lb` 的 `color: #fff`
 * 與 `rgba(255,255,255,…)` 各階。
 *
 * **不放進 [Palette]**：Lightbox 的底色不隨主題變，前景也就不該隨主題變——
 * 階段 15A 之前 Lightbox 用 `accentInk` 當圖示色，深色模式下它是深色，疊在黑底上幾乎看不見。
 */
object OnDarkColors {
    val primary = Color(0xFFFFFFFF)
    val secondary = Color(0xCCFFFFFF)
    val tertiary = Color(0xA6FFFFFF)
    val fill = Color(0x1FFFFFFF)
    val hint = Color(0x8C000000)
}
