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

    /**
     * 查詢頁「標籤與地點」模式的候選 chip(規格第六節「查詢」):地點與標籤混在同一份清單,依張數排序。
     * 跟 [monthFacets] 同一個 UNION ALL 寫法,只是條件從「單一月份」換成「這個時間以前」——查詢頁的
     * 時間篩選跟首頁一樣是「N 年 N 月以前」的上界,不是單一個月。
     *
     * `limit` 由呼叫端傳「想要的張數 + 1」,藉此判斷「顯示更多」(規格:top-30 + 顯示更多)
     * ——不在這裡寫死 30,SQL 只管給多一筆讓呼叫端自己判斷,跟 `FolderDao.previewsIn` 的
     * `rn <= 4` 是同一種「多要一筆探測邊界」手法但更簡單(這裡連 window function 都不必)。
     */
    @Query(
        """
        SELECT name, kind, COUNT(*) AS cnt FROM (
            SELECT place AS name, 'place' AS kind
            FROM shot WHERE event_date < :before AND place IS NOT NULL
            UNION ALL
            SELECT t.name AS name, 'tag' AS kind
            FROM shot_tag st
            JOIN tag t ON t.id = st.tag_id
            JOIN shot s ON s.id = st.shot_id
            WHERE s.event_date < :before
        )
        GROUP BY name, kind ORDER BY cnt DESC, name
        LIMIT :limit
        """
    )
    suspend fun facetsInRange(before: String, limit: Int): List<MonthFacetProjection>

    /**
     * 查詢頁「標籤與地點」模式的第一頁。任一個地點或標籤命中即算(OR)，排序同首頁
     * （`event_date DESC, id DESC`）——這個模式不分相關度，理由見 `LibraryRepo.searchByFacets` 的 KDoc。
     *
     * `LEFT JOIN shot_tag` 對有多個標籤的圖會展開成多列；`DISTINCT` 收斂回一列——
     * SELECT 出來的欄位全部來自 `s.*`，同一張圖不管在哪個分支命中都是同一組值，收斂得乾淨。
     */
    @Query(
        """
        SELECT DISTINCT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level,
               s.event_date, s.place, s.description
        FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
        WHERE s.event_date < :before AND (s.place IN (:places) OR st.tag_id IN (:tagIds))
        ORDER BY s.event_date DESC, s.id DESC LIMIT :limit
        """
    )
    suspend fun facetSearchFirst(before: String, places: List<String>, tagIds: List<Long>, limit: Int): List<ShotRowProjection>

    /** 接續頁。keyset 比對跟首頁的 `feedAfter` 同一個寫法。 */
    @Query(
        """
        SELECT DISTINCT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level,
               s.event_date, s.place, s.description
        FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
        WHERE s.event_date < :before AND (s.place IN (:places) OR st.tag_id IN (:tagIds))
          AND (s.event_date < :eventDate OR (s.event_date = :eventDate AND s.id < :id))
        ORDER BY s.event_date DESC, s.id DESC LIMIT :limit
        """
    )
    suspend fun facetSearchAfter(
        before: String,
        places: List<String>,
        tagIds: List<Long>,
        eventDate: String,
        id: Long,
        limit: Int,
    ): List<ShotRowProjection>

    /** 結果列的「N 張」（規格第六節：「結果列顯示『N 張・全部日期・條件 chips』」）。 */
    @Query(
        """
        SELECT COUNT(DISTINCT s.id) FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
        WHERE s.event_date < :before AND (s.place IN (:places) OR st.tag_id IN (:tagIds))
        """
    )
    suspend fun facetSearchCount(before: String, places: List<String>, tagIds: List<Long>): Int

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

    /**
     * 帳號頁三格統計（規格第九節）。`distinctVideos` 直接數 `video` 表的列數，
     * 不對 `shot.video_id` 做 `COUNT(DISTINCT ...)`——`deleteShot` 刪掉一支影片最後一張時
     * 會連帶刪掉 `video` 列（規格第六節），兩種算法永遠同值，前者不必掃過整張 shot 表。
     */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM shot) AS totalShots,
          (SELECT COUNT(*) FROM shot WHERE substr(event_date, 1, 7) = :thisMonth) AS thisMonthShots,
          (SELECT COUNT(*) FROM video) AS distinctVideos
        """
    )
    suspend fun accountStats(thisMonth: String): AccountStatsProjection
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

data class AccountStatsProjection(
    @ColumnInfo(name = "totalShots") val totalShots: Int,
    @ColumnInfo(name = "thisMonthShots") val thisMonthShots: Int,
    @ColumnInfo(name = "distinctVideos") val distinctVideos: Int,
)
