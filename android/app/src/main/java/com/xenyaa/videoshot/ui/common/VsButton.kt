package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 原型 `.btn` 的五個變體。Danger／DangerQuiet 是全 app 唯二可以出現紅色的按鈕（手冊 §零）。 */
enum class ButtonVariant { Primary, Secondary, Quiet, Danger, DangerQuiet }

/**
 * 全 app 共用按鈕（原型「按鈕（單一基底＋變體）」）。底層借 Material3 的 [Button] 處理
 * 按壓、停用與語意，外觀全部由這裡指定。
 */
@Composable
fun VsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val (bg, fg, line) = when (variant) {
        ButtonVariant.Primary -> Triple(c.accent, c.accentInk, Color.Transparent)
        ButtonVariant.Secondary -> Triple(c.surface, c.text, c.borderStrong)
        ButtonVariant.Quiet -> Triple(Color.Transparent, c.textDim, Color.Transparent)
        ButtonVariant.Danger -> Triple(c.danger, c.dangerInk, Color.Transparent)
        ButtonVariant.DangerQuiet -> Triple(c.surface, c.danger, c.borderStrong)
    }
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, if (enabled) line else c.border),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = c.surface2,
            disabledContentColor = c.textFaint,
        ),
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        elevation = null,
        modifier = modifier.defaultMinSize(minHeight = AppTheme.spacing.tap).focusRing(shape),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppTheme.spacing.s2))
        }
        Text(text, style = MaterialTheme.typography.labelLarge) // 17 Medium
    }
}
