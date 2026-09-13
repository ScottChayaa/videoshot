package com.xenyaa.videoshot.core.paging

/**
 * 首頁與資料夾頁的 keyset 分頁游標。用 (event_date, id) 這組複合鍵而不是 OFFSET ——
 * OFFSET 在使用者往下捲時會隨著新增／刪除而跳號或漏資料。
 */
data class ShotCursor(val eventDate: String, val id: Long)

fun ShotCursor.encode(): String = "$eventDate|$id"

fun decodeShotCursor(raw: String): ShotCursor? {
    val at = raw.lastIndexOf('|')
    if (at <= 0) return null
    val id = raw.substring(at + 1).toLongOrNull() ?: return null
    return ShotCursor(raw.substring(0, at), id)
}
