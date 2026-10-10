package com.xenyaa.videoshot.data

import com.xenyaa.videoshot.data.library.StatKind
import com.xenyaa.videoshot.data.repo.LibraryRepo

/**
 * 帳號頁的地點與標籤管理（改名、合併、刪除），寫完圖庫再同步最近使用時間（規格第九節）。
 * 先寫圖庫、成功了才動 cache.db——圖庫是唯一無法重建的那份；同步失敗只影響抽屜順序（[FacetUsage] 吞掉）。
 */
class FacetEdits(
    private val library: LibraryRepo,
    private val usage: FacetUsage,
) {
    /** 撞名＝合併（`renameTag` 回傳目標的編號）。 */
    suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>) {
        val kept = library.renameTag(id, name, kind, aliases)
        if (kept != id) usage.merged(StatKind.TAG, id, kept)
    }

    suspend fun mergeTag(fromId: Long, toId: Long) {
        library.mergeTag(fromId, toId)
        usage.merged(StatKind.TAG, fromId, toId)
    }

    suspend fun deleteTag(id: Long) {
        library.deleteTag(id)
        usage.deleted(StatKind.TAG, id)
    }

    /** 撞名＝合併（`renamePlace` 回傳目標的編號）。 */
    suspend fun renamePlace(id: Long, name: String, aliases: List<String>) {
        val kept = library.renamePlace(id, name, aliases)
        if (kept != id) usage.merged(StatKind.PLACE, id, kept)
    }

    suspend fun mergePlace(fromId: Long, toId: Long) {
        library.mergePlace(fromId, toId)
        usage.merged(StatKind.PLACE, fromId, toId)
    }

    suspend fun deletePlace(id: Long) {
        library.deletePlace(id)
        usage.deleted(StatKind.PLACE, id)
    }
}
