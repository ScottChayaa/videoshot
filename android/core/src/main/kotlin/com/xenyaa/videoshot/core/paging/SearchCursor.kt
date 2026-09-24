package com.xenyaa.videoshot.core.paging

/**
 * 文字查詢結果的 keyset 游標（規格第八節）。相關度（地點 > 標籤 > 關鍵字）是排序的第一鍵，
 * 所以比 [ShotCursor] 多一個 `relevance` 分量——只比 `(event_date, id)` 排不出
 * 「這批裡地點命中的都排在關鍵字命中前面」這件事。
 *
 * 「標籤與地點」模式（多選 chip，OR 篩選）不用這個游標，直接沿用 [ShotCursor]——
 * 那個模式的命中都是使用者自己勾出來的條件，不需要分相關度（見 Task 4 的 KDoc）。
 */
data class SearchCursor(val relevance: Int, val eventDate: String, val id: Long)

fun SearchCursor.encode(): String = "$relevance|$eventDate|$id"

fun decodeSearchCursor(raw: String): SearchCursor? {
    val parts = raw.split("|")
    if (parts.size != 3) return null
    val relevance = parts[0].toIntOrNull() ?: return null
    val id = parts[2].toLongOrNull() ?: return null
    return SearchCursor(relevance, parts[1], id)
}
