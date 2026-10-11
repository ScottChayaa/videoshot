package com.xenyaa.videoshot.ui.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.TopBarIconButton
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsRadioRow
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.common.VsSearchTopBar
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 相簿清單頁（程式碼裡仍叫 folder）(手冊 §六、規格第六節)。**只列根層** —— 子資料夾在資料夾頁的上半。
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
    /** 讀取失敗時〔重試〕要做的事——接到 `FoldersViewModel::reload`。 */
    onRetry: () -> Unit,
    /** 卡片 ⋯ 的【封存相簿】——接到 `FoldersViewModel::archive`。 */
    onToggleArchive: (FolderCard) -> Unit,
    /** 下拉過門檻放手（或 TalkBack 自訂動作）——進封存相簿畫面。 */
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortPicking by remember { mutableStateOf(false) }
    val visible = FoldersStore.visible(state)

    Column(modifier.fillMaxSize()) {

        if (state.searching) {
            // 點〔搜尋〕整條頂欄換成搜尋框（同首頁篩選抽屜的搜尋框）；【取消】與系統返回鍵都清掉搜尋字並收回
            BackHandler { onSearching(false) }
            VsSearchTopBar(
                query = state.query,
                onQuery = onQuery,
                placeholder = "搜尋相簿名稱",
                onCancel = { onSearching(false) },
            )
        } else {
            VsTopBar("相簿") {
                TopBarIconButton(VsIcons.Search, "搜尋相簿", { onSearching(true) })
                TopBarIconButton(VsIcons.FolderPlus, "新增相簿", onStartCreate, tint = AppTheme.colors.accent)
            }
        }

        // 頂欄以下整段可以下拉：拉過門檻放手進封存相簿（規格第六節「進入封存相簿」）。
        // 用 modifier 而不是 PullToRefreshBox：1.4.0 的 PullToRefreshBox 沒有 enabled，搜尋時要能關掉。
        val pullState = rememberPullToRefreshState()
        Box(
            Modifier
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = false,
                    state = pullState,
                    enabled = !state.searching,
                    onRefresh = onOpenArchive,
                )
                // 膠囊從內容區頂端底下滑出來，不畫到頂欄上（格線自己會裁切，其他內容不會超出這個框）
                .clipToBounds(),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 狀態列（原型 `.fd-status`）：內距 上 8／左右 12／下 0。排序鍵右側往外 8（margin-right: -8），
                // 好讓文字右緣跟版面對齊；視覺高度不變，但觸控區撐到 44dp。
                Row(
                    Modifier.fillMaxWidth().padding(start = AppTheme.spacing.s3, top = AppTheme.spacing.s2, end = AppTheme.spacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.query.isBlank()) "${FoldersStore.active(state).size} 個相簿" else "符合 ${visible.size} 個",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textDim,
                        modifier = Modifier
                            .weight(1f)
                            // 手勢之外的入口：TalkBack 使用者沒辦法「下拉」。掛在這個可聚焦的文字節點上
                            // （外層容器不會被 TalkBack 聚焦，掛那裡等於沒有）；搜尋時跟手勢一樣關掉。
                            .then(
                                if (state.searching) Modifier
                                else Modifier.semantics {
                                    customActions = listOf(CustomAccessibilityAction("進入封存相簿") { onOpenArchive(); true })
                                },
                            ),
                    )
                    val sortShape = RoundedCornerShape(AppTheme.radii.sm)
                    Row(
                        Modifier
                            .focusRing(sortShape)
                            .clip(sortShape)
                            .clickable(role = Role.Button) { sortPicking = true }
                            .defaultMinSize(minHeight = AppTheme.spacing.tap)
                            .padding(horizontal = AppTheme.spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                    ) {
                        Text(state.sort.label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.text)
                        Icon(VsIcons.ChevronDown, contentDescription = null, tint = AppTheme.colors.textDim, modifier = Modifier.size(14.dp))
                    }
                }

                // 讀取失敗不能無聲無息（N2 的回歸測試）——不接住的話，下面的空狀態判斷式會把
                // 「讀取失敗」誤判成「還沒有任何分類」，主動說錯話。同 FolderScreen.kt 的 FolderErrorRow。
                if (state.error != null) {
                    FolderErrorRow(message = state.error, onRetry = onRetry)
                }

                when (FoldersStore.emptyKind(state)) {
                    FoldersEmpty.NO_FOLDERS -> PullableEmpty {
                        VsEmptyState(
                            message = "還沒有任何相簿",
                            icon = VsIcons.Folder,
                            actionText = "新增相簿",
                            onAction = onStartCreate,
                        )
                    }
                    FoldersEmpty.NO_MATCH -> PullableEmpty {
                        VsEmptyState(
                            message = "沒有符合的相簿",
                            icon = VsIcons.Folder,
                            actionText = "清除篩選",
                            onAction = { onQuery("") },
                            actionVariant = ButtonVariant.Secondary,
                        )
                    }
                    null -> FolderCardGrid(
                        cards = visible,
                        loader = loader,
                        onOpen = onOpen,
                        onRename = onStartRename,
                        onToggleArchive = onToggleArchive,
                        onDelete = onAskDelete,
                    )
                }
            }
            ArchivePullIndicator(pullState, Modifier.align(Alignment.TopCenter))
        }
    }

    if (sortPicking) {
        VsBottomSheet(onDismissRequest = { sortPicking = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
                Text(
                    "排序方式",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.text,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
                )
                // selectableGroup：TalkBack 唸「第 N 個，共 M 個」
                Column(Modifier.selectableGroup()) {
                    for (option in FolderSort.entries) {
                        VsRadioRow(
                            title = option.label,
                            selected = option == state.sort,
                            onSelect = { sortPicking = false; onSort(option) },
                        )
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

/**
 * 空狀態外面包一層可捲動容器——不能捲的內容收不到巢狀捲動，下拉進封存相簿就拉不動。
 * 全部相簿都封存時相簿畫面就是空狀態，拉不動等於封存的相簿找不回來。
 * `verticalScroll` 裡拿不到有限高度，用 `BoxWithConstraints` 取高度當最小高度，空狀態才能置中。
 */
@Composable
private fun PullableEmpty(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .testTag("foldersEmpty"),
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}
