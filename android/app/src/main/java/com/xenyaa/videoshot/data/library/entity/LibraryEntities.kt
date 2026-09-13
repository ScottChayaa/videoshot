package com.xenyaa.videoshot.data.library.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 被取過圖的 YouTube 影片（不一定屬於使用者）。欄位對應規格第四節 library.db 的 video。
 * publishedAt 是 watch page 的 ISO 8601 原字串；addedAt 是 Unix 秒。
 */
@Entity(tableName = "video")
data class VideoEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "channel_title") val channelTitle: String,
    @ColumnInfo(name = "published_at") val publishedAt: String,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "privacy") val privacy: String,
    @ColumnInfo(name = "sb_spec") val sbSpec: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
