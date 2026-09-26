package com.xenyaa.videoshot.data.repo.model

/** 帳號頁三格統計（規格第九節）：總收藏張數、本月新增、去重後的來源影片數。 */
data class AccountStats(
    val totalShots: Int,
    val thisMonthShots: Int,
    val distinctVideos: Int,
)
