package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 分類清單頁(手冊 §六、規格第六節)。**只列根層** —— 子資料夾在資料夾頁的上半。
 *
 * 排序用文字不用圖示(狀態列那顆按鈕):箭頭圖示說不出「現在是照什麼排的」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    state: FoldersState,
    loader: ThumbLoader,
    onOpen: (FolderCard) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (FolderCard) -> Unit,
    onQuery: (String) -> Unit,
    /** 對話框裡打字 —— 接到 `FoldersStore.editName`，順便把上一次的錯誤訊息清掉 */
    onEditorName: (String) -> Unit,
    onSearching: (Boolean) -> Unit,
    onSort: (FolderSort) -> Unit,
    onStartCreate: () -> Unit,
    onStartRename: (FolderCard) -> Unit,
    onAskDelete: (FolderCard) -> Unit,
    onDismissEditor: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortPicking by remember { mutableStateOf(false) }
    val visible = FoldersStore.visible(state)

    Column(modifier.fillMaxSize()) {

        Row(
            Modifier.fillMaxWidth().padding(
                start = AppTheme.spacing.s4, end = AppTheme.spacing.s2, top = AppTheme.spacing.s2,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "分類",
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onSearching(!state.searching) },
                modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape),
            ) {
                Icon(VsIcons.Search, contentDescription = "篩選分類名稱", tint = AppTheme.colors.textDim)
            }
            IconButton(
                onClick = onStartCreate,
                modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape),
            ) {
                Icon(VsIcons.FolderPlus, contentDescription = "新增資料夾", tint = AppTheme.colors.accent)
            }
        }

        if (state.searching) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text("搜尋分類名稱") },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "搜尋分類名稱" },
                )
                TextButton(onClick = { onSearching(false) }) { Text("取消") }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.query.isBlank()) "${state.cards.size} 個分類" else "符合 ${visible.size} 個",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { sortPicking = true }, modifier = Modifier.focusRing()) {
                Text(state.sort.label)
            }
        }

        when (FoldersStore.emptyKind(state)) {
            FoldersEmpty.NO_FOLDERS -> EmptyBlock(
                message = "還沒有任何分類",
                actionLabel = "新增資料夾",
                onAction = onStartCreate,
            )
            FoldersEmpty.NO_MATCH -> EmptyBlock(
                message = "沒有符合的分類",
                actionLabel = "清除篩選",
                onAction = { onQuery("") },
            )
            null -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
            ) {
                items(visible, key = { it.id }) { card ->
                    FolderCardTile(
                        card = card,
                        loader = loader,
                        onOpen = { onOpen(card) },
                        onRename = { onStartRename(card) },
                        onDelete = { onAskDelete(card) },
                    )
                }
            }
        }
    }

    if (sortPicking) {
        ModalBottomSheet(
            onDismissRequest = { sortPicking = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppTheme.colors.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
                Text(
                    "排序方式",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.text,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
                )
                for (option in FolderSort.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .focusRing()
                            .clickable { sortPicking = false; onSort(option) }
                            .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option == state.sort) AppTheme.colors.accent else AppTheme.colors.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (option == state.sort) {
                            Icon(VsIcons.Check, contentDescription = "目前的排序", tint = AppTheme.colors.accent)
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        FolderNameDialog(
            editor = editor,
            onName = onEditorName,
            onConfirm = {
                if (editor.target == null) onCreate(editor.name.trim()) else onRename(editor.target, editor.name.trim())
            },
            onDismiss = onDismissEditor,
        )
    }

    state.deleting?.let { card ->
        DeleteFolderDialog(card = card, onConfirm = { onDelete(card) }, onDismiss = onDismissDelete)
    }
}

@Composable
private fun EmptyBlock(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(AppTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textDim)
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = AppTheme.spacing.s4).focusRing(CircleShape),
        ) { Text(actionLabel) }
    }
}
