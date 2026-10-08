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
 * `query`／`expanded` 的 key：true＝地點區、false＝標籤區。
 */
data class FilterDraft(
    val places: Set<String>,
    val tags: Set<String>,
    val query: Map<Boolean, String> = mapOf(true to "", false to ""),
    val expanded: Set<Boolean> = emptySet(),
) {
    fun isSelected(option: FilterOption) = if (option.isPlace) option.name in places else option.name in tags
}

data class FilterSection(val shown: List<FilterOption>, val hiddenCount: Int)

/** 抽屜每一區實際要畫哪些小膠囊（純函式，畫面與測試共用）。 */
object FilterLists {
    /** 未展開、沒有搜尋字時每區最多畫幾個——上千顆小膠囊一次畫出來會讓抽屜開啟卡頓。 */
    const val COLLAPSED_LIMIT = 50

    fun visible(options: List<FilterOption>, isPlace: Boolean, draft: FilterDraft): FilterSection {
        val pool = options.filter { it.isPlace == isPlace }
        val selected = if (isPlace) draft.places else draft.tags
        val missing = (selected - pool.map { it.name }.toSet()).sorted()
            .map { FilterOption(it, isPlace, "other", emptyList()) }
        val query = draft.query[isPlace].orEmpty().trim()
        if (query.isNotEmpty()) {
            val hit = (missing + pool).filter { o ->
                o.name.contains(query, ignoreCase = true) || o.aliases.any { it.contains(query, ignoreCase = true) }
            }
            return FilterSection(hit, 0)
        }
        val all = missing + pool
        if (isPlace in draft.expanded || all.size <= COLLAPSED_LIMIT) return FilterSection(all, 0)
        return FilterSection(all.take(COLLAPSED_LIMIT), all.size - COLLAPSED_LIMIT)
    }
}

/** 首頁月份小膠囊點下去＝只篩這一個（[HomeViewModel.applySingle]）。別名在這裡用不到，留空。 */
fun MonthFacet.toFilterOption(): FilterOption = FilterOption(name, isPlace, tagKind, emptyList())
