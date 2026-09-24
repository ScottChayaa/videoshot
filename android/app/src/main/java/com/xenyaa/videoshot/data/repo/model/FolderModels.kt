package com.xenyaa.videoshot.data.repo.model

import com.xenyaa.videoshot.core.folders.SortableFolder
import com.xenyaa.videoshot.core.paging.FolderCursor

/**
 * 清單頁與資料夾頁上半共用的一張卡片。
 *
 * @param shotCount **含子孫層**的張數（規格第六節）。同一張圖同時在父與子只算一次
 * @param lastActivityAt 含子孫層最近一次有圖被加進來的時間（秒）；一張圖都沒有時退回建立時間
 * @param preview 預覽拼貼的圖，最多 4 張，最近加入在前。**不含 webp**，一律經 `thumbFor(shot)` 取圖
 */
data class FolderCard(
    val id: Long,
    override val name: String,
    override val shotCount: Int,
    override val lastActivityAt: Long,
    val preview: List<ShotRow>,
) : SortableFolder

/**
 * 樹上的一個資料夾。
 *
 * @param depth 根層是 1（規格的「深度上限 5 層」從 1 數起，跟 `createFolder` 的檢查同一個基準）
 */
data class FolderNode(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val depth: Int,
)

/**
 * 資料夾內容的一頁。
 *
 * 不共用首頁的 [Page]：那個的游標型別是 `ShotCursor`（拍攝日期），資料夾內排的是加入時間。
 */
data class FolderPage(val items: List<ShotRow>, val next: FolderCursor?)
