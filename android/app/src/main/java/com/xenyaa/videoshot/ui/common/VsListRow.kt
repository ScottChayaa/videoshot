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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 清單列（原型 `styles.css`「設定／清單列」的 `.set-row`）。可點時尾端自動畫 › 。
 *
 * [iconTint] 預設 `textDim`；標籤清單列用種類色（[ChipKind.color]）。
 *
 * 預設把整列的語意合併成一個節點（TalkBack 一次唸完標題與副標）。尾端放 `clickable`／`IconButton`／
 * `Switch`／`toggleable` 這類自己就是語意合併邊界的元件沒有問題——它們在合併後仍是獨立的可點節點，
 * 整列同時給 [onClick] 也一樣（資料夾內容的子資料夾列就是整列可點加尾端 ⋯ 按鈕）。
 * 只有尾端元件是「只掛 `semantics { onClick }`、自己不合併」的自訂寫法時，才會被整列合併吃掉，
 * 這時把 [mergeSemantics] 設成 `false`，尾端元件才保有自己的語意。
 */
@Composable
fun VsListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    mergeSemantics: Boolean = true,
) {
    var m = modifier.fillMaxWidth()
    if (onClick != null) m = m.focusRing().clickable(role = Role.Button, onClick = onClick)
    if (mergeSemantics) m = m.semantics(mergeDescendants = true) {}
    m = m.defaultMinSize(minHeight = AppTheme.spacing.tap)
        .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3)
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3)) {
        if (icon != null) Icon(icon, null, tint = iconTint ?: AppTheme.colors.textDim, modifier = Modifier.size(22.dp))
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
