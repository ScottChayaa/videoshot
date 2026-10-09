package com.xenyaa.videoshot.ui.home

import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.data.repo.model.MonthFacet

/** 已套用到首頁的篩選（階段 17）：地點與標籤名稱，**任一個符合**即顯示。空＝沒有篩選。 */
data class HomeFilter(val places: Set<String> = emptySet(), val tags: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = places.isEmpty() && tags.isEmpty()
    val size: Int get() = places.size + tags.size
}

/**
 * 篩選抽屜開著時的草稿：按【套用】才變成 [HomeFilter]，關掉抽屜就丟掉（設計決議 3）。
 * `query` 的 key：true＝地點區、false＝標籤區。
 */
data class FilterDraft(
    val places: Set<String>,
    val tags: Set<String>,
    val query: Map<Boolean, String> = mapOf(true to "", false to ""),
) {
    fun isSelected(option: FilterOption) = if (option.isPlace) option.name in places else option.name in tags
}

/** @param hiddenCount 超過 [FilterLists.MAX_SHOWN] 沒列出來的個數（已勾選的不算，它們一定列出） */
data class FilterSection(val shown: List<FilterOption>, val hiddenCount: Int)

/** 抽屜每一區實際要畫哪些小膠囊（純函式，畫面與測試共用）。 */
object FilterLists {
    /**
     * 每區最多列幾個（有沒有搜尋字都一樣），其餘靠搜尋框找。上限是為了抽屜的分頁計算與記憶體：
     * 候選可能上千個，分頁要先量過每顆的寬度。
     */
    const val MAX_SHOWN = 100

    fun visible(options: List<FilterOption>, isPlace: Boolean, draft: FilterDraft): FilterSection {
        val pool = options.filter { it.isPlace == isPlace }
        val selected = if (isPlace) draft.places else draft.tags
        val missing = (selected - pool.map { it.name }.toSet()).sorted()
            .map { FilterOption(it, isPlace, "other", emptyList()) }
        val query = draft.query[isPlace].orEmpty().trim()
        val all = if (query.isEmpty()) {
            missing + pool
        } else {
            (missing + pool).filter { o ->
                o.name.contains(query, ignoreCase = true) || o.aliases.any { it.contains(query, ignoreCase = true) }
            }
        }
        if (all.size <= MAX_SHOWN) return FilterSection(all, 0)
        // 已勾選的項目不能因為超過上限就看不到（使用者看不到自己選了什麼）：
        // 前 100 個照列，超出的已勾選項目依原順序接在後面，隱藏數只算還沒勾選的
        val tail = all.drop(MAX_SHOWN)
        val tailSelected = tail.filter { draft.isSelected(it) }
        return FilterSection(all.take(MAX_SHOWN) + tailSelected, tail.size - tailSelected.size)
    }
}

/** 首頁月份小膠囊點下去＝只篩這一個（[HomeViewModel.applySingle]）。別名在這裡用不到，留空。 */
fun MonthFacet.toFilterOption(): FilterOption = FilterOption(name, isPlace, tagKind, emptyList())

/**
 * 篩選抽屜候選的讀取狀態。讀取中不能說「這段時間沒有地點」（其實是還沒讀到）；
 * 失敗只標在抽屜上、抽屜自己有【重試】，不變成首頁的錯誤列。
 */
enum class FilterOptionsStatus { LOADING, READY, FAILED }

/**
 * 篩選抽屜的小膠囊分頁（純函式）：依寬度由左到右排成列，排不下就換列，每 [rowsPerPage] 列一頁。
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
