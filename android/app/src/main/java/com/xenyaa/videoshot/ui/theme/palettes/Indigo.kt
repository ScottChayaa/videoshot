package com.xenyaa.videoshot.ui.theme.palettes

import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.theme.Palette
import com.xenyaa.videoshot.ui.theme.ThemeSpec

/**
 * 預設色系。色值原本逐一對應 `mockups/uiux-v2/styles.css` 的 CSS 變數；2026-10-25 主色系從靛藍換成藍，其餘照舊。
 * 檔名與 `id` 沿用靛藍時代的 `indigo`（`id` 存在 DataStore，不可改）。
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

    // 主色＝藍（scott 在元件總覽調色盤挑的 #1A6DDB，壓暗一點讓它當文字放在 bg 上也過 AA）
    accent = Color(0xFF196AD6),
    accentInk = Color(0xFFFFFFFF),
    accentWeak = Color(0xFFE8F1FC),
    accentLine = Color(0xFFB8D1F6),

    hero1 = Color(0xFF196AD6),
    hero2 = Color(0xFF31BCE7),

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
    focusRing = Color(0x8C196AD6),
)

val Indigo = ThemeSpec(id = "indigo", label = "藍（預設）", palette = Colors)
