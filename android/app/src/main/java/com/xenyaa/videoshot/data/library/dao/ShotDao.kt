package com.xenyaa.videoshot.data.library.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.ShotImageEntity

@Dao
interface ShotDao {
    @Insert suspend fun insert(shot: ShotEntity): Long

    @Query("SELECT COUNT(*) FROM shot WHERE video_id = :videoId")
    suspend fun countOfVideo(videoId: String): Int

    @Query("DELETE FROM shot WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Upsert suspend fun putImage(image: ShotImageEntity)

    @Query("SELECT * FROM shot_image WHERE shot_id = :shotId")
    suspend fun imageOf(shotId: Long): ShotImageEntity?

    @Query("UPDATE shot SET description = :description, place = :place WHERE id = :id")
    suspend fun updateDescriptionAndPlace(id: Long, description: String?, place: String?)

    @Query("UPDATE shot SET event_date = :eventDate WHERE id = :id")
    suspend fun updateEventDate(id: Long, eventDate: String)

    @Query("UPDATE shot SET place = :place WHERE id = :id")
    suspend fun updatePlace(id: Long, place: String?)

    @Query("UPDATE shot SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String?)

    @Query("DELETE FROM shot WHERE video_id = :videoId")
    suspend fun deleteOfVideo(videoId: String)

    /**
     * 首頁時間軸的第一頁。由新到舊：event_date 遞減，同一天內 id 遞減。走 index_shot_event_date_id。
     *
     * @param before 時間篩選的上界（下個月的第一天）。沒有篩選時呼叫端給 `9999-99-99`。
     *        **篩選一定要下推到這裡** —— 撈回來再過濾的話，被濾掉的那幾筆仍然佔著 limit 的名額
     */
    @Query(
        """
        SELECT id, video_id, at_sec, source, frame_index, sb_level, event_date, place, description
        FROM shot WHERE event_date < :before
        ORDER BY event_date DESC, id DESC LIMIT :limit
        """
    )
    suspend fun feedFirst(before: String, limit: Int): List<ShotRowProjection>

    /** 接續頁。條件是標準的 keyset 比較：(event_date, id) 嚴格小於游標。 */
    @Query(
        """
        SELECT id, video_id, at_sec, source, frame_index, sb_level, event_date, place, description
        FROM shot
        WHERE event_date < :before
          AND (event_date < :eventDate OR (event_date = :eventDate AND id < :id))
        ORDER BY event_date DESC, id DESC LIMIT :limit
        """
    )
    suspend fun feedAfter(before: String, eventDate: String, id: Long, limit: Int): List<ShotRowProjection>

    /** Lightbox 的「共 M 張」。跟著同一個篩選條件走，否則 N 會大於 M。 */
    @Query("SELECT COUNT(*) FROM shot WHERE event_date < :before")
    suspend fun countBefore(before: String): Int

    /**
     * 某個月出現過的地點與標籤，附張數（規格第六節「每月的標籤列，GROUP BY 即時算」）。
     * 地點與標籤混在同一張清單裡，由 kind 區分。
     */
    @Query(
        """
        SELECT name, kind, COUNT(*) AS cnt FROM (
            SELECT place AS name, 'place' AS kind
            FROM shot WHERE substr(event_date, 1, 7) = :month AND place IS NOT NULL
            UNION ALL
            SELECT t.name AS name, 'tag' AS kind
            FROM shot_tag st
            JOIN tag t ON t.id = st.tag_id
            JOIN shot s ON s.id = st.shot_id
            WHERE substr(s.event_date, 1, 7) = :month
        )
        GROUP BY name, kind ORDER BY cnt DESC, name
        """
    )
    suspend fun monthFacets(month: String): List<MonthFacetProjection>

    @Query(
        """
        SELECT substr(event_date, 1, 7) AS month, COUNT(*) AS count
        FROM shot GROUP BY month ORDER BY month DESC
        """
    )
    suspend fun monthCounts(): List<MonthCountProjection>

    @Query(
        """
        SELECT id, video_id, at_sec, source, frame_index, sb_level, event_date, place, description
        FROM shot WHERE video_id = :videoId ORDER BY at_sec
        """
    )
    suspend fun ofVideo(videoId: String): List<ShotRowProjection>

    @Query(
        """
        SELECT id, video_id, at_sec, source, frame_index, sb_level, event_date, place, description
        FROM shot WHERE id = :id
        """
    )
    suspend fun rowById(id: Long): ShotRowProjection?

    /** 抽屜的既有地點建議（規格第五節欄位表）。空字串在寫入時已轉成 null，這裡只要排除 null。 */
    @Query("SELECT DISTINCT place FROM shot WHERE place IS NOT NULL ORDER BY place")
    suspend fun distinctPlaces(): List<String>
}

/** Room 直接映射查詢結果用；欄位名對應 SQL 的輸出欄位。 */
data class ShotRowProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "at_sec") val atSec: Double,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "frame_index") val frameIndex: Int?,
    @ColumnInfo(name = "sb_level") val sbLevel: Int?,
    @ColumnInfo(name = "event_date") val eventDate: String,
    @ColumnInfo(name = "place") val place: String?,
    @ColumnInfo(name = "description") val description: String?,
)

data class MonthCountProjection(
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "count") val count: Int,
)

data class MonthFacetProjection(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "cnt") val count: Int,
)
