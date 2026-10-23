package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 已選的標籤：外觀同 [VsTagChip]（中性半透明底、無框、帶 `#` 圖示），尾端多一個關閉圖示，整顆可點＝移除。
 * 名稱「移除標籤 X」讓輔助技術聽得出點下去會發生什麼事。
 * 照片編輯抽屜與取圖第三步（含批次編輯）共用。
 */
@Composable
fun VsRemovableTag(name: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    // 觸控區至少 44dp 高（手冊 §零），看得到的膠囊置中、跟其他小膠囊一樣矮
    Box(
        modifier
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .semantics(mergeDescendants = true) { contentDescription = "移除標籤 $name" }
            .clickable(role = Role.Button, onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .focusRing(shape)
                .clip(shape)
                .background(AppTheme.colors.text.copy(alpha = UNSELECTED_FILL_ALPHA))
                .padding(horizontal = AppTheme.spacing.s2, vertical = AppTheme.spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
        ) {
            Icon(ChipKind.TAG.icon, contentDescription = null, tint = ChipKind.TAG.color, modifier = Modifier.size(16.dp))
            Text(name, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.text)
            Icon(VsIcons.Close, contentDescription = null, tint = AppTheme.colors.textDim, modifier = Modifier.size(14.dp))
        }
    }
}
