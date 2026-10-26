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

/**
 * 地點與標籤最近一次被「使用」的時間（規格第四節 cache.db；首頁篩選抽屜的排序用）。
 * kind／ref_id 同統計表：1＝地點（place.id）、2＝標籤（tag.id）。
 * 不進備份：是使用習慣不是圖資，掉了只是抽屜順序退回張數排序。
 */
@Entity(tableName = "facet_recent", primaryKeys = ["kind", "ref_id"])
data class FacetRecentEntity(
    @ColumnInfo(name = "kind") val kind: Int,
    @ColumnInfo(name = "ref_id") val refId: Long,
    /** 最後一次使用的時間，Unix 秒 */
    @ColumnInfo(name = "used_at") val usedAt: Long,
)

/**
 * 剪輯（階段 19）進行中的草稿。**永遠只有一列**，寫法同 [DraftEntity]。
 * `payload` 是 `ClipDraftCodec` 編的 JSON（輸出方向、素材、時間軸與復原重做紀錄）；
 * 裡面引用手機媒體庫的素材編號，是這台裝置的狀態，所以放 cache.db、不進備份。
 */
@Entity(tableName = "edit_draft")
data class EditDraftEntity(
    /** 輸出方向的列舉名稱：'PORTRAIT' | 'LANDSCAPE'。草稿列要顯示方向時不必解整份 payload。 */
    @ColumnInfo(name = "orientation") val orientation: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: Int = ONLY_ROW

    companion object { const val ONLY_ROW = 1 }
}
