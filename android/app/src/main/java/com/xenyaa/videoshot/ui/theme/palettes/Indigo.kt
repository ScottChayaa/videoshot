package com.xenyaa.videoshot.ui.theme.palettes

import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.theme.Palette
import com.xenyaa.videoshot.ui.theme.ThemeSpec

/**
 * 預設色系（2026-11-08 起是中性灰階：灰白底＋炭黑主色；之前是「夕陽海灘」的深海藍、更早是靛藍）。
 * 檔名與 `id` 沿用靛藍時代的 `indigo`（`id` 存在 DataStore，不可改）。
 *
 * **要新增別的色系就複製這個檔案改色值**，然後在 `Palettes.ALL` 裡多列一行 —— 其他地方都不必動。
 */
private val Colors = Palette(
    // 中性灰白底（2026-11-08 前是帶一點暖的 #F5F4F1）
    bg = Color(0xFFF4F4F5),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFAFAFA),
    border = Color(0xFFE4E4E7),
    borderStrong = Color(0xFFCBCBD0),

    text = Color(0xFF1C1C21),
    textDim = Color(0xFF4A4A52),
    textFaint = Color(0xFF6B6B74),

    // 主色＝炭黑
    accent = Color(0xFF2D2D33),
    accentInk = Color(0xFFFFFFFF),
    accentWeak = Color(0xFFEBEBEE),
    accentLine = Color(0xFFBCBCBE),

    // 帳號頁頭像的漸層（頭像上是白色字母）
    hero1 = Color(0xFF3A3A44),
    hero2 = Color(0xFF6A6A78),

    danger = Color(0xFFC62B48),
    dangerInk = Color(0xFFFFFFFF),
    dangerWeak = Color(0xFFF9EAED),
    warn = Color(0xFFCD393C),
    warnWeak = Color(0xFFFAF0DC),
    ok = Color(0xFF0F7B55),
    okWeak = Color(0xFFE2EFEB),

    overlay = Color(0x6B1C1C21),
    scrim = Color(0x9E000000),
    lightboxBg = Color(0xFF0A0A0B),
    focusRing = Color(0x8C2D2D33),

    selectedMark = Color(0xFF1A73E8),
    doneMark = Color(0xFF16A34A),
    playerProgress = Color(0xFFFFFFFF),
    playingFrame = Color(0xFFFFD23F),
)

val Indigo = ThemeSpec(id = "indigo", label = "夕陽海灘（預設）", palette = Colors)
