package com.xenyaa.videoshot.core.paging

/**
 * 資料夾內容的 keyset 游標。
 *
 * 跟首頁的 [ShotCursor] 分開而不是共用：資料夾內的順序是「新加入在前」（`shot_folder.added_at`），
 * 首頁是拍攝日期（`shot.event_date`）。同一張圖在兩邊的位置不一樣，硬共用一個游標型別，
 * 只會讓「這個字串裡的第一個欄位到底是哪個時間」變成每次都要回頭查的事。
 */
data class FolderCursor(val addedAt: Long, val shotId: Long)

fun FolderCursor.encode(): String = "$addedAt|$shotId"

fun decodeFolderCursor(raw: String): FolderCursor? {
    val at = raw.lastIndexOf('|')
    if (at <= 0) return null
    val addedAt = raw.substring(0, at).toLongOrNull() ?: return null
    val shotId = raw.substring(at + 1).toLongOrNull() ?: return null
    return FolderCursor(addedAt, shotId)
}
