package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.FocusRingWidth

enum class ChipSize { Regular, Mini }

/**
 * 一般尺寸小膠囊的寬度組成：左右內距＋圖示＋圖示與文字的間距＋文字。篩選抽屜分頁要在畫之前先算出
 * 每顆多寬（[regularWidthPx]），所以這幾個數字只寫在這裡，小膠囊本身也讀它們。
 * 左右內距 8（2026-10-09 scott 要求再緊一點，原本 10；迷你從 8 改 6）。
 */
object TagChipMetrics {
    val PadRegular = 8.dp
    val IconRegular = 14.dp
    val IconGap = 4.dp

    /** 一般尺寸、不帶張數的小膠囊寬度（px）。 */
    fun regularWidthPx(textWidthPx: Int, density: Float): Int =
        textWidthPx + ((PadRegular * 2 + IconRegular + IconGap).value * density).let { kotlin.math.ceil(it).toInt() }
}

/**
 * 標籤／地點小膠囊（原型 `styles.css`「chip」＋ `app.js` 的 `tagChip`）。
 *
 * 種類靠**圖示＋顏色**兩者一起區分；選取只靠變色（實心主色＋白字，跟淺底深字的明暗差夠大，不只靠色相）。
 *
 * 可點的小膠囊分兩種（設計 §八，與 [VsToolbarPill] 的 `isToggle` 同一條規則）：
 * - [isToggle] = false（預設）：動作／導覽（首頁月份標籤、第三步的建議標籤），當成按鈕，**不帶選取語意**，
 *   TalkBack 不會唸「未勾選，核取方塊」；
 * - [isToggle] = true：可切換選取（核取方塊語意，[selected] 才有意義）。
 */
@Composable
fun VsTagChip(
    name: String,
    kind: ChipKind,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    isToggle: Boolean = false,
    count: Int? = null,
    size: ChipSize = ChipSize.Regular,
    textLayout: TextLayoutResult? = null,
    onClick: (() -> Unit)? = null,
) {
    val regular = size == ChipSize.Regular
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    // 一般尺寸不畫框線，靠主色淺底表示「可以按」：accentWeak 在 bg 上太淡、accentLine 太重，取偏 accentWeak 的四分之一處；
    // 迷你維持白底＋框線（純顯示）
    val bg = when {
        selected -> AppTheme.colors.accent
        regular -> lerp(AppTheme.colors.accentWeak, AppTheme.colors.accentLine, 0.25f)
        else -> AppTheme.colors.surface
    }
    val ink = if (selected) AppTheme.colors.accentInk else AppTheme.colors.text
    val iconTint = if (selected) AppTheme.colors.accentInk else kind.color
    val textStyle = MaterialTheme.typography.bodySmall
    val iconSize = if (regular) TagChipMetrics.IconRegular else 13.dp
    val pad = if (regular) PaddingValues(horizontal = TagChipMetrics.PadRegular, vertical = AppTheme.spacing.s1)
    else PaddingValues(horizontal = 6.dp, vertical = 2.dp)

    // 可點時：觸控區是至少 44dp 高的透明外框、膠囊置中（手冊 §零），但按下的漣漪與鍵盤焦點框
    // 只畫在看得到的膠囊上，按下時的範圍跟看到的按鈕一樣大。
    // 外框的 selectable 是唯一的合併點（文字＋選取＋點擊）；內層再合併一次會讓內層自成一個節點，外框反而沒有文字。
    // 焦點用 onFocusChanged 記在一個狀態裡、只在繪製階段讀（焦點框畫在膠囊上），不為每一顆開一個收集焦點的協程——
    // 篩選抽屜一次畫幾十顆，這些固定成本加起來就是抽屜開啟時那一幀的耗時
    val interaction = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    val ringColor = AppTheme.colors.focusRing
    var m = if (onClick != null) {
        Modifier
            .drawWithContent {
                drawContent()
                if (focused.value) {
                    val r = AppTheme.radii.sm.toPx()
                    val w = FocusRingWidth.toPx()
                    drawRoundRect(
                        ringColor,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(this.size.width - w, this.size.height - w),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(w),
                    )
                }
            }
            .clip(shape)
            .indication(interaction, ripple())
    } else {
        modifier.semantics(mergeDescendants = true) {}.clip(shape)
    }
    m = m.background(bg)
    if (!regular) m = m.border(1.dp, if (selected) AppTheme.colors.accent else AppTheme.colors.border, shape)
    m = m.padding(pad)

    // 選取只靠變色表示：實心主色＋白字 vs 淺底＋深字，明暗差夠大，不只靠色相；TalkBack 由 selectable 唸已選取
    val chip: @Composable () -> Unit = {
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TagChipMetrics.IconGap)) {
            // 圖示直接畫向量圖，不經 Icon（少一層語意與版面節點；本來就不唸）
            Box(Modifier.size(iconSize).paint(rememberVectorPainter(kind.icon), colorFilter = ColorFilter.tint(iconTint)))
            if (textLayout != null && count == null) {
                // 呼叫端已經量過這段文字（篩選抽屜分頁時量的），直接畫，不再排版一次；文字語意照樣補上
                val density = LocalDensity.current
                Box(
                    Modifier
                        .size(with(density) { textLayout.size.width.toDp() }, with(density) { textLayout.size.height.toDp() })
                        .semantics { text = AnnotatedString(name) }
                        .drawBehind { drawText(textLayout, color = ink) },
                )
            } else if (count == null) {
                Text(name, style = textStyle, color = ink)
            } else {
                Text(
                    buildAnnotatedString {
                        append(name)
                        append(' ')
                        withStyle(SpanStyle(color = if (selected) AppTheme.colors.accentInk else AppTheme.colors.textDim)) {
                            append(count.toString())
                        }
                    },
                    style = textStyle,
                    color = ink,
                )
            }
        }
    }
    if (onClick != null) {
        Box(
            modifier
                .defaultMinSize(minHeight = AppTheme.spacing.tap)
                .onFocusChanged { focused.value = it.isFocused }
                .chipClickable(isToggle, selected, interaction, onClick),
            contentAlignment = Alignment.Center,
        ) { chip() }
    } else {
        chip()
    }
}

/** 切換型用核取方塊語意（帶選取狀態）；動作型只是按鈕，沒有選取語意。 */
private fun Modifier.chipClickable(
    isToggle: Boolean,
    selected: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier =
    if (isToggle) selectable(selected, interaction, indication = null, role = Role.Checkbox, onClick = onClick)
    else clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
