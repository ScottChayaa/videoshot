package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 清單列（原型 `styles.css`「設定／清單列」的 `.set-row`）。可點時尾端自動畫 › 。 */
@Composable
fun VsListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var m = modifier.fillMaxWidth()
    if (onClick != null) m = m.focusRing().clickable(role = Role.Button, onClick = onClick)
    m = m.semantics(mergeDescendants = true) {}
        .defaultMinSize(minHeight = AppTheme.spacing.tap)
        .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3)
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3)) {
        if (icon != null) Icon(icon, null, tint = AppTheme.colors.textDim, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) AppTheme.colors.danger else AppTheme.colors.text,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(VsIcons.ChevronRight, null, tint = AppTheme.colors.textFaint, modifier = Modifier.size(18.dp))
        }
    }
}
