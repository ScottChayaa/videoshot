package com.xenyaa.videoshot.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.xenyaa.videoshot.core.home.homeColumnsFor
import com.xenyaa.videoshot.core.home.monthLabel
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.ui.home.MonthPickerSheet
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.ui.thumb.labelOf

/**
 * 查詢分頁（規格第六節「查詢」、驗收手冊 §五）。條件與結果**同一頁切換**——
 * [SearchState.phase] 決定畫的是條件表單還是結果格線，不是導覽到另一個畫面。
 */
@Composable
fun SearchScreen(
    state: SearchState,
    loader: ThumbLoader,
    listState: LazyGridState,
    onSetMode: (SearchMode) -> Unit,
    onSetTextQuery: (String) -> Unit,
    onToggleFacet: (MonthFacet) -> Unit,
    onShowMoreFacets: () -> Unit,
    onPickMonth: (String?) -> Unit,
    onRunSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onShowConditions: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val showingResults = state.phase == SearchPhase.RESULTS

    // 捲到接近底部就補下一頁——寫法照 HomeScreen.kt／FolderScreen.kt：`nearEnd` 用
    // derivedStateOf 算，LaunchedEffect 的 block 不是常駐的 collector，而是每次 key 換了
    // 就重新跑一次、讀當下最新的 state。原本用 snapshotFlow.collect() 常駐訂閱，
    // block 裡讀的 state.results.size 卻是掛載當下就凍結的閉包，一直是初次進結果階段時
    // 的 0，等於門檻永遠是「>= -6」、每次捲動都會觸發（最終審查 Important 2）。
    val nearEnd by remember(listState, state.results.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.results.size - 6
        }
    }
    LaunchedEffect(nearEnd, state.tagCursor, state.textCursor, state.resultsLoading, showingResults) {
        if (showingResults && nearEnd && SearchStore.canLoadMore(state)) onLoadMore()
    }

    Column(Modifier.fillMaxSize()) {
        SearchTopBar(showingResults = showingResults, onBack = onShowConditions)

        if (showingResults) {
            ResultsPane(
                state = state, loader = loader, listState = listState,
                onOpen = onOpen, modifier = Modifier.weight(1f),
            )
        } else {
            ConditionsPane(
                state = state,
                onSetMode = onSetMode,
                onSetTextQuery = onSetTextQuery,
                onToggleFacet = onToggleFacet,
                onShowMoreFacets = onShowMoreFacets,
                onOpenDatePicker = { picking = true },
                modifier = Modifier.weight(1f),
            )
            SearchActionBar(state = state, onClick = onRunSearch)
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

@Composable
private fun SearchTopBar(showingResults: Boolean, onBack: () -> Unit) {
    if (showingResults) {
        VsTopBar("查詢結果", nav = TopBarNav.Back(onBack, label = "改條件"))
    } else {
        VsTopBar("查詢")
    }
}

@Composable
private fun ModeTabs(mode: SearchMode, onSetMode: (SearchMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
        FilterChip(selected = mode == SearchMode.TAG, onClick = { onSetMode(SearchMode.TAG) }, label = { Text("標籤與地點") })
        FilterChip(selected = mode == SearchMode.TEXT, onClick = { onSetMode(SearchMode.TEXT) }, label = { Text("描述") })
    }
}

private fun dateLabelOf(upToMonth: String?): String =
    if (upToMonth == null) "全部日期" else "${monthLabel(upToMonth)} 以前"

@Composable
private fun ConditionsPane(
    state: SearchState,
    modifier: Modifier = Modifier,
    onSetMode: (SearchMode) -> Unit,
    onSetTextQuery: (String) -> Unit,
    onToggleFacet: (MonthFacet) -> Unit,
    onShowMoreFacets: () -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AppTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        ModeTabs(mode = state.mode, onSetMode = onSetMode)

        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
            Text("時間", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textDim)
            OutlinedButton(onClick = onOpenDatePicker) {
                Text(dateLabelOf(state.upToMonth))
                Icon(VsIcons.Calendar, contentDescription = null, modifier = Modifier.padding(start = AppTheme.spacing.s1))
            }
        }

        when (state.mode) {
            SearchMode.TAG -> TagCloudPane(state = state, onToggleFacet = onToggleFacet, onShowMore = onShowMoreFacets)
            SearchMode.TEXT -> TextQueryPane(query = state.textQuery, onQueryChange = onSetTextQuery)
        }
    }
}

