package com.xenyaa.videoshot.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 剪輯分頁的暗色配色（剪輯首頁含底部導覽、方向與導出抽屜、選素材、編輯、導出中、播放成品）。
 * 色值來自 `mockups/palette/` 的剪輯模式；`accentLine` 這類衍生色照那一頁「複製色碼」的算法。
 *
 * **不在 [Palettes.ALL] 裡**：使用者選的色系只管一般畫面，剪輯分頁固定用這一套。
 */
val ClipPalette = Palette(
    bg = Color(0xFF000000),
    surface = Color(0xFF222225),
    surface2 = Color(0xFF1C1C1F),
    border = Color(0xFF242427),
    borderStrong = Color(0xFF3A3A3F),

    text = Color(0xFFF2F2F3),
    textDim = Color(0xFFA5A5AB),
    // 調色盤頁是 #808086，在卡片上只有 4.04:1；調亮到過 AA 4.5:1（2026-11-08）
    textFaint = Color(0xFF8A8A90),

    accent = Color(0xFFE8E8EA),
    accentInk = Color(0xFF0A0A0B),
    accentWeak = Color(0xFF3C3C45),
    accentLine = Color(0xFF616164),

    // 帳號頁的 hero 漸層剪輯分頁用不到；Material 的 secondary 取它，跟主色一致
    hero1 = Color(0xFFE8E8EA),
    hero2 = Color(0xFFE8E8EA),

    danger = Color(0xFFFF4952),
    dangerInk = Color(0xFF1A0B0E),
    dangerWeak = Color(0xFF38262A),
    warn = Color(0xFFF5B63F),
    warnWeak = Color(0xFF3B3428),
    ok = Color(0xFF3DD68C),
    okWeak = Color(0xFF253831),

    overlay = Color(0x6B000000),
    scrim = Color(0x9E000000),
    lightboxBg = Color(0xFF000000),
    focusRing = Color(0x8CE8E8EA),

    selectedMark = Color(0xFF1A73E8),
    doneMark = Color(0xFF16A34A),
    playerProgress = Color(0xFFFFFFFF),
)

/** 剪輯專用、[Palette] 沒有的三個顏色。 */
object ClipColors {
    /** 預覽區（影片後面）的底。 */
    val preview = Color(0xFF000000)
    /** 編輯畫面時間軸那一條的底。 */
    val timeline = Color(0xFF0A0A0B)
    /** 時間軸上目前的位置。 */
    val playhead = Color(0xFFFFD23F)
}

/**
 * 套用剪輯暗色；[enabled] 為 false 時原樣沿用外層的色系。
 *
 * 兩種情況的組合結構相同（永遠包一層 Provider＋MaterialTheme），切換時裡面的畫面不會被重建
 * （AppShell 裡有取圖分頁的 WebView，重建會讓影片從頭載入）。暗色時系統列圖示換成白的，離開時還原。
 */
@Composable
fun ClipTheme(enabled: Boolean = true, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (!enabled || controller == null) return@DisposableEffect onDispose {}
        val wasLightStatus = controller.isAppearanceLightStatusBars
        val wasLightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = wasLightStatus
            controller.isAppearanceLightNavigationBars = wasLightNav
        }
    }
    val palette = if (enabled) ClipPalette else LocalPalette.current
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (enabled) ClipPalette.toColorScheme(darkColorScheme()) else MaterialTheme.colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
