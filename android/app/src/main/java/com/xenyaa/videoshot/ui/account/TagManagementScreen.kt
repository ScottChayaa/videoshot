package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.data.repo.model.TagUsage
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 帳號頁「標籤管理」子畫面：全部標籤 ＋ kind ＋ 使用張數（規格第九節）。 */
@Composable
fun TagManagementScreen(
    state: AccountState,
    onBack: () -> Unit,
    onOpenEditor: (TagUsage) -> Unit,
    onDismissEditor: () -> Unit,
    onEditName: (String) -> Unit,
    onEditKind: (TagKind) -> Unit,
    onEditAliases: (String) -> Unit,
    onRequestSave: () -> Unit,
    onConfirmMerge: () -> Unit,
    onDismissMerge: () -> Unit,
    onAskDelete: (TagUsage) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AccountSettingHeader("標籤管理", onBack)
        Text(
            "全部標籤（${state.tags.size}）",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textDim,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        )
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(state.tags, key = { it.id }) { tag ->
                TagRow(tag, onClick = { onOpenEditor(tag) })
            }
        }
        Text(
            "點一個標籤可以改名、編輯別名或刪除；刪除只解除關聯，圖不會被刪。",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textDim,
            modifier = Modifier.padding(AppTheme.spacing.s4),
        )
    }

    val editor = state.editor
    if (editor != null) {
        TagEditSheet(
            editor = editor,
            onDismiss = onDismissEditor,
            onEditName = onEditName,
            onEditKind = onEditKind,
            onEditAliases = onEditAliases,
            onSave = onRequestSave,
            onDelete = { state.tags.firstOrNull { it.id == editor.id }?.let(onAskDelete) },
        )
    }

    val pendingMerge = state.pendingMerge
    if (pendingMerge != null) {
        AlertDialog(
            onDismissRequest = onDismissMerge,
            title = { Text("合併到「$pendingMerge」") },
            text = { Text("已經有標籤叫「$pendingMerge」，這次改名會把兩者的圖合併到既有標籤，這個標籤會被刪除。") },
            confirmButton = { TextButton(onClick = onConfirmMerge) { Text("合併") } },
            dismissButton = { TextButton(onClick = onDismissMerge) { Text("取消") } },
        )
    }

    val deleting = state.deleting
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("刪除「${deleting.name}」") },
            text = { Text("只解除標籤關聯，收藏的圖不會被刪除。") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("刪除", color = AppTheme.colors.danger) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("取消") } },
        )
    }
}

@Composable
private fun TagRow(tag: TagUsage, onClick: () -> Unit) {
    val kind = TagKind.byId(tag.kind)
    val icon = when (kind) {
        TagKind.PERSON -> VsIcons.Person
        TagKind.PET -> VsIcons.Pet
        TagKind.TOPIC -> VsIcons.Topic
        TagKind.OTHER -> VsIcons.OtherKind
    }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing()
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.textDim)
        Column(Modifier.weight(1f).padding(start = AppTheme.spacing.s3)) {
            Text(tag.name, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            Text(
                "${kind.label} ・ ${tag.shotCount} 張",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}
