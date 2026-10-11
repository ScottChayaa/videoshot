package com.xenyaa.videoshot.ui.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.common.SelectionBarAction
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsSelectionBar
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 封存相簿畫面（規格第六節）。跟相簿畫面共用 `FoldersViewModel` 的同一份 `cards`，
 * 只畫封存的（`FoldersStore.archived`，封存時間新到舊）；沒有搜尋、排序，也沒有下拉。
 * 長按多選同相簿畫面，底部動作列是【重新命名】【取消封存】【刪除】。
 */
@Composable
fun ArchivedFoldersScreen(
    state: FoldersState,
    loader: ThumbLoader,
    onBack: () -> Unit,
    onOpen: (FolderCard) -> Unit,
    onRename: (Long, String) -> Unit,
    /** 刪除確認框（一本或多本）按下【刪除】 */
    onDeleteSelected: () -> Unit,
    onEditorName: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onDismissDelete: () -> Unit,
    onRetry: () -> Unit,
    /** 長按（進入多選）與多選中點一下都走這裡 */
    onToggleSelect: (Long) -> Unit,
    /** 頂欄【✕】與系統返回鍵：離開多選 */
    onClearSelection: () -> Unit,
    /** 底部【重新命名】（只有選一本時能按） */
    onRenameSelected: () -> Unit,
    /** 底部【取消封存】 */
    onUnarchiveSelected: () -> Unit,
    /** 底部【刪除】：開確認框 */
    onAskDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selecting = state.selection.isNotEmpty()
    BackHandler(enabled = selecting) { onClearSelection() }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            VsTopBar("已選 ${state.selection.size} 本", nav = TopBarNav.Close(onClearSelection, "取消選取"), divider = false)
        } else {
            VsTopBar("封存相簿", nav = TopBarNav.Back(onBack))
        }

        if (state.error != null) {
            FolderErrorRow(message = state.error, onRetry = onRetry)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (FoldersStore.archivedEmpty(state)) {
                VsEmptyState(
                    message = "還沒有封存的相簿",
                    modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically),
                    icon = VsIcons.Archive,
                    secondary = "長按相簿，再按底部的【封存】",
                )
            } else {
                FolderCardGrid(
                    cards = FoldersStore.archived(state),
                    loader = loader,
                    selection = state.selection,
                    onOpen = onOpen,
                    onToggleSelect = { onToggleSelect(it.id) },
                )
            }
        }

        if (selecting) {
            VsSelectionBar(
                listOf(
                    SelectionBarAction(VsIcons.Edit, "重新命名", onRenameSelected, enabled = state.selection.size == 1),
                    SelectionBarAction(VsIcons.ArchiveRestore, "取消封存", onUnarchiveSelected),
                    SelectionBarAction(VsIcons.Trash, "刪除", onAskDeleteSelected),
                ),
            )
        }
    }

    state.editor?.let { editor ->
        // 這個畫面不能新增相簿，editor.target 一定有值
        FolderNameDialog(
            editor = editor,
            onName = onEditorName,
            onConfirm = { editor.target?.let { onRename(it, editor.name.trim()) } },
            onDismiss = onDismissEditor,
        )
    }

    state.deleting?.let { card ->
        DeleteFolderDialog(card = card, onConfirm = onDeleteSelected, onDismiss = onDismissDelete)
    }

    state.deletingMany?.let { cards ->
        DeleteFoldersDialog(count = cards.size, onConfirm = onDeleteSelected, onDismiss = onDismissDelete)
    }
}
