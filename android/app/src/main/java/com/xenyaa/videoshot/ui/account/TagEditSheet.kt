package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

private fun iconOf(kind: TagKind): ImageVector = when (kind) {
    TagKind.PERSON -> VsIcons.Person
    TagKind.PET -> VsIcons.Pet
    TagKind.TOPIC -> VsIcons.Topic
    TagKind.OTHER -> VsIcons.OtherKind
}

/**
 * 標籤編輯抽屜：改名、改 kind、編輯別名、刪除（規格第九節「標籤管理的規則」）。
 * 純顯示元件——所有互動都經 `editor: TagEditor` 與回呼，狀態在 `AccountViewModel`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditSheet(
    editor: TagEditor,
    onDismiss: () -> Unit,
    onEditName: (String) -> Unit,
    onEditKind: (TagKind) -> Unit,
    onEditAliases: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(bottom = AppTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Text("編輯標籤", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)

            OutlinedTextField(
                value = editor.name,
                onValueChange = onEditName,
                label = { Text("名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("顯示圖示", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textDim)
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
                TagKind.entries.forEach { kind ->
                    FilterChip(
                        selected = kind == editor.kind,
                        onClick = { onEditKind(kind) },
                        label = { Text(kind.label) },
                        leadingIcon = { Icon(iconOf(kind), contentDescription = null) },
                    )
                }
            }

            OutlinedTextField(
                value = editor.aliasesRaw,
                onValueChange = onEditAliases,
                label = { Text("別名（逗號分隔，檢索時視同本名）") },
                placeholder = { Text("例：我家的貓、橘貓") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                TextButton(onClick = onDelete) {
                    Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger)
                    Text("刪除", color = AppTheme.colors.danger)
                }
                Button(
                    onClick = onSave,
                    enabled = editor.name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("儲存") }
            }
        }
    }
}
