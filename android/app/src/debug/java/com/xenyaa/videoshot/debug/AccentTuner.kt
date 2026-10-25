package com.xenyaa.videoshot.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTextAction
import com.xenyaa.videoshot.ui.common.VsToolbarPill
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.Palette
import com.xenyaa.videoshot.ui.theme.ThemeSpec
import com.xenyaa.videoshot.ui.theme.palettes.Indigo
import kotlin.math.max
import kotlin.math.min

/**
 * 元件總覽頁右上角的調色盤（開發測試版專用）：手動調主色、換底色，整頁元件即時跟著換，
 * 用來試哪個主色適合。只活在這一頁、不存檔，關掉 app 就回到預設色系。
 *
 * 只調一個主色，其餘的主色系欄位（淡底、描邊、焦點框、帳號頁漸層）從它推出來；
 * 語意色（紅／橙／綠）不動 —— 「紅色只用在破壞性動作」要在換色之後仍然成立。
 */
enum class BaseTone(val label: String) {
    /** 預設色系的冷灰藍底（#EEF0F6）。 */
    COOL("冷灰（現在）"),

    /** 不帶色相的中性灰：不會把照片的顏色「染」偏。 */
    NEUTRAL("中性灰"),

    /** 底色帶一點主色的色相。 */
    TINTED("跟主色"),
}

data class AccentTuning(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val tone: BaseTone = BaseTone.COOL,
) {
    val accent: Color get() = Color.hsl(hue, saturation, lightness)

    fun toSpec(): ThemeSpec = ThemeSpec(id = "debug-custom", label = "自訂", palette = palette())

    private fun palette(): Palette {
        val a = accent
        val base = when (tone) {
            BaseTone.COOL -> Indigo.palette
            BaseTone.NEUTRAL -> Indigo.palette.copy(
                bg = Color(0xFFF1F1F2),
                surface2 = Color(0xFFF6F6F7),
                border = Color(0xFFE2E2E5),
                borderStrong = Color(0xFFC9C9CE),
                text = Color(0xFF1A1A1C),
                textDim = Color(0xFF4E4E55),
                textFaint = Color(0xFF6E6E76),
                overlay = Color(0x6B141416),
            )
            BaseTone.TINTED -> Indigo.palette.copy(
                bg = Color.hsl(hue, 0.30f, 0.95f),
                surface2 = Color.hsl(hue, 0.35f, 0.975f),
                border = Color.hsl(hue, 0.22f, 0.89f),
                borderStrong = Color.hsl(hue, 0.18f, 0.80f),
                text = Color.hsl(hue, 0.30f, 0.14f),
                textDim = Color.hsl(hue, 0.10f, 0.34f),
                textFaint = Color.hsl(hue, 0.08f, 0.44f),
            )
        }
        // 主色上的字：白字跟深字哪個對比高就用哪個（很亮的主色例如黃，白字讀不到）
        val ink = if (contrast(Color.White, a) >= contrast(base.text, a)) Color.White else base.text
        return base.copy(
            accent = a,
            accentInk = ink,
            accentWeak = lerp(Color.White, a, 0.10f),
            accentLine = lerp(Color.White, a, 0.32f),
            hero1 = a,
            hero2 = Color.hsl((hue + 25f) % 360f, saturation, min(lightness + 0.08f, 0.9f)),
            focusRing = a.copy(alpha = 0.55f),
        )
    }

    companion object {
        val DEFAULT = from(Indigo.palette.accent)

        fun from(color: Color, tone: BaseTone = BaseTone.COOL): AccentTuning {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color.toArgb(), hsl)
            return AccentTuning(hsl[0], hsl[1], hsl[2], tone)
        }
    }
}

/** WCAG 對比值（1～21）。 */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/**
 * 推薦的主色。都避開紅色（破壞色）；白字在上面都過 AA 4.5:1。
 * 綠、琥珀跟「完成」（綠）、「警示」（橙褐）是近親，選了要注意並排時分不分得開。
 */
private val Presets = listOf(
    "藍（現在）" to Color(0xFF196AD6),
    "靛藍（舊）" to Color(0xFF4F46E5),
    "深海藍" to Color(0xFF1D4ED8),
    "深青" to Color(0xFF0E7490),
    "青綠" to Color(0xFF0F766E),
    "石墨" to Color(0xFF3F3F46),
    "琥珀" to Color(0xFFB45309),
    "橄欖" to Color(0xFF4D7C0F),
)

/** 模擬照片：常見的照片色（天空、夕陽、樹林、海、膚色、夜景、雪、花），看主色擺在照片旁邊搶不搶戲。 */
private val FakePhotos = listOf(
    Color(0xFF5B9BD5) to Color(0xFFCFE6F7),
    Color(0xFF7A3B69) to Color(0xFFF28C38),
    Color(0xFF2E5E3A) to Color(0xFF9BC37B),
    Color(0xFF0B5E7A) to Color(0xFF53C1C9),
    Color(0xFFE8B796) to Color(0xFFA86E52),
    Color(0xFF14162E) to Color(0xFFE2A84B),
    Color(0xFFE9EEF3) to Color(0xFFA9B6C2),
    Color(0xFFD94F7B) to Color(0xFFF6D365),
)

