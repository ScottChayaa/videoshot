package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.TagChipMetrics
import com.xenyaa.videoshot.ui.common.TextFieldSize
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsChipSkeleton
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTextAction
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 每一頁最多幾列小膠囊；多的往右滑。 */
const val FILTER_ROWS_PER_PAGE = 4

/** 小膠囊左右間距（比一般的 8 少 1）與列距（44dp 觸控區彼此疊 2dp，看起來的間距少 2）。2026-10-09 scott 要求。 */
private val ChipGap = 7.dp
private val RowSpacing = (-2).dp

/**
 * 首頁的地點與標籤篩選抽屜（階段 17 設計決議 3）。純顯示元件——勾選、搜尋字都在
 * [FilterDraft]（`HomeViewModel` 管），這裡只畫 [FilterLists.visible] 算出來的結果。
 *
 * 【清除】只清草稿的勾選（有勾選才能按），要按【套用】才生效；關掉抽屜（滑掉、點背景、返回）＝草稿作廢。
 * 小膠囊不帶張數、是切換型（核取方塊語意，TalkBack 唸「已選取」）。
 *
 * 每一區的小膠囊**每頁最多 [FILTER_ROWS_PER_PAGE] 列，多的左右滑**，下面的點點表示第幾頁；
 * 每區最多列 [FilterLists.MAX_SHOWN] 個，其餘用搜尋框找。分頁要先知道每顆多寬：用文字量測算
 * （[TagChipMetrics]），不必先把小膠囊畫出來；分頁器只組看得到的那一頁，候選再多，一次也只畫幾十顆。
 *
 * **第一幀只畫抽屜外框與骨架**（[VsChipSkeleton]），之後一幀換一區（先地點、再標籤），抽屜滑動途中就出現：
 * 跟外框擠在第一幀，抽屜要等它畫完才開始滑（按下去頓一下）。小膠囊要夠輕，放在滑動途中才不會卡——
 * 文字在分頁時已經量過（[TagChipMetrics]），直接交給 [VsTagChip] 畫，不再排版第二次。
 * （2026-10-09 曾改成「停穩才畫」避開卡頓，scott 嫌小膠囊出現太晚，精簡小膠囊後改回滑動中出現。）
 *
 * `skipPartiallyExpanded = true`：理由同 [MonthPickerSheet]，也讓 Robolectric 不必等展開動畫。
 * 【套用】固定在底部不跟著捲，內容區太高時自己捲。
 *
 * @param options 候選（地點與標籤混在一起，這裡依 `isPlace` 分兩區）
 * @param status 候選讀取中：兩區各顯示骨架（已勾選的照樣列出，骨架接在後面）；讀取失敗：捲動區最上面
 *        「讀取失敗」＋【重試】（[onRetry] 只重讀候選，草稿不動）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    options: List<FilterOption>,
    draft: FilterDraft,
    onToggle: (FilterOption) -> Unit,
    onQuery: (isPlace: Boolean, text: String) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    status: FilterOptionsStatus = FilterOptionsStatus.READY,
    onRetry: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 0：只有骨架；1：地點區開始一列一列換上小膠囊；2：地點區換完，標籤區開始換。
    // 一幀只多畫一列（約 5 顆），抽屜滑動途中每幀的負擔夠小、不掉幀——實測一次畫兩區那一幀 50～60ms、
    // 一幀一區 30～47ms（正式版、完整編譯），都會在滑動途中掉幀
    var chipsStage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // 第一幀（抽屜外框＋骨架）畫完、抽屜開始滑了，才開始換
        withFrameNanos { }
        chipsStage = 1
    }
    VsBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // 有勾選才能按【清除】（主色＋底線），沒勾選時灰字不可點
        VsSheetHeader("篩選") {
            VsTextAction("清除", onClear, enabled = draft.places.isNotEmpty() || draft.tags.isNotEmpty())
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(top = AppTheme.spacing.s3, bottom = AppTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            // fill = false：內容少時抽屜只長到內容那麼高，多時才被限制在可用高度內、區塊自己捲；
            // 【套用】在捲動區外面，一直看得到
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s4),
            ) {
                if (status == FilterOptionsStatus.FAILED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "讀取失敗",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textDim,
                            modifier = Modifier.weight(1f),
                        )
                        VsButton("重試", onRetry, variant = ButtonVariant.Quiet)
                    }
                }
                FilterSectionBlock("地點", true, options, draft, status, chipsStage >= 1, onToggle, onQuery) {
                    if (chipsStage == 1) chipsStage = 2
                }
                FilterSectionBlock("標籤", false, options, draft, status, chipsStage >= 2, onToggle, onQuery) {}
            }
            VsButton("套用", onApply, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary)
        }
    }
}

@Composable
private fun FilterSectionBlock(
    title: String,
    isPlace: Boolean,
    options: List<FilterOption>,
    draft: FilterDraft,
    status: FilterOptionsStatus,
    chipsReady: Boolean,
    onToggle: (FilterOption) -> Unit,
    onQuery: (Boolean, String) -> Unit,
    onRevealed: () -> Unit,
) {
    val noun = if (isPlace) "地點" else "標籤"
    val section = FilterLists.visible(options, isPlace, draft)
    val query = draft.query[isPlace].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.text)
            VsTextField(
                value = query,
                onValueChange = { onQuery(isPlace, it) },
                modifier = Modifier.weight(1f),
                placeholder = "搜尋",
                size = TextFieldSize.Dense,
                semanticLabel = "搜尋$noun",
                leadingIcon = VsIcons.Search,
            )
        }
        if (!chipsReady) {
            // 滑動中：骨架的塊數跟等一下要畫的小膠囊差不多（有上限），換上小膠囊時抽屜高度不會差太多
            ChipSkeleton(section.shown.size.coerceIn(SKELETON_MIN, SKELETON_MAX))
        } else if (section.shown.isEmpty() && status == FilterOptionsStatus.READY) {
            Text(
                if (query.isBlank()) "這段時間沒有$noun" else "找不到符合的$noun",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
            )
        } else {
            if (section.shown.isNotEmpty()) {
                ChipPager(section.shown, isPlace, draft, query, onToggle, onRevealed)
            }
            // 讀取中：已勾選的在上面照樣列出，骨架接在後面，不說「這段時間沒有…」（其實還沒讀到）。
            // 失敗時說明與【重試】在捲動區最上面，這裡什麼都不加
            if (status == FilterOptionsStatus.LOADING) ChipSkeleton(SKELETON_MIN)
        }
        // 這一區沒有小膠囊要一列一列換（空的、還在讀），直接算換完，下一區才不會卡著
        val hasPager = chipsReady && section.shown.isNotEmpty()
        LaunchedEffect(chipsReady, hasPager) { if (chipsReady && !hasPager) onRevealed() }
        if (chipsReady && section.hiddenCount > 0) {
            Text(
                "還有 ${section.hiddenCount} 個沒列出，請用搜尋找",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun ChipSkeleton(count: Int) = VsChipSkeleton(count, chipGap = ChipGap, rowSpacing = RowSpacing)

/**
 * 一區的小膠囊：依寬度排列、每頁最多 [FILTER_ROWS_PER_PAGE] 列，左右滑換頁，超過一頁時下面有點點。
 * 分頁器的高度取最多列的那一頁，滑到列數較少的最後一頁時高度不會跳。搜尋字一變就回第一頁。
 *
 * 第一次出現時**一幀多畫一列**（高度一開始就是最終高度，版面不跳），畫完呼叫 [onRevealed]；
 * 之後換頁、搜尋都直接畫整頁。
 */
