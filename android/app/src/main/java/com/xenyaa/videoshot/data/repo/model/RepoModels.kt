package com.xenyaa.videoshot.data.repo.model

import com.xenyaa.videoshot.core.paging.ShotCursor

/**
 * 畫面看到的一張圖。**刻意不含 webp** —— 首頁一次撈 50 筆，
 * 帶上 6 KB 的 BLOB 會把清單拖垮；圖一律經 thumbs 模組的 thumbFor(shot) 取得。
 */
data class ShotRow(
    val id: Long,
    val videoId: String,
    val atSec: Double,
    val source: String,
    val frameIndex: Int?,
    val sbLevel: Int?,
    val eventDate: String,
    val place: String?,
    val description: String?,
)

data class MonthCount(val month: String, val count: Int)

data class Page<T>(val items: List<T>, val next: ShotCursor?)
