package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 標籤編輯抽屜：改名、編輯別名、合併、刪除（規格第九節「標籤管理的規則」）。
 * 純顯示元件——所有互動都經 `editor: TagEditor` 與回呼，狀態在 `AccountViewModel`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditSheet(
    editor: TagEditor,
    onDismiss: () -> Unit,
    onEditName: (String) -> Unit,
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

            VsTextField(
                value = editor.aliasesRaw,
                onValueChange = onEditAliases,
                label = "別名（逗號分隔，檢索時視同本名）",
                placeholder = "例：我家的貓、橘貓",
            )

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                // 刪除標籤是破壞性動作（只解除關聯）：DangerQuiet；真正刪除前還有確認框
                VsButton("刪除", onDelete, variant = ButtonVariant.DangerQuiet, icon = VsIcons.Trash)
                VsButton("合併到…", onStartMerge, variant = ButtonVariant.Secondary, icon = VsIcons.Merge)
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
