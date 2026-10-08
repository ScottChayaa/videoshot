package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.home.DEFAULT_THUMB_COLUMNS
import com.xenyaa.videoshot.core.home.thumbColumnsFor
import com.xenyaa.videoshot.core.home.monthLabel
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.chipKindOf
import com.xenyaa.videoshot.ui.common.TopBarIconButton
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.ui.thumb.ThumbGridGap
import com.xenyaa.videoshot.ui.thumb.ThumbTile

/**
 * 首頁：依 `event_date` 年月分組的縮圖牆，由新到舊。
 *
 * **每個月份同欄數**（手機欄數看帳號 › 縮圖的設定、§零「600dp 以上加欄」）——
 * 欄數隨當月張數變的話，捲動時每個月的格子大小都不一樣。
 * 月份標題（右邊帶該月地點）是吸頂的 `stickyHeader`；其他標籤在下一列、不吸頂。都佔滿一整列。
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
    /** 月份標籤點下去＝在首頁只篩這一個（階段 17 設計決議 6），不再跳到別的分頁 */
    onFacetClick: (MonthFacet) -> Unit,
    /** 開地點與標籤篩選抽屜；抽屜開不開看 `state.draft` */
    onOpenFilter: () -> Unit = {},
    onToggleDraft: (FilterOption) -> Unit = {},
    onDraftQuery: (isPlace: Boolean, text: String) -> Unit = { _, _ -> },
    onExpandDraft: (isPlace: Boolean) -> Unit = {},
    onClearDraft: () -> Unit = {},
    onApplyFilter: () -> Unit = {},
    /** 關抽屜但不套用（滑掉、點背景、返回）：草稿作廢 */
    onDismissFilter: () -> Unit = {},
    /** 清掉**已套用**的地點與標籤篩選（空狀態的按鈕用） */
    onClearFilter: () -> Unit = {},
    /** 手機寬度每列張數（帳號 › 縮圖；平板寬度會再加欄） */
    phoneColumns: Int = DEFAULT_THUMB_COLUMNS,
    /** 取圖完成後要捲到的月份（`YYYY-MM`）；null＝不用捲 */
    scrollToMonth: String? = null,
    /** 捲完（或發現那個月不在清單裡）回報一次，讓外面把 scrollToMonth 清掉 */
    onScrolledToMonth: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val columns = thumbColumnsFor(LocalConfiguration.current.screenWidthDp, phoneColumns)
    val slots = remember(state.items, state.facets) { HomeStore.slots(state) }

    // 目前「真的吸在頂端」的是哪個月的標題 —— 只有它要加陰影。標題還在原本位置（清單頂端）時
    // 沒有東西從底下捲過去，不加，不然陰影會壓在第二列標籤或第一列縮圖上。
    val stuckMonth by remember(listState, slots) {
        derivedStateOf {
            val first = slots.getOrNull(listState.firstVisibleItemIndex) ?: return@derivedStateOf null
            if (first is HomeSlot.Header && listState.firstVisibleItemScrollOffset == 0) null else first.month
        }
    }

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

    // 篩選（時間範圍或地點／標籤）換了就回頂端 —— 選到的那個月是第一個分組，停在原本的捲動位置會看不到它。
    //
    // 拿 rememberSaveable 記「上一次真的套用過的篩選」而不是直接把 upToMonth 當 key ——
    // 這個 composable 離開過 composition 再回來的每一次（切分頁、開關 Lightbox）都是一次
    // *全新*的掛載，LaunchedEffect 不管 key 值有沒有變都會重新跑一次區塊。如果只看 key，
    // 捲到第 400 張開一張圖、關掉，回來就會被強制捲回頂端（見階段 7 全盤覆查第 4 點）。
    // 用 rememberSaveable 的初始值直接帶入目前的 upToMonth，新掛載的當下兩者天生相等，
    // 這一次自然不會觸發；真的換了篩選（掛載期間 upToMonth 改變）才會不相等而捲動。
    //
    // key 是把「時間範圍＋地點＋標籤」攤成一個字串（rememberSaveable 存得下；`HomeFilter` 不是可存的型別）。
    val filterKey = filterScrollKey(state.upToMonth, state.filter)
    var lastAppliedFilter by rememberSaveable { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (filterKey != lastAppliedFilter) {
            lastAppliedFilter = filterKey
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

        HomeTopBar(
            upToMonth = state.upToMonth,
            filter = state.filter,
            onOpenMonths = { picking = true },
            onOpenFilter = onOpenFilter,
        )

        // 讀取失敗不能無聲無息：loading 沒有接住例外就會卡在 true、清單再也不會重試
        // （見階段 7 全盤覆查第 2 點）。這裡只是提示＋重試，不是破壞性動作，不用 danger 色。
        if (state.error != null) {
            HomeErrorRow(message = state.error, onRetry = onLoadMore)
        }

        // 用 if/else 而不是提早 return —— 空狀態也要能往下走到選擇器那一段，
        // 不然篩選出 0 筆結果時按日曆鈕會完全沒反應（手冊 §二：這個鈕本來就該打得開選擇器）。
        if (state.items.isEmpty() && state.endReached) {
            HomeEmpty(
                timeFiltered = state.upToMonth != null,
                facetFiltered = !state.filter.isEmpty,
                onClearTime = { onPickMonth(null) },
                onClearFacets = onClearFilter,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                // 縮圖左右貼齊螢幕邊緣（不留外距，縮圖盡量大；偏離原型 `.date-group` 的左右 12），
                // 月份標題與標籤列自己留左右 12。
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(ThumbGridGap),
                verticalArrangement = Arrangement.spacedBy(ThumbGridGap),
            ) {
                // 月份標題吸頂：往下捲時當月標題貼在頂欄下方，捲到下個月由下個月的標題推走接手。
                // 標題要一個一個用 stickyHeader 宣告，所以這裡逐格走 slots，不能整份丟給 items()。
                for (slot in slots) {
                    when (slot) {
                        is HomeSlot.Header -> stickyHeader(key = slot.key, contentType = "header") {
                            MonthHeader(
                                label = slot.label,
                                // 篩選中不畫月份旁的地點小膠囊（設計決議 4）
                                places = if (state.filter.isEmpty) state.facets[slot.month].orEmpty().filter { it.isPlace } else emptyList(),
                                stuck = slot.month == stuckMonth,
                                onFacetClick = onFacetClick,
                            )
                        }

                        is HomeSlot.Facets -> item(key = slot.key, span = { GridItemSpan(maxLineSpan) }, contentType = "facets") {
                            MonthFacetRow(
                                facets = state.facets[slot.month].orEmpty().filterNot { it.isPlace },
                                onClick = onFacetClick,
                            )
                        }

                        // 正方形、直角、focusRing、Role.Button 與「開啟」都在 ThumbTile 裡
                        // （真的是按鈕：鍵盤與輔助技術都到得了，手冊 §二最後一條）
                        is HomeSlot.Tile -> item(key = slot.key, contentType = "tile") {
                            ThumbTile(
                                shot = slot.shot,
                                loader = loader,
                                onClick = { onOpen(slot.index) },
                            )
                        }
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
                filtering = !state.filter.isEmpty,
            )
        }

        state.draft?.let { draft ->
            FilterSheet(
                options = state.filterOptions,
                draft = draft,
                onToggle = onToggleDraft,
                onQuery = onDraftQuery,
                onExpand = onExpandDraft,
                onClear = onClearDraft,
                onApply = onApplyFilter,
                onDismiss = onDismissFilter,
            )
        }
    }
}

/** 換了這個字串就是換了篩選（任何一項不同都算），畫面要捲回頂端。 */
private fun filterScrollKey(upToMonth: String?, filter: HomeFilter): String =
    listOf(
        upToMonth.orEmpty(),
        filter.places.sorted().joinToString("\u0001"),
        filter.tags.sorted().joinToString("\u0001"),
    ).joinToString("\u0000")

/**
 * 右上角兩顆鈕：〔依地點與標籤篩選〕〔依時間篩選〕。有篩選時那一顆變成主色淺底圓形＋主色圖示，
 * 不另外佔一列狀態列；清除時間篩選在月份選擇器的【清除】，清除地點與標籤篩選在抽屜的【清除篩選】。
 * TalkBack 用 stateDescription 唸出目前的篩選。
 */
@Composable
private fun HomeTopBar(upToMonth: String?, filter: HomeFilter, onOpenMonths: () -> Unit, onOpenFilter: () -> Unit) {
    VsTopBar("收藏", divider = false) {
        TopBarIconButton(
            VsIcons.Filter,
            "依地點與標籤篩選",
            onOpenFilter,
            modifier = if (!filter.isEmpty) {
                Modifier.semantics { stateDescription = "已套用 ${filter.size} 個篩選條件" }
            } else {
                Modifier
            },
            active = !filter.isEmpty,
        )
        TopBarIconButton(
            VsIcons.Calendar,
            "依時間篩選",
            onOpenMonths,
            modifier = if (upToMonth != null) {
                Modifier.semantics { stateDescription = "只顯示 ${monthLabel(upToMonth)} 以前的收藏" }
            } else {
                Modifier
            },
            active = upToMonth != null,
        )
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
 * 月份標題列（吸頂）：左邊年月，右邊是該月出現過的**地點**；其他標籤在下一列（[MonthFacetRow]），不吸頂。
 * 地點單行橫向捲動，捲不下的往右滑。
 *
 * 吸頂時縮圖會從底下捲過，所以整列鋪實色底；[stuck]（真的吸在頂端）時底下加一道陰影跟縮圖分層，
 * 還在原位時不加。最小高度固定，有沒有地點的月份標題一樣高，吸頂換月時不會跳動。
 */
private val HeaderShadowHeight = 10.dp

@Composable
private fun MonthHeader(label: String, places: List<MonthFacet>, stuck: Boolean, onFacetClick: (MonthFacet) -> Unit) {
    val shadowAlpha by animateFloatAsState(if (stuck) 1f else 0f, label = "monthHeaderShadow")
    Row(
        Modifier
            // 陰影畫在自己範圍外，墊高一層確保不會被後畫的縮圖蓋掉
            .zIndex(1f)
            .fillMaxWidth()
            // 陰影自己畫在標題下緣外面：系統的 elevation 陰影在淺色底、縮圖上幾乎看不見（實機驗過）
            .drawWithContent {
                drawContent()
                if (shadowAlpha > 0f) {
                    val h = HeaderShadowHeight.toPx()
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.2f * shadowAlpha), Color.Transparent),
                            startY = size.height,
                            endY = size.height + h,
                        ),
                        topLeft = Offset(0f, size.height),
                        size = Size(size.width, h),
                    )
                }
            }
            .background(AppTheme.colors.bg)
            .heightIn(min = AppTheme.spacing.tap)
            .padding(vertical = AppTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = AppTheme.colors.text,
            maxLines = 1,
            modifier = Modifier.padding(start = AppTheme.spacing.s3, end = AppTheme.spacing.s2),
        )
        if (places.isNotEmpty()) {
            Row(
                // 右邊 12 放在捲動內容裡：靜止時跟螢幕右緣留白，捲動時小膠囊仍可捲到螢幕邊緣
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = AppTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FacetChips(places, onFacetClick)
            }
        }
    }
}

