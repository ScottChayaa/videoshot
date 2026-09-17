package com.xenyaa.videoshot.core.home

/** 首頁的一個年月分組。 */
data class MonthGroup<T>(val month: String, val label: String, val items: List<T>)

/** `2026-03-14` → `2026-03`。 */
fun monthOf(eventDate: String): String = eventDate.take(7)

/** `2026-03` → `2026年3月`。個位數月份不補零（與原型的 `fmtMonthLabel` 一致）。 */
fun monthLabel(month: String): String {
    val year = month.take(4)
    val m = month.drop(5).toIntOrNull() ?: return month
    return "${year}年${m}月"
}

/**
 * 「只顯示某月以前」的 SQL 上界：**下個月的第一天**，條件寫成 `event_date < :bound`。
 *
 * 用「小於下月一號」而不是 `substr(event_date, 1, 7) <= :month`：後者對欄位做了函式運算，
 * `index_shot_event_date_id` 就用不上了，資料一多就變成全表掃描。
 */
fun nextMonthStart(month: String): String {
    val year = month.take(4).toIntOrNull() ?: return "9999-99-99"
    val m = month.drop(5).toIntOrNull() ?: return "9999-99-99"
    val nextYear = if (m == 12) year + 1 else year
    val nextMonth = if (m == 12) 1 else m + 1
    return "%04d-%02d-01".format(nextYear, nextMonth)
}

/**
 * 依月份切段。**只切相鄰的**（清單本來就由新到舊排好），
 * 不用 `groupBy` —— 那會把不相鄰的同月份合併，等於默默改變順序。
 */
fun <T> groupByMonth(items: List<T>, monthOf: (T) -> String): List<MonthGroup<T>> {
    val groups = mutableListOf<MonthGroup<T>>()
    for (item in items) {
        val month = monthOf(item)
        val last = groups.lastOrNull()
        if (last != null && last.month == month) {
            groups[groups.lastIndex] = last.copy(items = last.items + item)
        } else {
            groups += MonthGroup(month, monthLabel(month), listOf(item))
        }
    }
    return groups
}

/**
 * 首頁縮圖牆的欄數。手機固定三欄（手冊 §二），**同一次顯示中所有月份同欄數**
 * —— 欄數隨當月張數變的話，捲動時每個月的格子大小都不一樣。
 * 600dp 以上加欄（手冊 §零）。
 */
fun homeColumnsFor(widthDp: Int): Int = when {
    widthDp >= 900 -> 6
    widthDp >= 600 -> 5
    else -> 3
}
