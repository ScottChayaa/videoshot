package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 下拉欄位（原型查詢頁的時間欄位 `.qfield`＋`.qfield-label`）：標籤在上（15 Medium `textDim`、下距 8），
 * 欄位 `surface` 底、1dp `borderStrong`、圓角 8、內距 12，值 17，右側 [VsIcons.ChevronDown]。
 * 點擊交給呼叫端（開月份挑選）。整個欄位是一顆按鈕，TalkBack 唸「{label}：{value}」
 * （上方的標籤文字不另外進語意，免得重複唸）。
 */
@Composable
fun VsSelectField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.padding(bottom = AppTheme.spacing.s2).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = AppTheme.colors.textDim,
        )
        Row(
            Modifier.fillMaxWidth()
                .focusRing(shape)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label：$value" }
                .background(AppTheme.colors.surface, shape)
                .border(1.dp, AppTheme.colors.borderStrong, shape)
                .defaultMinSize(minHeight = AppTheme.spacing.tap)
                .padding(AppTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            Text(
                value,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(VsIcons.ChevronDown, null, tint = AppTheme.colors.textDim, modifier = Modifier.size(18.dp))
        }
    }
}
