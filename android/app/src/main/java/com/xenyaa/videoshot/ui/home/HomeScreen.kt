package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import com.xenyaa.videoshot.core.home.homeColumnsFor
import com.xenyaa.videoshot.core.home.monthLabel
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/** 縮圖的無障礙名稱。有描述就唸描述，沒有就唸中性字樣加秒數（手冊 §零「輔助操作」）。 */
private fun labelOf(shot: ShotRow): String =
    shot.description?.takeIf { it.isNotBlank() } ?: "片段縮圖 ${formatClock(shot.atSec)}"

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

    // 篩選換了就回頂端 —— 選到的那個月是第一個分組，停在原本的捲動位置會看不到它
    LaunchedEffect(state.upToMonth) { listState.scrollToItem(0) }

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

        // 用 if/else 而不是提早 return —— 空狀態也要能往下走到選擇器那一段，
        // 不然篩選出 0 筆結果時按日曆鈕會完全沒反應（手冊 §二：這個鈕本來就該打得開選擇器）。
        if (state.items.isEmpty() && state.endReached) {
            HomeEmpty(filtered = state.upToMonth != null, onClearFilter = { onPickMonth(null) })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
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
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.text,
                            modifier = Modifier.padding(top = AppTheme.spacing.s4, bottom = AppTheme.spacing.s2),
                        )

                        is HomeSlot.Facets -> MonthFacetRow(
                            facets = state.facets[slot.month].orEmpty(),
                            onClick = { onFacetClick(slot.month, it) },
                        )

                        is HomeSlot.Tile -> ThumbImage(
                            shot = slot.shot,
                            loader = loader,
                            contentDescription = labelOf(slot.shot),
                            modifier = Modifier
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(AppTheme.radii.sm))
                                // 真的是按鈕：鍵盤與輔助技術都到得了（手冊 §二最後一條）
                                .clickable(onClickLabel = "開啟") { onOpen(slot.index) },
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
    Row(
        Modifier.fillMaxWidth().padding(
            start = AppTheme.spacing.s4, end = AppTheme.spacing.s2, top = AppTheme.spacing.s2,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "收藏",
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenFilter, modifier = Modifier.size(AppTheme.spacing.tap)) {
            Icon(VsIcons.Calendar, contentDescription = "依時間篩選", tint = AppTheme.colors.textDim)
        }
    }
}

/** 啟用篩選時才出現的可清除狀態列（手冊 §二第四條）。 */
@Composable
private fun FilterBar(month: String, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s1)
            .clip(RoundedCornerShape(AppTheme.radii.sm))
            .background(AppTheme.colors.accentWeak)
            .padding(AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VsIcons.Calendar, contentDescription = null, tint = AppTheme.colors.accent)
        Text(
            "只顯示 ${monthLabel(month)} 以前的收藏",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.text,
            modifier = Modifier.weight(1f).padding(horizontal = AppTheme.spacing.s2),
        )
        IconButton(onClick = onClear, modifier = Modifier.size(AppTheme.spacing.tap)) {
            Icon(VsIcons.Close, contentDescription = "清除時間篩選", tint = AppTheme.colors.textDim)
        }
    }
}

/** 該月出現過的地點與標籤。⏳ 單行橫向捲動（規格第六節；換行排列尚未確認）。 */
@Composable
private fun MonthFacetRow(facets: List<MonthFacet>, onClick: (MonthFacet) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = AppTheme.spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
    ) {
        for (facet in facets) {
            AssistChip(onClick = { onClick(facet) }, label = { Text(facet.name) })
        }
    }
}

@Composable
private fun HomeEmpty(filtered: Boolean, onClearFilter: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(AppTheme.spacing.s5),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (filtered) {
            Text("這個時間點以前沒有收藏", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            TextButton(onClick = onClearFilter) { Text("清除時間篩選") }
        } else {
            Text("還沒有收藏", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)
            Text(
                "按下方的【取圖】，貼一支 YouTube 網址就可以開始",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}
