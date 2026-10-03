package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.home.homeColumnsFor
import com.xenyaa.videoshot.core.home.monthLabel
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.ChipSize
import com.xenyaa.videoshot.ui.common.TopBarIconButton
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import androidx.compose.foundation.shape.CircleShape
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.ui.thumb.ThumbTile

/**
 * 首頁：依 `event_date` 年月分組的縮圖牆，由新到舊。
 *
 * **每個月份同欄數**（手冊 §二「固定三欄」、§零「600dp 以上加欄」）——
 * 欄數隨當月張數變的話，捲動時每個月的格子大小都不一樣。
 * 月份標題與標籤列各佔滿一整列（`GridItemSpan(maxLineSpan)`）。
 */
@Composable
fun HomeScreen(
    state: HomeState,
    loader: ThumbLoader,
    listState: LazyGridState,
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit,
    /** null＝清除篩選。開關選擇器是畫面自己的事，不必讓外面知道 */
    onPickMonth: (String?) -> Unit,
    onFacetClick: (String, MonthFacet) -> Unit,
    /** 取圖完成後要捲到的月份（`YYYY-MM`）；null＝不用捲 */
    scrollToMonth: String? = null,
    /** 捲完（或發現那個月不在清單裡）回報一次，讓外面把 scrollToMonth 清掉 */
    onScrolledToMonth: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val columns = homeColumnsFor(LocalConfiguration.current.screenWidthDp)
    val slots = remember(state.items, state.facets) { HomeStore.slots(state) }

    // 捲到剩最後一列時先去要下一頁，使用者才不會看到清單「停住」
    val nearEnd by remember(listState, state.items.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - columns - 1
        }
    }
    LaunchedEffect(nearEnd, state.cursor, state.loading) {
        if (nearEnd && HomeStore.canLoadMore(state)) onLoadMore()
    }

    // 篩選換了就回頂端 —— 選到的那個月是第一個分組，停在原本的捲動位置會看不到它。
    //
    // 拿 rememberSaveable 記「上一次真的套用過的篩選」而不是直接把 upToMonth 當 key ——
    // 這個 composable 離開過 composition 再回來的每一次（切分頁、開關 Lightbox）都是一次
    // *全新*的掛載，LaunchedEffect 不管 key 值有沒有變都會重新跑一次區塊。如果只看 key，
    // 捲到第 400 張開一張圖、關掉，回來就會被強制捲回頂端（見階段 7 全盤覆查第 4 點）。
    // 用 rememberSaveable 的初始值直接帶入目前的 upToMonth，新掛載的當下兩者天生相等，
    // 這一次自然不會觸發；真的換了篩選（掛載期間 upToMonth 改變）才會不相等而捲動。
    var lastAppliedFilter by rememberSaveable { mutableStateOf(state.upToMonth) }
    LaunchedEffect(state.upToMonth) {
        if (state.upToMonth != lastAppliedFilter) {
            lastAppliedFilter = state.upToMonth
            listState.scrollToItem(0)
        }
    }

    // 取圖完成導回首頁要捲到新圖那個月（手冊 §四第三步最後一條）。
    //
    // `state.loading` 一定要在 key 裡：外面呼叫 reload() 之後，items 會先被清空、
    // 補資料的 coroutine 才在稍後把新的一頁塞回來 —— 這一段「暫時是空的」是正常流程，
    // 不是「這個月真的不在」。如果趁這個空檔就把 onScrolledToMonth() 回報掉，
    // 外面的 scrollToMonth 會被清成 null，資料補齊後這個效果不會再跑第二次，月份就永遠捲不到。
    LaunchedEffect(scrollToMonth, slots, state.loading) {
        val month = scrollToMonth ?: return@LaunchedEffect
        val index = HomeStore.headerIndexOf(slots, month)
        when {
            index != null -> {
                listState.scrollToItem(index)
                onScrolledToMonth()
            }
            // 還在補資料：什麼都不做，等下一次資料到位再重新判斷一次
            state.loading -> Unit
            // 資料已經到位、確定找不到（例如那個月根本不在已載入的分頁範圍內）才放棄
            else -> onScrolledToMonth()
        }
    }

    Column(modifier.fillMaxSize()) {

        HomeTopBar(onOpenFilter = { picking = true })

        if (state.upToMonth != null) {
            FilterBar(month = state.upToMonth, onClear = { onPickMonth(null) })
        }

        // 讀取失敗不能無聲無息：loading 沒有接住例外就會卡在 true、清單再也不會重試
        // （見階段 7 全盤覆查第 2 點）。這裡只是提示＋重試，不是破壞性動作，不用 danger 色。
        if (state.error != null) {
            HomeErrorRow(message = state.error, onRetry = onLoadMore)
        }

        // 用 if/else 而不是提早 return —— 空狀態也要能往下走到選擇器那一段，
        // 不然篩選出 0 筆結果時按日曆鈕會完全沒反應（手冊 §二：這個鈕本來就該打得開選擇器）。
        if (state.items.isEmpty() && state.endReached) {
            HomeEmpty(filtered = state.upToMonth != null, onClearFilter = { onPickMonth(null) })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.s3),
                // 格線 2dp：刻意偏離 4/8/12/16/24/32 尺標——原型 `.tiles { gap: 2px }`，
                // 縮圖牆要靠得緊才像一整面牆（首頁與資料夾內容共用這個例外）。
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
                verticalArrangement = Arrangement.spacedBy(TILE_GAP),
            ) {
                items(
                    items = slots,
                    key = { it.key },
                    // 月份標題與標籤列各佔滿一整列，縮圖各佔一格
                    span = { slot -> if (slot is HomeSlot.Tile) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                ) { slot ->
                    when (slot) {
                        is HomeSlot.Header -> Text(
                            slot.label,
                            // 原型 `.date-group { padding: 24 12 0 }`、`h2 { margin-bottom: 12 }`；20 Bold
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AppTheme.colors.text,
                            modifier = Modifier.padding(top = AppTheme.spacing.s5, bottom = AppTheme.spacing.s3),
                        )

                        is HomeSlot.Facets -> MonthFacetRow(
                            facets = state.facets[slot.month].orEmpty(),
                            onClick = { onFacetClick(slot.month, it) },
                        )

                        // 正方形、圓角、focusRing、Role.Button 與「開啟」都在 ThumbTile 裡
                        // （真的是按鈕：鍵盤與輔助技術都到得了，手冊 §二最後一條）
                        is HomeSlot.Tile -> ThumbTile(
                            shot = slot.shot,
                            loader = loader,
                            onClick = { onOpen(slot.index) },
                        )
                    }
                }
            }
        }

        if (picking) {
            MonthPickerSheet(
                months = state.months,
                selected = state.upToMonth,
                onPick = { picking = false; onPickMonth(it) },
                onClear = { picking = false; onPickMonth(null) },
                onDismiss = { picking = false },
            )
        }
    }
}

