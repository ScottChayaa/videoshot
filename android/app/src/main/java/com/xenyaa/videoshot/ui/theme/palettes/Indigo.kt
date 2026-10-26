package com.xenyaa.videoshot.ui.theme.palettes

import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.theme.Palette
import com.xenyaa.videoshot.ui.theme.ThemeSpec

/**
 * 預設色系「夕陽海灘」（2026-10-25 起；之前是靛藍、短暫用過藍）：中性灰白底＋深海藍主色，帳號頁頭像是夕陽漸層。
 * 檔名與 `id` 沿用靛藍時代的 `indigo`（`id` 存在 DataStore，不可改）。
 *
 * **要新增別的色系就複製這個檔案改色值**，然後在 `Palettes.ALL` 裡多列一行 —— 其他地方都不必動。
 */
private val Colors = Palette(
    // 中性灰白底：只比純灰暖一點點，膠囊的淡灰底疊上去不會變髒（2026-10-26 前是暖白沙 #FBF4EC）
    bg = Color(0xFFF5F4F1),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFAF9F7),
    border = Color(0xFFE6E3DE),
    borderStrong = Color(0xFFCFCBC4),

    text = Color(0xFF1F2A35),
    textDim = Color(0xFF4A5562),
    textFaint = Color(0xFF6B7480),

    // 主色＝深海藍（白字 7.0:1、當文字在灰白底上 6.3:1）
    accent = Color(0xFF0B5F8A),
    accentInk = Color(0xFFFFFFFF),
    accentWeak = Color(0xFFE7EEF3),
    accentLine = Color(0xFFB4CADA),

    // 夕陽＝帳號頁頭像的漸層（hero 底不染色，就是頁面底色）。頭像上是白色字母，所以用深一階的陶土 → 焦糖橘；
    // 提案時的珊瑚 → 芒果太亮，白字只有 2～3:1
    hero1 = Color(0xFFB5482A),
    hero2 = Color(0xFFC96F22),

    danger = Color(0xFFC62B48),
    dangerInk = Color(0xFFFFFFFF),
    dangerWeak = Color(0xFFFBE9ED),
    warn = Color(0xFF8A5A0B),
    warnWeak = Color(0xFFFAF0DC),
    ok = Color(0xFF0F7B55),
    okWeak = Color(0xFFE2F2EC),

    overlay = Color(0x6B1F2A35),
    scrim = Color(0x9E000000),
    lightboxBg = Color(0xFF0B0B12),
    focusRing = Color(0x8C0B5F8A),
)

val Indigo = ThemeSpec(id = "indigo", label = "夕陽海灘（預設）", palette = Colors)
