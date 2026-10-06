package com.xenyaa.videoshot.ui.theme.palettes

import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.theme.Palette
import com.xenyaa.videoshot.ui.theme.ThemeSpec

/**
 * 預設色系。色值逐一對應 `mockups/uiux-v2/styles.css` 的 CSS 變數。
 *
 * **要新增別的色系就複製這個檔案改色值**，然後在 `Palettes.ALL` 裡多列一行 —— 其他地方都不必動。
 */
private val Colors = Palette(
    bg = Color(0xFFEEF0F6),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF3F5FB),
    border = Color(0xFFDCE0EC),
    borderStrong = Color(0xFFC3C9DC),

    text = Color(0xFF1B1B33),
    textDim = Color(0xFF4B5563),
    textFaint = Color(0xFF6B7280),

    accent = Color(0xFF4F46E5),
    accentInk = Color(0xFFFFFFFF),
    accentWeak = Color(0xFFECECFB),
    accentLine = Color(0xFFC7C4F5),

    hero1 = Color(0xFF4F46E5),
    hero2 = Color(0xFF8B5CF6),

    danger = Color(0xFFC62B48),
    dangerInk = Color(0xFFFFFFFF),
    dangerWeak = Color(0xFFFBE9ED),
    warn = Color(0xFF8A5A0B),
    warnWeak = Color(0xFFFAF0DC),
    ok = Color(0xFF0F7B55),
    okWeak = Color(0xFFE2F2EC),

    overlay = Color(0x6B14142B),
    scrim = Color(0x9E000000),
    lightboxBg = Color(0xFF0B0B12),
    focusRing = Color(0x8C4F46E5),
)

val Indigo = ThemeSpec(id = "indigo", label = "靛藍（預設）", palette = Colors)