@Composable
private fun HomeTopBar(onOpenFilter: () -> Unit) {
    VsTopBar("收藏") {
        TopBarIconButton(VsIcons.Calendar, "依時間篩選", onOpenFilter)
    }
}

/**
 * 啟用篩選時才出現的可清除狀態列（手冊 §二第四條；原型 `.filter-bar`）。
 * 外距左右 16 上 12，內距上下 8、左 12、右 8（右邊留給 44dp 的清除鈕）。
 */
@Composable
private fun FilterBar(month: String, onClear: () -> Unit) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, top = AppTheme.spacing.s3)
            .clip(shape)
            .background(AppTheme.colors.accentWeak)
            .border(BorderStroke(1.dp, AppTheme.colors.accentLine), shape)
            .padding(start = AppTheme.spacing.s3, end = AppTheme.spacing.s2, top = AppTheme.spacing.s2, bottom = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VsIcons.Calendar, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
        Text(
            "只顯示 ${monthLabel(month)} 以前的收藏",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.text,
            modifier = Modifier.weight(1f).padding(horizontal = AppTheme.spacing.s2),
        )
        IconButton(onClick = onClear, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape)) {
            Icon(VsIcons.Close, contentDescription = "清除時間篩選", tint = AppTheme.colors.textDim)
        }
    }
}

/** 讀取失敗的提示列 ＋ 重試。不是破壞性動作，一律不用 danger 色。 */
@Composable
private fun HomeErrorRow(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        VsButton("重試", onRetry, variant = ButtonVariant.Quiet)
    }
}

/**
 * 該月出現過的地點與標籤（原型 `.month-tags`）。⏳ 單行橫向捲動（規格第六節；換行排列尚未確認）。
 * 迷你小膠囊帶種類圖示與顏色；[MonthFacet.kind] 是 `"place"` 或 `"tag"`，標籤的種類看 [MonthFacet.tagKind]。
 */
/** 月份標籤的小膠囊種類：地點固定是 [ChipKind.PLACE]；標籤看 [MonthFacet.tagKind]（不認得的退回 OTHER）。 */
internal fun chipKindOf(facet: MonthFacet): ChipKind =
    if (facet.kind == "place") ChipKind.PLACE else ChipKind.ofTagKind(facet.tagKind)

@Composable
private fun MonthFacetRow(facets: List<MonthFacet>, onClick: (MonthFacet) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = AppTheme.spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (facet in facets) {
            VsTagChip(
                name = facet.name,
                kind = chipKindOf(facet),
                size = ChipSize.Mini,
                onClick = { onClick(facet) },
            )
        }
    }
}

@Composable
private fun HomeEmpty(filtered: Boolean, onClearFilter: () -> Unit) {
    if (filtered) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            VsEmptyState(
                message = "這個時間點以前沒有收藏",
                actionText = "清除時間篩選",
                onAction = onClearFilter,
            )
        }
    } else {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VsEmptyState(message = "還沒有收藏", icon = VsIcons.ImagePlus)
            Text(
                "按下方的【取圖】，貼一支 YouTube 網址就可以開始",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.s4),
            )
        }
    }
}

private val TILE_GAP = 2.dp
