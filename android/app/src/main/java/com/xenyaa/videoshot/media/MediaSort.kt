package com.xenyaa.videoshot.media

/**
 * 素材的「拍攝時間」（毫秒）。
 *
 * MediaStore 的 `DATE_TAKEN` 常常是 null 或 0（截圖、下載來的檔案、部分 App 存的檔），
 * 這時退回 `DATE_MODIFIED`；後者單位是**秒**，要乘 1000 才跟拍攝時間同單位。
 */
fun takenAtOf(dateTakenMs: Long?, dateModifiedSec: Long): Long =
    if (dateTakenMs == null || dateTakenMs <= 0L) dateModifiedSec * 1000L else dateTakenMs

/**
 * 由新到舊排序；拍攝時間相同時依 [MediaItem.id] 由大到小。
 *
 * 連拍或批次匯入常有同一毫秒的素材，補上 `id` 當第二鍵，
 * 重新整理後的順序才不會跳動。
 */
fun sortNewestFirst(items: List<MediaItem>): List<MediaItem> =
    items.sortedWith(
        compareByDescending<MediaItem> { it.takenAtMs }.thenByDescending { it.id },
    )