@Composable
private fun TagCloudPane(state: SearchState, onToggleFacet: (MonthFacet) -> Unit, onShowMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
        Text(
            "標籤與地點（${state.facets.size}${if (state.facetsHasMore) "+" else ""}）",
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.textDim,
        )
        when {
            state.facetsLoading -> CircularProgressIndicator(modifier = Modifier.size(AppTheme.spacing.s5))
            state.facets.isEmpty() -> Text("這個時間以前沒有標籤", color = AppTheme.colors.textDim)
            else -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                ) {
                    for (facet in state.facets) {
                        val key = facetKey(facet)
                        val isSelected = key in state.selected
                        InputChip(
                            selected = isSelected,
                            onClick = { onToggleFacet(facet) },
                            label = { Text("${facet.name} ${facet.count}") },
                            // Material3 1.4.0 的 InputChip 選取狀態只換底色，不會自己畫勾勾
                            // （反編譯過 1.4.0 原始碼確認：leadingIcon 完全交給呼叫端決定，
                            // KDoc 也建議選取時放打勾圖示）——手冊要求看得到勾勾，不是只換顏色。
                            leadingIcon = if (isSelected) {
                                { Icon(VsIcons.Check, contentDescription = null, modifier = Modifier.testTag("chipCheck")) }
                            } else null,
                        )
                    }
                }
                if (state.facetsHasMore) {
                    TextButton(onClick = onShowMore) { Text("顯示更多") }
                }
            }
        }
    }
}

@Composable
private fun TextQueryPane(query: String, onQueryChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
        Text("描述關鍵字", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textDim)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("例：加勒比海夜潛看到的大蝦") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun SearchActionBar(state: SearchState, onClick: () -> Unit) {
    val reason = SearchStore.disabledReason(state)
    Column(
        Modifier.fillMaxWidth().padding(AppTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
    ) {
        Button(
            onClick = onClick,
            enabled = SearchStore.canQuery(state),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(SearchStore.queryButtonLabel(state))
        }
        if (reason != null) {
            Text(reason, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResultsPane(
    state: SearchState,
    loader: ThumbLoader,
    listState: LazyGridState,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = homeColumnsFor(LocalConfiguration.current.screenWidthDp)
    Column(modifier.fillMaxWidth()) {
        ResultBar(state = state)
        if (state.error != null) {
            Text(state.error, color = AppTheme.colors.textDim, modifier = Modifier.padding(horizontal = AppTheme.spacing.s3))
        }
        if (state.results.isEmpty() && !state.resultsLoading) {
            SearchEmpty()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
            ) {
                items(state.results, key = { it.id }) { shot ->
                    ThumbImage(
                        shot = shot,
                        loader = loader,
                        contentDescription = labelOf(shot),
                        modifier = Modifier
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(AppTheme.radii.sm))
                            .focusRing(RoundedCornerShape(AppTheme.radii.sm))
                            .clickable(onClickLabel = "開啟") { onOpen(state.results.indexOf(shot)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultBar(state: SearchState) {
    Column(Modifier.fillMaxWidth().padding(AppTheme.spacing.s3), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
            Text("${state.total} 張", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)
            Text("·", color = AppTheme.colors.textDim)
            Text(dateLabelOf(state.upToMonth), color = AppTheme.colors.textDim)
        }
        ConditionChipsRow(state)
        state.heard?.let { heard ->
            Text(
                "聽懂了：${heard.text}${if (heard.local) "（本機解析）" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
            )
        }
    }
}

/**
 * 結果列的「條件 chips」（驗收手冊 §五：「N 張・全部日期・條件 chips」、mockup `tags.html`
 * 的 `renderResults()`）——標籤模式列出目前勾選的 facet，文字模式列出查詢字串本身。
 * 純顯示用，不能再點掉（改條件要靠上面的返回鍵），所以用 [AssistChip] 的 `onClick = {}`。
 *
 * 標籤模式**直接從 [SearchState.selected] 的 key 解析標籤／地點名稱來畫**，不查
 * [SearchState.facets]——`seedFromHome` 帶進來的那個 key 是從單一月份的 facet 挑出來的，
 * `loadFacets()` 之後重查的是**整個時間範圍**的 top-30 池子，很容易把它修剪掉
 * （`SearchStore.loadedFacets` 的 pruning）。查詢結果本身沒受影響，但如果 chips 還要
 * 反查 `state.facets` 才畫得出來，選到的條件就會憑空消失（最終審查 Important 3）。
 */
@Composable
private fun ConditionChipsRow(state: SearchState) {
    when (state.mode) {
        SearchMode.TAG -> {
            if (state.selected.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
                    for (key in state.selected) {
                        AssistChip(onClick = {}, label = { Text(nameOfFacetKey(key)) })
                    }
                }
            }
        }
        SearchMode.TEXT -> {
            if (state.textQuery.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text(state.textQuery) })
            }
        }
    }
}

/** [facetKey] 的反函式：`"$kind:$name"` 拆回 `name`——找不到分隔符就整段當名稱顯示。 */
private fun nameOfFacetKey(key: String): String {
    val sep = key.indexOf(':')
    return if (sep < 0) key else key.substring(sep + 1)
}

@Composable
private fun SearchEmpty() {
    Column(
        Modifier.fillMaxSize().padding(AppTheme.spacing.s5),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(VsIcons.Search, contentDescription = null, tint = AppTheme.colors.textDim)
        Text("沒有符合的收藏", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
    }
}
