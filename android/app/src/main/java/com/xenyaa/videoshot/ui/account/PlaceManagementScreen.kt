package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.data.repo.model.PlaceUsage
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.common.VsSettingDivider
import com.xenyaa.videoshot.ui.common.VsSettingGroup
import com.xenyaa.videoshot.ui.common.VsSettingNote
import com.xenyaa.videoshot.ui.folders.FolderErrorRow
import com.xenyaa.videoshot.ui.icons.VsIcons

/** 帳號頁「地點管理」子畫面：全部地點 ＋ 使用張數（結構比照 [TagManagementScreen]）。 */
@Composable
fun PlaceManagementScreen(
    state: AccountState,
    onBack: () -> Unit,
    onOpenEditor: (PlaceUsage) -> Unit,
    onDismissEditor: () -> Unit,
    onEditName: (String) -> Unit,
    onEditAliases: (String) -> Unit,
    onRequestSave: () -> Unit,
    onConfirmMerge: () -> Unit,
    onDismissMerge: () -> Unit,
    onAskDelete: (PlaceUsage) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AccountSettingHeader("地點管理", onBack)
        if (state.error != null) {
            FolderErrorRow(message = state.error, onRetry = onRetry)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            VsSettingGroup(title = "全部地點（${state.places.size}）") {
                state.places.forEachIndexed { index, place ->
                    if (index > 0) VsSettingDivider()
                    VsListRow(
                        title = place.name,
                        subtitle = "${place.shotCount} 張",
                        icon = VsIcons.MapPin,
                        iconTint = ChipKind.PLACE.color,
                        onClick = { onOpenEditor(place) },
                    )
                }
            }
            VsSettingNote("點一個地點可以改名、編輯別名、合併或刪除；刪除只會讓那些圖沒有地點，圖不會被刪。")
        }
    }

    val editor = state.placeEditor
    if (editor != null) {
        PlaceEditSheet(
            editor = editor,
            onDismiss = onDismissEditor,
            onEditName = onEditName,
            onEditAliases = onEditAliases,
            onSave = onRequestSave,
            onDelete = { state.places.firstOrNull { it.id == editor.id }?.let(onAskDelete) },
        )
    }

    val pendingMerge = state.placePendingMerge
    if (pendingMerge != null) {
        AlertDialog(
            onDismissRequest = onDismissMerge,
            title = { Text("合併到「$pendingMerge」") },
            text = { Text("已經有地點叫「$pendingMerge」，這次改名會把兩者的圖合併到既有地點，這個地點的名字會變成它的別名。") },
            confirmButton = { VsButton("合併", onConfirmMerge, variant = ButtonVariant.Primary) },
            dismissButton = { VsButton("取消", onDismissMerge, variant = ButtonVariant.Quiet) },
        )
    }

    val deleting = state.placeDeleting
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("刪除「${deleting.name}」") },
            text = { Text("這 ${deleting.shotCount} 張圖會變成沒有地點，圖不會被刪。要把它併到別的地點，請改用【合併到…】。") },
            confirmButton = { VsButton("刪除", onConfirmDelete, variant = ButtonVariant.Danger) },
            dismissButton = { VsButton("取消", onDismissDelete, variant = ButtonVariant.Quiet) },
        )
    }
}
