package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.data.repo.model.TagUsage
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.common.VsSettingDivider
import com.xenyaa.videoshot.ui.common.VsSettingGroup
import com.xenyaa.videoshot.ui.common.VsSettingNote
import com.xenyaa.videoshot.ui.folders.FolderErrorRow

/** 帳號頁「標籤管理」子畫面：全部標籤 ＋ kind ＋ 使用張數（規格第九節）。
 * @param onRetry 讀取／改名／刪除失敗時的〔重試〕——接到 `AccountViewModel::reload`
 *   （最終審查 Important 3：`AccountState.error` 原本設定了卻沒有任何畫面讀它，
 *   跟 `FoldersScreen`／`FolderScreen` 同一套 `FolderErrorRow` 元件）。 */
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
    /** 讀取／改名／刪除失敗時的〔重試〕——見上面 KDoc；預設空白，既有呼叫端／測試
     * 不關心錯誤重試時不必跟著改。 */
    onStartMerge: () -> Unit = {},
    onPickMergeTarget: (Long) -> Unit = {},
    onDismissMergePicker: () -> Unit = {},
    onCancelMergeTarget: () -> Unit = {},
    onConfirmMergeTarget: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AccountSettingHeader("標籤管理", onBack)
        if (state.error != null) {
            FolderErrorRow(message = state.error, onRetry = onRetry)
        }
        // 標籤數量是個人圖庫的量級（數十到數百），清單放在 VsSettingGroup 裡跟著整頁一起捲動
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            VsSettingGroup(title = "全部標籤（${state.tags.size}）") {
                state.tags.forEachIndexed { index, tag ->
                    if (index > 0) VsSettingDivider()
                    TagRow(tag, onClick = { onOpenEditor(tag) })
                }
            }
            VsSettingNote("點一個標籤可以改名、編輯別名或刪除；刪除只解除關聯，圖不會被刪。")
        }
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
            onStartMerge = onStartMerge,
            onDelete = { state.tags.firstOrNull { it.id == editor.id }?.let(onAskDelete) },
        )
    }

    val pendingMerge = state.pendingMerge
    if (pendingMerge != null) {
        AlertDialog(
            onDismissRequest = onDismissMerge,
            title = { Text("合併到「$pendingMerge」") },
            text = { Text("已經有標籤叫「$pendingMerge」，這次改名會把兩者的圖合併到既有標籤，這個標籤會被刪除。") },
            confirmButton = { VsButton("合併", onConfirmMerge, variant = ButtonVariant.Primary) },
            dismissButton = { VsButton("取消", onDismissMerge, variant = ButtonVariant.Quiet) },
        )
    }

    val picking = state.mergePicking
    if (picking != null && picking.kind == MergeKind.TAG) {
        MergePickerSheet(
            sourceName = picking.fromName,
            candidates = state.tags.filter { it.id != picking.fromId }.map {
                MergeCandidate(it.id, it.name, it.shotCount, ChipKind.ofTagKind(it.kind).icon, ChipKind.ofTagKind(it.kind).color)
            },
            onPick = onPickMergeTarget,
            onDismiss = onDismissMergePicker,
        )
    }

    val mergeConfirm = state.mergeConfirm
    if (mergeConfirm != null && mergeConfirm.request.kind == MergeKind.TAG) {
        val from = mergeConfirm.request
        AlertDialog(
            onDismissRequest = onCancelMergeTarget,
            title = { Text("合併到「${mergeConfirm.toName}」") },
            text = { Text("「${from.fromName}」的 ${from.fromCount} 張圖會改成「${mergeConfirm.toName}」，「${from.fromName}」會變成它的別名。") },
            confirmButton = { VsButton("合併", onConfirmMergeTarget, variant = ButtonVariant.Primary) },
            dismissButton = { VsButton("取消", onCancelMergeTarget, variant = ButtonVariant.Quiet) },
        )
    }

    if (state.merging) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("合併中…") },
            text = { Text("圖很多時要幾秒鐘，請稍候。") },
            confirmButton = {},
        )
    }

    val deleting = state.deleting
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("刪除「${deleting.name}」") },
            // 16C 設計決議 5：提醒改用合併
            text = { Text("只解除標籤關聯，收藏的圖不會被刪除。要把它併到別的標籤，請改用【合併到…】。") },
            // 刪除標籤是破壞性動作：Danger（只解除關聯，圖不會被刪）
            confirmButton = { VsButton("刪除", onConfirmDelete, variant = ButtonVariant.Danger) },
            dismissButton = { VsButton("取消", onDismissDelete, variant = ButtonVariant.Quiet) },
        )
    }
}

/** 標籤清單列：圖示＝種類圖示＋種類色（人物／動物／主題／其他，同首頁的小膠囊），副標＝種類 ・ 張數。 */
@Composable
private fun TagRow(tag: TagUsage, onClick: () -> Unit) {
    val kind = TagKind.byId(tag.kind)
    val chipKind = ChipKind.ofTagKind(tag.kind)
    VsListRow(
        title = tag.name,
        subtitle = "${kind.label} ・ ${tag.shotCount} 張",
        icon = chipKind.icon,
        iconTint = chipKind.color,
        onClick = onClick,
    )
}