/**
 * 該月地點以外的標籤（原型 `.month-tags`），在月份標題列下方，不吸頂。單行橫向捲動。
 * 小膠囊帶種類圖示與顏色，種類看 [MonthFacet.tagKind]。
 */
@Composable
private fun MonthFacetRow(facets: List<MonthFacet>, onClick: (MonthFacet) -> Unit) {
    Row(
        // 左右 12 放在捲動內容裡：靜止時跟月份標題對齊，捲動時小膠囊仍可捲到螢幕邊緣
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = AppTheme.spacing.s3, end = AppTheme.spacing.s3, bottom = AppTheme.spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FacetChips(facets, onClick)
    }
}

@Composable
private fun FacetChips(facets: List<MonthFacet>, onClick: (MonthFacet) -> Unit) {
    for (facet in facets) {
        // 跟查詢頁同一款可點小膠囊（無框、主色淺底、四角圓角）
        VsTagChip(
            name = facet.name,
            kind = chipKindOf(facet),
            onClick = { onClick(facet) },
        )
    }
}

@Composable
private fun HomeEmpty(timeFiltered: Boolean, facetFiltered: Boolean, onClearTime: () -> Unit, onClearFacets: () -> Unit) {
    if (facetFiltered) {
        // 地點與標籤篩選優先：【清除篩選】直接清掉已套用的篩選（設計決議 4）
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            VsEmptyState(
                message = "沒有符合篩選的收藏",
                actionText = "清除篩選",
                onAction = onClearFacets,
            )
        }
    } else if (timeFiltered) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            VsEmptyState(
                message = "這個時間點以前沒有收藏",
                actionText = "清除時間篩選",
                onAction = onClearTime,
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

