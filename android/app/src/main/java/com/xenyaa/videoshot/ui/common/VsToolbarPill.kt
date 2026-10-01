package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** [Outline]＝原型 `.wz-tools button`；[Quiet]＝`.wz-tools.pick button`（灰底小鈕，不搶主按鈕的視線）。 */
enum class PillStyle { Outline, Quiet }

/** 工具列小按鈕。[selected] 時換成主色底；停用時透明度 0.5。 */
@Composable
fun VsToolbarPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    style: PillStyle = PillStyle.Outline,
) {
    val outline = style == PillStyle.Outline
    val shape = if (outline) RoundedCornerShape(AppTheme.radii.full) else RoundedCornerShape(AppTheme.radii.sm)
    val bg = when {
        selected -> AppTheme.colors.accent
        outline -> AppTheme.colors.surface
        else -> AppTheme.colors.surface2
    }
    val line = when {
        selected -> AppTheme.colors.accent
        outline -> AppTheme.colors.border
        else -> Color.Transparent
    }
    val ink = if (selected) AppTheme.colors.accentInk else AppTheme.colors.text
    Box(
        modifier
            // Quiet 視覺高 30，但外層實際佔位撐到 44（不用 minimumInteractiveComponentSize：
            // 它只放大觸控範圍，語意邊界仍是 30，TalkBack 的焦點框與測試量到的高度都會偏小）
            .defaultMinSize(minWidth = AppTheme.spacing.tap, minHeight = AppTheme.spacing.tap)
            .alpha(if (enabled) 1f else 0.5f)
            .focusRing(shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape).background(bg).border(1.dp, line, shape)
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
