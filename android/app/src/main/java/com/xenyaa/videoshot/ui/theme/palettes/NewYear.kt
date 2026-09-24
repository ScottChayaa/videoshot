package com.xenyaa.videoshot.ui.theme.palettes

import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.theme.Palette
import com.xenyaa.videoshot.ui.theme.ThemeSpec

/**
 * 新年（春節）主題。**只做淺色一套** —— 深色模式沿用它自己的淺色（見 `ThemeSpec.dark`）。
 *
 * **主色是金，不是紅。** 紅金是春節的底，但「紅色只用在破壞性動作」是這個 app 的驗收條件
 * （手冊 §零第二條：主要動作一律是主色、只有刪除那類是紅的）。主色若換成紅，
 * 【刪除這張收藏】就跟【播放這一段】同色，那條規則當場失效。所以：
 *
 * - **金**（`accent`）＝主要動作。用的是**深金**：`accent` 在這個 app 裡不只填色，
 *   導覽選取那一格、排序鈕、篩選列都直接拿它當**文字色**，亮金在米白底上只有 1.75:1、讀不到
 * - **紅**（`danger`）＝只有刪除那類動作，與金一眼分得開
 * - 亮金（`hero2`）留給帳號頁的**紅→金漸層**與描邊／淡底，節慶感集中在那裡
 *
 * 色值都過 AA 4.5:1，`PaletteTest` 會自動檢查（不合格就紅燈）。
 */
private val Light = Palette(
    // 暖米白，取代預設的冷灰底
    bg = Color(0xFFFDF3E7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFBF1E3),
    border = Color(0xFFEADCC8),
    borderStrong = Color(0xFFD9C4A5),

    // 文字帶一點褐，跟暖底同一個色溫
    text = Color(0xFF2B1A12),
    textDim = Color(0xFF5A4636),
    textFaint = Color(0xFF7A6450),

    // 主色＝深金（白字 5.7:1、當文字在米白底上 5.2:1，兩種用法都過 AA）
    accent = Color(0xFF8A5E10),
    accentInk = Color(0xFFFFFFFF),
    accentWeak = Color(0xFFFCF1DC),
    accentLine = Color(0xFFE0C48A),

    // 帳號頁的 hero 漸層：紅 → 金，節慶感集中在這裡
    hero1 = Color(0xFFC1121F),
    hero2 = Color(0xFFE8B33D),

    // 紅只出現在這一組
    danger = Color(0xFFC0173A),
    dangerInk = Color(0xFFFFFFFF),
    dangerWeak = Color(0xFFFBE7EB),
    // 警示往橙移：預設色系的 #8A5A0B 跟這裡的深金幾乎同色，並排時分不出來
    warn = Color(0xFF9A4A07),
    warnWeak = Color(0xFFFAF0DC),
    ok = Color(0xFF0F7B55),
    okWeak = Color(0xFFE2F2EC),

    overlay = Color(0x6B2B1A12),
    scrim = Color(0x9E000000),
    // Lightbox 的底必須不透明（規格第六節）；這裡用暖黑，跟整體色溫一致
    lightboxBg = Color(0xFF12100E),
    // 焦點框跟主色同一階，65% 讓它在白底與卡片上都看得見
    focusRing = Color(0xA68A5E10),
)

val NewYear = ThemeSpec(
    id = "newyear",
    label = "新年",
    light = Light,
)