@Composable
private fun ChipPager(
    shown: List<FilterOption>,
    isPlace: Boolean,
    draft: FilterDraft,
    query: String,
    onToggle: (FilterOption) -> Unit,
    onRevealed: () -> Unit,
) {
    // 快取量過的名字：搜尋框每打一個字清單就變一次，同樣的名字不必重量
    val measurer = rememberTextMeasurer(cacheSize = FilterLists.MAX_SHOWN * 2)
    val textStyle = MaterialTheme.typography.bodySmall
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxWidth = constraints.maxWidth
        // 量好的文字排版留著，畫小膠囊時直接用（不再排版第二次）
        val layouts = remember(shown, textStyle, density) { shown.map { o -> measurer.measure(o.name, textStyle) } }
        val pages = remember(layouts, maxWidth) {
            val widths = layouts.map { TagChipMetrics.regularWidthPx(it.size.width, density.density) }
            paginateChips(widths, maxWidth, with(density) { ChipGap.roundToPx() }, FILTER_ROWS_PER_PAGE)
        }
        var revealedRows by remember { mutableIntStateOf(1) }
        LaunchedEffect(Unit) {
            while (revealedRows < FILTER_ROWS_PER_PAGE) {
                withFrameNanos { }
                revealedRows++
            }
            onRevealed()
        }
        val pagerState = rememberPagerState { pages.size }
        LaunchedEffect(query) { if (pagerState.currentPage != 0) pagerState.scrollToPage(0) }
        val rowCount = pages.maxOf { it.size }
        val pageHeight: Dp = AppTheme.spacing.tap * rowCount + RowSpacing * (rowCount - 1)
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(pageHeight),
                pageSpacing = AppTheme.spacing.s4,
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
                    for (row in pages[page].take(revealedRows)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                            for (i in row) {
                                val option = shown[i]
                                VsTagChip(
                                    name = option.name,
                                    kind = if (isPlace) ChipKind.PLACE else ChipKind.ofTagKind(option.tagKind),
                                    selected = draft.isSelected(option),
                                    isToggle = true,
                                    textLayout = layouts[i],
                                    onClick = { onToggle(option) },
                                )
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) PageDots(pages.size, pagerState.currentPage)
        }
    }
}

/** 分頁點點：目前那頁主色、其餘框線色。TalkBack 唸「第 N 頁，共 M 頁」。 */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "第 ${current + 1} 頁，共 $count 頁" },
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2, Alignment.CenterHorizontally),
    ) {
        repeat(count) { i ->
            val color = if (i == current) AppTheme.colors.accent else AppTheme.colors.borderStrong
            Box(Modifier.size(DotSize).background(color, CircleShape))
        }
    }
}

private val DotSize = 6.dp

/** 骨架的塊數：讀取中不知道會有幾個，放一排的量；滑動中依目前的候選數，有上限。 */
private const val SKELETON_MIN = 6
private const val SKELETON_MAX = 10
