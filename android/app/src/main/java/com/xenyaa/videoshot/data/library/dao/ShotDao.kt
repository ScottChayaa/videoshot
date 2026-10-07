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

    /** Lightbox 的「共 M 張」。跟著同一個篩選條件走，否則 N 會大於 M。 */
    @Query("SELECT COUNT(*) FROM shot WHERE event_date < :before")
    suspend fun countBefore(before: String): Int

    /**
     * 某個月出現過的地點與標籤，附張數（規格第六節「每月的標籤列，GROUP BY 即時算」）。
     * 地點與標籤混在同一張清單裡，由 kind 區分。
     */
    @Query(
        """
        SELECT name, kind, tag_kind, COUNT(*) AS cnt FROM (
            SELECT p.name AS name, 'place' AS kind, 'other' AS tag_kind
            FROM shot s JOIN place p ON p.id = s.place_id
            WHERE substr(s.event_date, 1, 7) = :month
            UNION ALL
            SELECT t.name AS name, 'tag' AS kind, t.kind AS tag_kind
            FROM shot_tag st
            JOIN tag t ON t.id = st.tag_id
            JOIN shot s ON s.id = st.shot_id
            WHERE substr(s.event_date, 1, 7) = :month
        )
        GROUP BY name, kind, tag_kind ORDER BY cnt DESC, name
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
        SELECT name, kind, tag_kind, COUNT(*) AS cnt FROM (
            SELECT p.name AS name, 'place' AS kind, 'other' AS tag_kind
            FROM shot s JOIN place p ON p.id = s.place_id
            WHERE s.event_date < :before
            UNION ALL
            SELECT t.name AS name, 'tag' AS kind, t.kind AS tag_kind
            FROM shot_tag st
            JOIN tag t ON t.id = st.tag_id
            JOIN shot s ON s.id = st.shot_id
            WHERE s.event_date < :before
        )
        GROUP BY name, kind, tag_kind ORDER BY cnt DESC, name
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

    /** 結果列的「N 張」（規格第六節：「結果列顯示『N 張・全部日期・條件 chips』」）。 */
    @Query(
        """
        SELECT COUNT(DISTINCT s.id) FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
        WHERE s.event_date < :before AND (s.place_id IN (:placeIds) OR st.tag_id IN (:tagIds))
        """
    )
    suspend fun facetSearchCount(before: String, placeIds: List<Long>, tagIds: List<Long>): Int

    @Query(
        """
        SELECT substr(event_date, 1, 7) AS month, COUNT(*) AS count
        FROM shot GROUP BY month ORDER BY month DESC
        """
    )
    suspend fun monthCounts(): List<MonthCountProjection>

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
    /** 標籤本身的 `tag.kind`（人物／動物／主題／其他）；地點列固定 `'other'`，畫面端以 [kind] 先判斷地點。 */
    @ColumnInfo(name = "tag_kind") val tagKind: String,
    @ColumnInfo(name = "cnt") val count: Int,
)

data class AccountStatsProjection(
    @ColumnInfo(name = "totalShots") val totalShots: Int,
    @ColumnInfo(name = "thisMonthShots") val thisMonthShots: Int,
    @ColumnInfo(name = "distinctVideos") val distinctVideos: Int,
)