/** Lucide `palette`；四個顏料點是 r=.5 的圓，改寫成兩段弧線（線寬 2 畫出來就是圓點）。 */
val PaletteIcon: ImageVector by lazy {
    ImageVector.Builder("palette", 24.dp, 24.dp, 24f, 24f).apply {
        listOf(
            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
            "M13 6.5a.5.5 0 1 0 1 0a.5.5 0 1 0-1 0",
            "M17 10.5a.5.5 0 1 0 1 0a.5.5 0 1 0-1 0",
            "M6 12.5a.5.5 0 1 0 1 0a.5.5 0 1 0-1 0",
            "M8 7.5a.5.5 0 1 0 1 0a.5.5 0 1 0-1 0",
        ).forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentTunerSheet(tuning: AccentTuning, onChange: (AccentTuning) -> Unit, onDismiss: () -> Unit) {
    val s2 = AppTheme.spacing.s2
    VsBottomSheet(onDismissRequest = onDismiss) {
        VsSheetHeader("主色") { VsTextAction("還原", onClick = { onChange(AccentTuning.DEFAULT) }) }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(bottom = AppTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Label("推薦")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s2), verticalArrangement = Arrangement.spacedBy(s2), maxItemsInEachRow = 4) {
                Presets.forEach { (name, color) ->
                    val picked = tuning.accent.hex() == color.hex()
                    Swatch(name, color, picked, Modifier.weight(1f)) { onChange(AccentTuning.from(color, tuning.tone)) }
                }
            }

            Label("色相 ${tuning.hue.toInt()}°")
            Box(
                Modifier.fillMaxWidth().height(8.dp).background(
                    Brush.horizontalGradient((0..6).map { Color.hsl(it * 60f % 360f, 0.8f, 0.5f) }),
                    CircleShape,
                ),
            )
            Slider(tuning.hue, { onChange(tuning.copy(hue = it)) }, valueRange = 0f..359f)
            Label("飽和度 ${(tuning.saturation * 100).toInt()}%")
            Slider(tuning.saturation, { onChange(tuning.copy(saturation = it)) })
            Label("亮度 ${(tuning.lightness * 100).toInt()}%")
            Slider(tuning.lightness, { onChange(tuning.copy(lightness = it)) }, valueRange = 0.1f..0.9f)

            ContrastReadout(tuning)

            Label("底色")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s2), verticalArrangement = Arrangement.spacedBy(s2)) {
                BaseTone.entries.forEach { tone ->
                    VsToolbarPill(tone.label, onClick = { onChange(tuning.copy(tone = tone)) }, selected = tuning.tone == tone, isToggle = true)
                }
            }

            Label("預覽")
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                FakePhotos.forEach { (top, bottom) ->
                    Box(Modifier.weight(1f).aspectRatio(1f).background(Brush.verticalGradient(listOf(top, bottom)), RectangleShape))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s2), verticalArrangement = Arrangement.spacedBy(s2)) {
                VsTagChip("加勒比海", ChipKind.PLACE, selected = true)
                VsTagChip("夜潛", ChipKind.TAG)
                VsToolbarPill("已選取", onClick = {}, selected = true, isToggle = true)
            }
            VsButton("下一步", onClick = {})
            VsButton("刪除這張收藏", onClick = {}, variant = ButtonVariant.Danger)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textDim)
}

@Composable
private fun Swatch(name: String, color: Color, picked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(AppTheme.radii.sm))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .then(if (picked) Modifier.border(2.dp, colors.text, CircleShape).padding(4.dp) else Modifier)
                .background(color, CircleShape),
        )
        Text(name, style = MaterialTheme.typography.labelMedium, color = colors.textDim, maxLines = 1)
    }
}

/** 主色在這個 app 裡兩種用法都有：填色（上面放字）、直接當文字色（導覽選取、排序鈕），兩種都要過 AA。 */
@Composable
private fun ContrastReadout(tuning: AccentTuning) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(AppTheme.radii.sm)).padding(AppTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(c.accent, CircleShape))
            Box(Modifier.width(AppTheme.spacing.s2))
            Text(c.accent.hex(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = c.text)
        }
        ContrastLine("主色上的字", contrast(c.accentInk, c.accent))
        ContrastLine("主色當文字（底色上）", contrast(c.accent, c.bg))
        ContrastLine("主色當文字（卡片上）", contrast(c.accent, c.surface))
        val h = tuning.hue
        if ((h < 20f || h > 330f) && tuning.saturation > 0.35f) {
            Text("太接近紅色：刪除鈕會跟主要動作撞色", style = MaterialTheme.typography.bodyMedium, color = c.danger)
        }
    }
}

@Composable
private fun ContrastLine(label: String, ratio: Float) {
    val c = AppTheme.colors
    val pass = ratio >= 4.5f
    Text(
        "$label  ${"%.1f".format(ratio)}:1  ${if (pass) "✓ 過 AA" else "✗ 未過 AA"}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (pass) c.textDim else c.danger,
    )
}
