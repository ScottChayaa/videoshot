package com.xenyaa.videoshot.data.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * storyboard 縮圖在**這台裝置**上的狀態（規格第四節 cache.db）。
 * 之所以不放 library.db：縮圖在不在是每台裝置各自不同的事實，跟著備份過去會騙到新裝置。
 */
@Entity(tableName = "thumb_state", primaryKeys = ["video_id", "sb_level", "frame_index"])
data class ThumbStateEntity(
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "sb_level") val sbLevel: Int,
    @ColumnInfo(name = "frame_index") val frameIndex: Int,
    /** 'missing'（待回填）| 'ok' | 'lost'（無法取回） */
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "attempts") val attempts: Int,
    /** 退避用的下次重試時間，Unix 秒 */
    @ColumnInfo(name = "next_try_at") val nextTryAt: Long,
    /** 'video_unavailable' | 'no_storyboard' | 'retries_exhausted'；state 不是 lost 時為 null */
    @ColumnInfo(name = "lost_reason") val lostReason: String?,
)

/**
 * 取圖精靈草稿。**永遠只有一列**（最近一支影片）—— 用固定主鍵達成，
 * 存新的就覆蓋舊的，不必先刪再插。
 */
@Entity(tableName = "draft")
data class DraftEntity(
    @ColumnInfo(name = "video_id") val videoId: String,
    /** 1 | 2 | 3 */
    @ColumnInfo(name = "step") val step: Int,
    /** JSON：已勾選的 frame_index、手動補圖清單、第三步已套用的圖資（不存 dHash 指紋，見 `DraftPayload` 的註解） */
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: Int = ONLY_ROW

    companion object { const val ONLY_ROW = 1 }
}
