package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.FocusRingWidth

/**
 * 兩種都是四邊圓角、標籤小膠囊的中性底（文字色 4%）、無邊框；差在大小。
 * [Outline]＝44dp 高的大鈕（名稱沿用，目前只有元件總覽頁用）；
 * [Quiet]＝視覺 30dp 高的小鈕，取圖第二步工具列與第三步快速選取列共用，不搶主按鈕的視線。
 */
enum class PillStyle { Outline, Quiet }

/**
 * 工具列小按鈕。[selected] 時換成主色底；停用時透明度 0.5。
 * 只有切換型按鈕（[isToggle]＝true）才把開關狀態寫進語意（TalkBack 唸「已選取／未選取」）；
 * 一般動作鈕（全選、清除…）不帶 Selected 屬性，免得被唸成「未選取」。
 */
@Composable
fun VsToolbarPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    isToggle: Boolean = false,
    enabled: Boolean = true,
    style: PillStyle = PillStyle.Outline,
) {
    val outline = style == PillStyle.Outline
    val shape = RoundedCornerShape(if (outline) AppTheme.radii.md else AppTheme.radii.sm)
    val bg = when {
        selected -> AppTheme.colors.accent
        else -> AppTheme.colors.text.copy(alpha = UNSELECTED_FILL_ALPHA)
    }
    val line = when {
        selected -> AppTheme.colors.accent
        else -> Color.Transparent
    }
    val ink = if (selected) AppTheme.colors.accentInk else AppTheme.colors.text
    // 觸控區是外框的 44dp，但按下的漣漪與鍵盤焦點框只畫在看得到的按鈕上（同 VsTagChip）——
    // 不然按下去會在按鈕外面多冒出一圈比按鈕大的陰影
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val ring = AppTheme.colors.focusRing
    Box(
        modifier
            // Quiet 視覺高 30，但外層實際佔位撐到 44（不用 minimumInteractiveComponentSize：
            // 它只放大觸控範圍，語意邊界仍是 30，TalkBack 的焦點框與測試量到的高度都會偏小）
            .defaultMinSize(minWidth = AppTheme.spacing.tap, minHeight = AppTheme.spacing.tap)
            .alpha(if (enabled) 1f else 0.5f)
            .onFocusChanged { focused = it.hasFocus }
            // 切換型按鈕的開關狀態要讓 TalkBack 唸得出來（不只靠主色底）
            .then(if (isToggle) Modifier.semantics { this.selected = selected } else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .border(FocusRingWidth, if (focused) ring else Color.Transparent, shape)
                .clip(shape).background(bg).border(1.dp, line, shape)
                .indication(interaction, ripple())
                .defaultMinSize(minHeight = if (outline) AppTheme.spacing.tap else 30.dp)
                .padding(horizontal = if (outline) AppTheme.spacing.s3 else AppTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
        ) {
            if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(if (outline) 17.dp else 15.dp))
            Text(text, style = if (outline) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall, color = ink)
        }
    }
}
