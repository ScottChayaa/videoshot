package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 每一頁最多幾列小膠囊；多的往右滑。 */
const val CHIP_ROWS_PER_PAGE = 4

/** 小膠囊左右間距（比一般的 8 少 1）與列距（44dp 觸控區彼此疊 2dp，看起來的間距少 2）。2026-10-09 scott 要求。 */
val ChipPagerGap = 7.dp
val ChipPagerRowSpacing = (-2).dp

/** 一次最多量幾段文字（量測快取的大小）。 */
private const val MEASURE_CACHE = 200

/**
 * 小膠囊分頁（純函式）：依寬度由左到右排成列，排不下就換列，每 [rowsPerPage] 列一頁。
 * 一顆比整列還寬的照樣自己佔一列（畫的時候會被截）。
 *
 * @param widths 每顆的寬度（px），順序就是顯示順序
 * @return 頁 → 列 → 那一列的項目索引
 */
fun paginateChips(widths: List<Int>, maxWidth: Int, gap: Int, rowsPerPage: Int): List<List<List<Int>>> {
    require(rowsPerPage > 0) { "rowsPerPage 要大於 0" }
    val rows = mutableListOf<MutableList<Int>>()
    var used = 0
    widths.forEachIndexed { i, w ->
        val row = rows.lastOrNull()
        if (row != null && used + gap + w <= maxWidth) {
            row += i
            used += gap + w
        } else {
            rows += mutableListOf(i)
            used = w
        }
    }
    return rows.chunked(rowsPerPage)
}

/**
 * 一組小膠囊：依寬度排列、每頁最多 [CHIP_ROWS_PER_PAGE] 列，左右滑換頁，超過一頁時下面有點點。
 * 首頁篩選抽屜與詳細資料抽屜共用（2026-10-12 從篩選抽屜抽出來）。
 *
 * 分頁要先知道每顆多寬：用文字量測算（[TagChipMetrics]），不必先把小膠囊畫出來；分頁器只組看得到的那一頁，
 * 候選再多，一次也只畫幾十顆。分頁器的高度取最多列的那一頁，滑到列數較少的最後一頁時高度不會跳。
 * [resetKey] 一變（例如搜尋字）就回第一頁。
 *
 * [progressive] = true 時，第一次出現**一幀多畫一列**（高度一開始就是最終高度，版面不跳），畫完呼叫 [onRevealed]；
 * 之後換頁、搜尋都直接畫整頁（見 AGENTS.md「畫面載入分幀優化」）。
 *
 * @param names 不可為空（呼叫端自己處理「沒有候選」的文字）
 */
@Composable
fun VsChipPager(
    names: List<String>,
    kind: ChipKind,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selected: (Int) -> Boolean = { false },
    isToggle: Boolean = false,
    resetKey: Any? = null,
    progressive: Boolean = false,
    onRevealed: () -> Unit = {},
) {
    if (names.isEmpty()) return
    // 快取量過的名字：搜尋框每打一個字清單就變一次，同樣的名字不必重量
    val measurer = rememberTextMeasurer(cacheSize = MEASURE_CACHE)
    val textStyle = MaterialTheme.typography.bodySmall
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidth = constraints.maxWidth
        // 量好的文字排版留著，畫小膠囊時直接用（不再排版第二次）
        val layouts = remember(names, textStyle, density) { names.map { measurer.measure(it, textStyle) } }
        val pages = remember(layouts, maxWidth) {
            val widths = layouts.map { TagChipMetrics.regularWidthPx(it.size.width, density.density) }
            paginateChips(widths, maxWidth, with(density) { ChipPagerGap.roundToPx() }, CHIP_ROWS_PER_PAGE)
        }
        var revealedRows by remember { mutableIntStateOf(if (progressive) 1 else CHIP_ROWS_PER_PAGE) }
        LaunchedEffect(Unit) {
            while (revealedRows < CHIP_ROWS_PER_PAGE) {
                withFrameNanos { }
                revealedRows++
            }
            onRevealed()
        }
        val pagerState = rememberPagerState { pages.size }
        LaunchedEffect(resetKey) { if (pagerState.currentPage != 0) pagerState.scrollToPage(0) }
        val rowCount = pages.maxOf { it.size }
        val pageHeight: Dp = AppTheme.spacing.tap * rowCount + ChipPagerRowSpacing * (rowCount - 1)
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(pageHeight),
                pageSpacing = AppTheme.spacing.s4,
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(ChipPagerRowSpacing)) {
                    for (row in pages[page].take(revealedRows)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ChipPagerGap)) {
                            for (i in row) {
                                VsTagChip(
                                    name = names[i],
                                    kind = kind,
                                    selected = selected(i),
                                    isToggle = isToggle,
                                    textLayout = layouts[i],
                                    onClick = { onClick(i) },
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
