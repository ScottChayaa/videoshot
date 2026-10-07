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

    @Query("UPDATE shot SET event_date = :eventDate WHERE id = :id")
    suspend fun updateEventDate(id: Long, eventDate: String)

    @Query("UPDATE shot SET place_id = :placeId WHERE id = :id")
    suspend fun updatePlace(id: Long, placeId: Long?)

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
        SELECT $SHOT_ROW_COLUMNS
        FROM shot s $JOIN_PLACE
        WHERE s.event_date < :before
        ORDER BY s.event_date DESC, s.id DESC LIMIT :limit
        """
    )
    suspend fun feedFirst(before: String, limit: Int): List<ShotRowProjection>

    /** 接續頁。條件是標準的 keyset 比較：(event_date, id) 嚴格小於游標。 */
    @Query(
        """
        SELECT $SHOT_ROW_COLUMNS
        FROM shot s $JOIN_PLACE
        WHERE s.event_date < :before
          AND (s.event_date < :eventDate OR (s.event_date = :eventDate AND s.id < :id))
        ORDER BY s.event_date DESC, s.id DESC LIMIT :limit
        """
    )
    suspend fun feedAfter(before: String, eventDate: String, id: Long, limit: Int): List<ShotRowProjection>

    /**
     * 查詢頁「標籤與地點」模式的第一頁。任一個地點或標籤命中即算(OR)，排序同首頁
     * （`event_date DESC, id DESC`）——這個模式不分相關度，理由見 `LibraryRepo.searchByFacets` 的 KDoc。
     *
     * `LEFT JOIN shot_tag` 對有多個標籤的圖會展開成多列；`DISTINCT` 收斂回一列——
     * SELECT 出來的欄位全部來自 `s.*`，同一張圖不管在哪個分支命中都是同一組值，收斂得乾淨。
     */
    @Query(
        """
        SELECT DISTINCT $SHOT_ROW_COLUMNS
        FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id $JOIN_PLACE
        WHERE s.event_date < :before AND (s.place_id IN (:placeIds) OR st.tag_id IN (:tagIds))
        ORDER BY s.event_date DESC, s.id DESC LIMIT :limit
        """
    )
    suspend fun facetSearchFirst(before: String, placeIds: List<Long>, tagIds: List<Long>, limit: Int): List<ShotRowProjection>

    /** 接續頁。keyset 比對跟首頁的 `feedAfter` 同一個寫法。 */
    @Query(
        """
        SELECT DISTINCT $SHOT_ROW_COLUMNS
        FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id $JOIN_PLACE
        WHERE s.event_date < :before AND (s.place_id IN (:placeIds) OR st.tag_id IN (:tagIds))
          AND (s.event_date < :eventDate OR (s.event_date = :eventDate AND s.id < :id))
        ORDER BY s.event_date DESC, s.id DESC LIMIT :limit
        """
    )
    suspend fun facetSearchAfter(
        before: String,
        placeIds: List<Long>,
        tagIds: List<Long>,
        eventDate: String,
        id: Long,
        limit: Int,
    ): List<ShotRowProjection>

    /**
     * 查詢頁「標籤與地點」的**分段合併**查法（規格第八節，設計決議 4）：地點走 `index_shot_place_id`、
     * 標籤走 `index_shot_tag_tag_id`，各自撈出符合的圖再合併排序。選取的項目張數少時遠快於沿時間軸掃描
     * （實機：1,266 張的標籤 0.12 秒 → 0.015 秒）；張數多時反而慢，由 repo 依 `StatsDao.selectedTotal` 決定用哪個。
     * `UNION`（不是 UNION ALL）讓同時命中地點與標籤的圖只出現一次。keyset 條件寫進兩個分支，第二頁以後同樣只撈需要的。
     * 第一頁傳 `eventDate = "9999-99-99"`、`id = Long.MAX_VALUE`。
     */
    @Query(
        """
        SELECT u.id AS id, u.video_id AS video_id, u.at_sec AS at_sec, u.source AS source,
               u.frame_index AS frame_index, u.sb_level AS sb_level, u.event_date AS event_date,
               p.name AS place, u.description AS description
        FROM (
            SELECT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level, s.event_date, s.place_id, s.description
            FROM shot s
            WHERE s.place_id IN (:placeIds) AND s.event_date < :before
              AND (s.event_date < :eventDate OR (s.event_date = :eventDate AND s.id < :id))
            UNION
            SELECT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level, s.event_date, s.place_id, s.description
            FROM shot_tag st JOIN shot s ON s.id = st.shot_id
            WHERE st.tag_id IN (:tagIds) AND s.event_date < :before
              AND (s.event_date < :eventDate OR (s.event_date = :eventDate AND s.id < :id))
        ) u LEFT JOIN place p ON p.id = u.place_id
        ORDER BY u.event_date DESC, u.id DESC LIMIT :limit
        """
    )
    suspend fun facetSearchUnion(
        before: String,
        placeIds: List<Long>,
        tagIds: List<Long>,
        eventDate: String,
        id: Long,
        limit: Int,
    ): List<ShotRowProjection>

    /** 結果列的「N 張」，最多數到 `cap`（呼叫端傳 `RESULT_COUNT_CAP + 1`；設計決議 2）。 */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT s.id FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
            WHERE s.event_date < :before AND (s.place_id IN (:placeIds) OR st.tag_id IN (:tagIds))
            LIMIT :cap
        )
        """
    )
    suspend fun facetSearchCount(before: String, placeIds: List<Long>, tagIds: List<Long>, cap: Int): Int

    @Query(
        """
        SELECT $SHOT_ROW_COLUMNS FROM shot s $JOIN_PLACE WHERE s.video_id = :videoId ORDER BY s.at_sec
        """
    )
    suspend fun ofVideo(videoId: String): List<ShotRowProjection>

    @Query(
        """
        SELECT $SHOT_ROW_COLUMNS FROM shot s $JOIN_PLACE WHERE s.id = :id
        """
    )
    suspend fun rowById(id: Long): ShotRowProjection?

    /**
     * 回填掃描用：全部 storyboard 來源的 shot（規格第十一節步驟 1）。
     * 不分頁——一次全部撈出來跟 `thumbs/` 底下的檔案比對，10,000 張量級可以接受
     * （附錄 A-8：全部縮圖約 60 MB，`shot` 表本身的列更小）。
     */
    @Query(
        """
        SELECT $SHOT_ROW_COLUMNS FROM shot s $JOIN_PLACE WHERE s.source = 'storyboard'
        """
    )
    suspend fun allStoryboardShots(): List<ShotRowProjection>
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
    /** 標籤本身的 `tag.kind`（人物／動物／主題／其他）；地點列固定 `'other'`，畫面端以 [kind] 先判斷地點。 */
    @ColumnInfo(name = "tag_kind") val tagKind: String,
    @ColumnInfo(name = "cnt") val count: Int,
)
