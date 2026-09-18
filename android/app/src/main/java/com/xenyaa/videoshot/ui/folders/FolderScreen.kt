package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.ui.thumb.labelOf

/** 子資料夾那一列的卡片寬度。只有這一頁用到，不升格成主題 token。 */
private val ChildCardWidth = 200.dp

/**
 * 資料夾頁：上半子資料夾、下半**本層**的圖（規格第六節）。
 *
 * 這一頁沒有挑圖介面 —— 空狀態要說得出圖從哪裡加，不然使用者會在這裡找半天。
 */
@Composable
fun FolderScreen(
    state: FolderState,
    loader: ThumbLoader,
    onBack: () -> Unit,
    onOpenChild: (FolderCard) -> Unit,
    onOpenShot: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onStartCreateChild: () -> Unit,
    /** 頂列〔⋯〕的〔改名〕——改的是**這一頁自己**。 */
    onStartRename: () -> Unit,
    /** 頂列〔⋯〕的〔刪除資料夾〕——刪的是**這一頁自己**。 */
    onAskDeleteSelf: () -> Unit,
    /** 上半子卡片的〔改名〕——改的是**那張子卡片**，不是這一頁（裁決 2）。 */
    onRenameChild: (FolderCard) -> Unit,
    /** 上半子卡片的〔刪除〕——問的是**那張子卡片**，不是這一頁（裁決 2）。 */
    onAskDeleteChild: (FolderCard) -> Unit,
    onEditorName: (String) -> Unit,
    onConfirmEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val canGoDeeper = (state.node?.depth ?: 1) < 5

    // 捲到最後一列就續載（寫法同 HomeScreen）
    LaunchedEffect(gridState, state.endReached) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= state.items.size - 6 && !state.endReached && !state.loading) onLoadMore()
            }
    }

    Column(modifier.fillMaxSize()) {

        Row(
            Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s2, vertical = AppTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape)) {
                Icon(VsIcons.Back, contentDescription = "返回", tint = AppTheme.colors.textDim)
            }
            Text(
                state.node?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.text,
                modifier = Modifier.weight(1f).padding(horizontal = AppTheme.spacing.s2),
            )
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape),
                ) {
                    Icon(VsIcons.More, contentDescription = "這個資料夾的更多操作", tint = AppTheme.colors.textDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("新增子資料夾") },
                        leadingIcon = { Icon(VsIcons.FolderPlus, contentDescription = null) },
                        // 規格：已在第 5 層則 UI 阻擋再建子層。停用之後要說得出原因，
                        // 不然使用者只會覺得按鈕壞了
                        enabled = canGoDeeper,
                        onClick = { menuOpen = false; onStartCreateChild() },
                    )
                    if (!canGoDeeper) {
                        Text(
                            "已經是第 5 層，不能再往下分",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textDim,
                            modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s1),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("改名") },
                        leadingIcon = { Icon(VsIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onStartRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("刪除資料夾", color = AppTheme.colors.danger) },
                        leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                        onClick = { menuOpen = false; onAskDeleteSelf() },
                    )
                }
            }
        }

        if (state.children.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s3),
                contentPadding = PaddingValues(horizontal = AppTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
            ) {
                items(state.children, key = { it.id }) { child ->
                    FolderCardTile(
                        card = child,
                        loader = loader,
                        onOpen = { onOpenChild(child) },
                        // 裁決 2：接的是這張子卡片自己，不是這一頁（onStartRename／onAskDeleteSelf）
                        onRename = { onRenameChild(child) },
                        onDelete = { onAskDeleteChild(child) },
                        modifier = Modifier.width(ChildCardWidth),
                    )
                }
            }
        }

        // 讀取中一律不算空狀態，否則每次進頁面都會閃一下空畫面（同 FoldersStore.emptyKind）
        if (state.items.isEmpty() && !state.loading) {
            Column(
                Modifier.fillMaxSize().padding(AppTheme.spacing.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("這個資料夾還沒有圖片", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textDim)
                Text(
                    "在圖片的全屏檢視裡用【加入分類】把圖放進來",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textFaint,
                    modifier = Modifier.padding(top = AppTheme.spacing.s2),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
            ) {
                itemsIndexed(state.items, key = { _, shot -> shot.id }) { index, shot ->
                    ThumbImage(
                        shot = shot,
                        loader = loader,
                        contentDescription = labelOf(shot),
                        modifier = Modifier
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(AppTheme.radii.sm))
                            .focusRing(RoundedCornerShape(AppTheme.radii.sm))
                            .clickable(onClickLabel = "開啟") { onOpenShot(index) },
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        FolderNameDialog(
            editor = editor,
            onName = onEditorName,
            onConfirm = onConfirmEditor,
            onDismiss = onDismissEditor,
        )
    }

    state.deleting?.let { card ->
        DeleteFolderDialog(card = card, onConfirm = onConfirmDelete, onDismiss = onDismissDelete)
    }
}
