package com.xenyaa.videoshot.ui.search

import com.xenyaa.videoshot.core.paging.SearchCursor
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.ShotRow

enum class SearchMode { TAG, TEXT }
enum class SearchPhase { CONDITIONS, RESULTS }

/**
 * 文字查詢的「聽懂了」摘要（規格第八節）。
 * @param local true＝規則式解析（畫面要加註「（本機解析）」），false＝Gemini 解析
 */
data class ResolvedSummary(val text: String, val local: Boolean)

/** chip 的穩定識別鍵，格式 `"$kind:$name"`——跟 mockup `mock-data.js` 的 `facetKey()` 同一個形狀。 */
fun facetKey(facet: MonthFacet): String = "${facet.kind}:${facet.name}"

/**
 * 查詢分頁的全部狀態。
 *
 * @param selected 目前勾選的 chip（[facetKey] 集合），只在 [SearchMode.TAG] 有意義
 * @param months 日期選擇器的選項（沿用首頁 `MonthPickerSheet` 的資料形狀，來源是同一個
 *        `LibraryRepo.monthCounts()`——查詢頁跟首頁看的是同一份「有收藏的月份」清單）
 * @param results 目前**已載入**的結果（keyset 一次一頁）；Lightbox 左右滑動的範圍就是它
 * @param total 符合目前條件的**總**張數
 * @param tagCursor／textCursor 兩種模式各自的游標——同一時間只有一個有意義（依 [mode]）
 * @param heard 文字查詢「聽懂了：…」摘要；標籤模式或還沒查過時是 null
 * @param error 上一次讀取失敗的訊息；不是例外物件，理由同 `HomeState`
 */
data class SearchState(
    val mode: SearchMode = SearchMode.TAG,
    val phase: SearchPhase = SearchPhase.CONDITIONS,
    val upToMonth: String? = null,
    val months: List<MonthCount> = emptyList(),
    val facetsLoading: Boolean = false,
    val facets: List<MonthFacet> = emptyList(),
    val facetsHasMore: Boolean = false,
    val selected: Set<String> = emptySet(),
    val textQuery: String = "",
    val results: List<ShotRow> = emptyList(),
    val total: Int = 0,
    val tagCursor: ShotCursor? = null,
    val textCursor: SearchCursor? = null,
    val resultsLoading: Boolean = false,
    val endReached: Boolean = false,
    val heard: ResolvedSummary? = null,
    val error: String? = null,
)
