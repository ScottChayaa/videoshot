package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 可關閉的提示卡（原型 `.wz-tip`）。有傳 [onDismiss] 時右側出現一顆關閉鈕。 */
@Composable
fun VsHintCard(text: String, modifier: Modifier = Modifier, icon: ImageVector? = VsIcons.Check, onDismiss: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, top = AppTheme.spacing.s3)
            .clip(shape).background(AppTheme.colors.surface2).border(1.dp, AppTheme.colors.border, shape)
            .padding(AppTheme.spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) Icon(icon, null, tint = AppTheme.colors.accent, modifier = Modifier.size(17.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textDim, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(shape)) {
                Icon(VsIcons.Close, contentDescription = "關閉提示", tint = AppTheme.colors.textDim, modifier = Modifier.size(24.dp))
            }
        }
    }
}
