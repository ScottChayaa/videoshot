package com.xenyaa.videoshot.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPalette = staticCompositionLocalOf { Palettes.DEFAULT.palette }

/**
 * 畫面取用設計 token 的入口：`AppTheme.colors.danger`、`AppTheme.spacing.s4`。
 *
 * 顏色走 CompositionLocal（會隨主題重組），尺標是常數物件（不隨主題變）。
 */
object AppTheme {
    val colors: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    val spacing: Spacing get() = Spacing
    val radii: Radii get() = Radii
}

/**
 * 把語意色映射到 Material3 的 ColorScheme，讓 Button／Chip／TextField 這些現成元件
 * **不必逐個覆寫顏色**就跟著色系走（取圖精靈那幾個畫面因此也一起換色）。
 */
fun Palette.toColorScheme(): ColorScheme {
    return lightColorScheme().copy(
        primary = accent,
        onPrimary = accentInk,
        primaryContainer = accentWeak,
        onPrimaryContainer = text,
        secondary = hero2,
        onSecondary = accentInk,
        secondaryContainer = accentWeak,
        onSecondaryContainer = text,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = textDim,
        surfaceContainer = surface2,
        surfaceContainerHigh = surface2,
        outline = border,
        outlineVariant = borderStrong,
        error = danger,
        onError = dangerInk,
        errorContainer = dangerWeak,
        onErrorContainer = danger,
        scrim = scrim,
    )
}

/**
 * **沒有深色模式，也沒有動態色（Material You，跟著使用者桌布變）。**
 * app 開起來就是使用者選的色系，不受手機的淺色／深色設定影響。
 * 動態色讓主色由桌布決定 —— 有人的桌布會讓主色變紅，「紅色只用在破壞性動作」（手冊 §零）就驗不了。
 */
@Composable
fun VideoshotTheme(
    spec: ThemeSpec = Palettes.DEFAULT,
    content: @Composable () -> Unit,
) {
    val palette = spec.palette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = Typography,
            content = content,
        )
    }
}
