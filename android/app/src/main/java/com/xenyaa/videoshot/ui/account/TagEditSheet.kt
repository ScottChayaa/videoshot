package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 標籤編輯抽屜：改名、改 kind、編輯別名、刪除（規格第九節「標籤管理的規則」）。
 * 純顯示元件——所有互動都經 `editor: TagEditor` 與回呼，狀態在 `AccountViewModel`。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditSheet(
    editor: TagEditor,
    onDismiss: () -> Unit,
    onEditName: (String) -> Unit,
    onEditKind: (TagKind) -> Unit,
    onEditAliases: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onStartMerge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VsBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(bottom = AppTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Text("編輯標籤", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)

            VsTextField(
                value = editor.name,
                onValueChange = onEditName,
                label = "名稱",
            )

            Text("顯示圖示", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textDim)
            // 原型 `.icon-pick`：四顆切換型膠囊，目前的種類選中（變色＋打勾，TalkBack 唸已選取）
            FlowRow(
                Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
            ) {
                TagKind.entries.forEach { kind ->
                    VsTagChip(
                        name = kind.label,
                        kind = ChipKind.ofTagKind(kind.id),
                        selected = kind == editor.kind,
                        isToggle = true,
                        onClick = { onEditKind(kind) },
                    )
                }
            }

            VsTextField(
                value = editor.aliasesRaw,
                onValueChange = onEditAliases,
                label = "別名（逗號分隔，檢索時視同本名）",
                placeholder = "例：我家的貓、橘貓",
            )

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                // 刪除標籤是破壞性動作（只解除關聯）：DangerQuiet；真正刪除前還有確認框
                VsButton("刪除", onDelete, variant = ButtonVariant.DangerQuiet, icon = VsIcons.Trash)
                VsButton("合併到…", onStartMerge, variant = ButtonVariant.Quiet)
                VsButton(
                    "儲存",
                    onSave,
                    Modifier.weight(1f),
                    variant = ButtonVariant.Primary,
                    enabled = editor.name.isNotBlank(),
                )
            }
        }
    }
}
