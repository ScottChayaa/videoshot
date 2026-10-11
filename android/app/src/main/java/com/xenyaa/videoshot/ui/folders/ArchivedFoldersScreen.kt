package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 封存相簿畫面（規格第六節）。跟相簿畫面共用 `FoldersViewModel` 的同一份 `cards`，
 * 只畫封存的（`FoldersStore.archived`，封存時間新到舊）；沒有搜尋、排序，也沒有下拉。
 * 卡片 ⋯ 的第二項是【取消封存】（`FolderCardTile` 依 `archivedAt` 自己換）。
 */
@Composable
fun ArchivedFoldersScreen(
    state: FoldersState,
    loader: ThumbLoader,
    onBack: () -> Unit,
    onOpen: (FolderCard) -> Unit,
    onUnarchive: (FolderCard) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (FolderCard) -> Unit,
    onEditorName: (String) -> Unit,
    onStartRename: (FolderCard) -> Unit,
    onAskDelete: (FolderCard) -> Unit,
    onDismissEditor: () -> Unit,
    onDismissDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        VsTopBar("封存相簿", nav = TopBarNav.Back(onBack))

        if (state.error != null) {
            FolderErrorRow(message = state.error, onRetry = onRetry)
        }

        if (FoldersStore.archivedEmpty(state)) {
            VsEmptyState(
                message = "還沒有封存的相簿",
                modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically),
                icon = VsIcons.Archive,
                secondary = "在相簿的 ⋯ 選單選【封存相簿】",
            )
        } else {
            FolderCardGrid(
                cards = FoldersStore.archived(state),
                loader = loader,
                onOpen = onOpen,
                onRename = onStartRename,
                onToggleArchive = onUnarchive,
                onDelete = onAskDelete,
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
        DeleteFolderDialog(card = card, onConfirm = { onDelete(card) }, onDismiss = onDismissDelete)
    }
}
