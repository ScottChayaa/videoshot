package com.xenyaa.videoshot.data.repo.model

/** 帳號頁三格統計（規格第九節）：總收藏張數、本月新增、去重後的來源影片數。 */
data class AccountStats(
    val totalShots: Int,
    val thisMonthShots: Int,
    val distinctVideos: Int,
)

/** 標籤管理頁的一列。`kind` 是原始字串（`tag.kind`），畫面自己用 `TagKind.byId` 轉圖示與文字。 */
data class TagUsage(
    val id: Long,
    val name: String,
    val kind: String,
    val aliases: List<String>,
    val shotCount: Int,
)
