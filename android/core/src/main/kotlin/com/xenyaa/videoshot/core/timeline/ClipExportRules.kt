package com.xenyaa.videoshot.core.timeline

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 輸出影片每分鐘的預估大小（bytes）：1080p H.264／AAC 的保守估計。 */
const val EXPORT_BYTES_PER_MINUTE = 75_000_000L

private const val MS_PER_MINUTE = 60_000L

private val fullDate = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val shortDate = DateTimeFormatter.ofPattern("MM/dd")

/**
 * 時間軸上還有段落的素材：依 `takenAtMs`（同時間依 `id`）由舊到新、不重複。
 * 一個素材分割成多段只算一次；段落全被刪光的素材不算。
 */
fun ClipDraft.usedSources(): List<ClipSource> {
    val used = timeline.segments.map { it.sourceId }.toSet()
    return sources
        .filter { it.id in used }
        .distinctBy { it.id }
        .sortedWith(compareBy<ClipSource> { it.takenAtMs }.thenBy { it.id })
}

/**
 * 導出的預設標題：只有日期「2026/10/20～10/27」（scott 2026-11-10 拿掉前面的 videoshot）；同一天「2026/10/18」；
 * 跨年兩邊都寫年份「2025/12/30～2026/01/02」；沒有素材只寫「videoshot」（標題不能空白）。
 * 日期取素材拍攝時間在 [zone] 的日曆日，`～` 是全形 U+FF5E。
 */
fun defaultClipTitle(sources: List<ClipSource>, zone: ZoneId): String {
    if (sources.isEmpty()) return "videoshot"
    val first = Instant.ofEpochMilli(sources.minOf { it.takenAtMs }).atZone(zone).toLocalDate()
    val last = Instant.ofEpochMilli(sources.maxOf { it.takenAtMs }).atZone(zone).toLocalDate()
    return when {
        first == last -> fullDate.format(first)
        first.year == last.year -> "${fullDate.format(first)}～${shortDate.format(last)}"
        else -> "${fullDate.format(first)}～${fullDate.format(last)}"
    }
}

/** 長度 [durationMs] 的輸出影片預估佔用的 bytes。 */
fun estimatedExportBytes(durationMs: Long): Long = durationMs * EXPORT_BYTES_PER_MINUTE / MS_PER_MINUTE

/** 開始輸出前至少要有的剩餘空間：預估大小的兩倍。 */
fun requiredFreeBytes(durationMs: Long): Long = 2 * estimatedExportBytes(durationMs)
