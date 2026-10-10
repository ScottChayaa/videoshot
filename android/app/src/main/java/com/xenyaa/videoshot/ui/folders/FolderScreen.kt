package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.home.DEFAULT_THUMB_COLUMNS
import com.xenyaa.videoshot.core.home.thumbColumnsFor
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.common.TopBarIconButton
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.ui.thumb.ThumbGridGap
import com.xenyaa.videoshot.ui.thumb.ThumbTile

/**
 * 資料夾頁：上半子資料夾（直向清單列）、下半**本層**的圖（正方形縮圖牆）（規格第六節）。
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
    /** 頂列〔⋯〕的〔重新命名〕——改的是**這一頁自己**。 */
    onStartRename: () -> Unit,
    /** 頂列〔⋯〕的〔移除相簿〕——刪的是**這一頁自己**。 */
    onAskDeleteSelf: () -> Unit,
    /** 子資料夾列〔⋯〕的〔重新命名〕——改的是**那張子卡片**，不是這一頁（裁決 2）。 */
    onRenameChild: (FolderCard) -> Unit,
    /** 子資料夾列〔⋯〕的〔移除相簿〕——問的是**那張子卡片**，不是這一頁（裁決 2）。 */
    onAskDeleteChild: (FolderCard) -> Unit,
    onEditorName: (String) -> Unit,
    onConfirmEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** 縮圖手機寬度每列張數（帳號 › 縮圖；平板寬度會再加欄） */
    phoneColumns: Int = DEFAULT_THUMB_COLUMNS,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // 捲到剩最後幾列就先去要下一頁（寫法真的照 HomeScreen.kt：`nearEnd` 用 derivedStateOf
    // 算，`LaunchedEffect` 的 block 不是常駐的 collector，而是每次 key 換了就重新跑一次、
    // 讀當下最新的 state —— 跟原本用 snapshotFlow.collect() 常駐訂閱、block 裡的 state
    // 卻是掛載當下就凍結的閉包不一樣）。
    //
    // `state.loading` 一定要在 key 裡（同 HomeScreen.kt 的理由）：不放的話，掛載當下如果
    // 剛好凍結到 `loading = true`（`init { reload() }` 一開始就是），`!state.loading`
    // 之後永遠是 false，捲動再也不會續載。
    // 子資料夾清單是網格最前面的一個整列 item（有子資料夾才有），圖從 headerCount 之後才開始算
    val headerCount = if (state.children.isEmpty()) 0 else 1
    val nearEnd by remember(gridState, state.items.size, headerCount) {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last - headerCount >= state.items.size - 6
        }
    }
    LaunchedEffect(nearEnd, state.cursor, state.loading) {
        if (nearEnd && !state.loading && !state.endReached) onLoadMore()
    }

    Column(modifier.fillMaxSize()) {

        VsTopBar(state.node?.name.orEmpty(), nav = TopBarNav.Back(onBack)) {
            Box {
                TopBarIconButton(VsIcons.More, "這個相簿的更多操作", { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("重新命名") },
                        leadingIcon = { Icon(VsIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onStartRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("移除相簿", color = AppTheme.colors.danger) },
                        leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                        onClick = { menuOpen = false; onAskDeleteSelf() },
                    )
                }
            }
        }

        // 麵包屑（原型 `.fd-breadcrumb`）：15 textDim，內距上 12 左右 16。還沒載到（空）就先不畫
        if (state.breadcrumb.isNotEmpty()) {
            Text(
                "相簿 / " + state.breadcrumb.joinToString(" / "),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, top = AppTheme.spacing.s3),
            )
        }

        // 讀取失敗不能無聲無息（同 HomeScreen.kt 的 HomeErrorRow）——不接住的話，下面的空狀態
        // 判斷式會把「讀取失敗」誤判成「這個資料夾真的是空的」，主動說錯話（審查 Important 3）。
        if (state.error != null) {
            FolderErrorRow(message = state.error, onRetry = onLoadMore)
        }

        // 子資料夾清單與圖放在同一個網格裡一起捲：子資料夾多的時候，單獨一塊不捲的區域會把縮圖牆擠掉。
        // 縮圖左右貼齊螢幕邊緣、格線 ThumbGridGap，同首頁；子資料夾清單自己留左右 16。
        LazyVerticalGrid(
            columns = GridCells.Fixed(thumbColumnsFor(LocalConfiguration.current.screenWidthDp, phoneColumns)),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = AppTheme.spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(ThumbGridGap),
            verticalArrangement = Arrangement.spacedBy(ThumbGridGap),
        ) {
            if (state.children.isNotEmpty()) {
                item(key = "children", span = { GridItemSpan(maxLineSpan) }) {
                    // 左右 16、上 4（網格上內距 12，合起來是原型 `.fd-list` 的上 16）；列間距 8，與下面的圖隔 12
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AppTheme.spacing.s4,
                                end = AppTheme.spacing.s4,
                                top = AppTheme.spacing.s1,
                                bottom = AppTheme.spacing.s3,
                            ),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                    ) {
                        state.children.forEach { child ->
                            ChildFolderRow(
                                card = child,
                                onOpen = { onOpenChild(child) },
                                // 裁決 2：接的是這張子資料夾自己，不是這一頁（onStartRename／onAskDeleteSelf）
                                onRename = { onRenameChild(child) },
                                onDelete = { onAskDeleteChild(child) },
                            )
                        }
                    }
                }
            }

            // 讀取中一律不算空狀態，否則每次進頁面都會閃一下空畫面（同 FoldersStore.emptyKind）；
            // 讀取失敗也不算——上面已經有 FolderErrorRow 說清楚了，這裡不能再說「資料夾是空的」
            // 蓋過去（審查 Important 3）。
            if (state.items.isEmpty() && !state.loading && state.error == null) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        VsEmptyState("這個相簿還沒有圖片", icon = VsIcons.ImagePlus)
                        Text(
                            "在圖片的全屏檢視裡用【加入相簿】把圖放進來",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = AppTheme.spacing.s4),
                        )
                    }
                }
            }

            itemsIndexed(state.items, key = { _, shot -> shot.id }) { index, shot ->
                // 正方形、直角、focusRing、Role.Button 與「開啟」都在 ThumbTile 裡
                ThumbTile(shot = shot, loader = loader, onClick = { onOpenShot(index) })
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

/**
 * 子資料夾的一列（原型 `.fd-row`）：資料夾圖示（accent）＋名稱＋張數＋看得見的〔⋯〕。
 *
 * 整列點了進入子資料夾；〔⋯〕與長按都開同一個選單（手冊 §六第二條：長按仍有效，但不是唯一入口）。
 * 跟 [FolderCardTile]（分類首頁用的卡片）分開寫，不動它。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChildFolderRow(
    card: FolderCard,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.border, shape)
            .focusRing(shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "開啟",
                onLongClickLabel = "更多操作",
                onLongClick = { menuOpen = true },
                onClick = onOpen,
            )
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        Icon(VsIcons.Folder, contentDescription = null, tint = AppTheme.colors.accent)
        Text(
            card.name,
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${card.shotCount} 張",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
        )
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape),
            ) {
                Icon(VsIcons.More, contentDescription = "「${card.name}」的更多操作", tint = AppTheme.colors.textDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("重新命名") },
                    leadingIcon = { Icon(VsIcons.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    // 破壞性動作：唯一用 danger 色的地方（手冊 §零第二條）
                    text = { Text("移除相簿", color = AppTheme.colors.danger) },
                    leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
