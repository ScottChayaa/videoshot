package com.xenyaa.videoshot.data.library.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification

@Dao
interface SearchDao {
    /**
     * ≥ 3 個字元的關鍵字走 FTS5（規格第八節）。
     * shot_fts 是 callback 用原生 SQL 建的虛擬表，Room 的編譯期驗證看不到它，只能跳過這一個方法的驗證。
     */
    @SkipQueryVerification
    @Query("SELECT rowid FROM shot_fts WHERE shot_fts MATCH :query ORDER BY rowid")
    suspend fun matchIds(query: String): List<Long>

    /** < 3 個字元的關鍵字 trigram 一律落空，退回 LIKE。只比對描述；地點名稱由 `PlaceDao.idsNameContains` 另外比對（規格第八節）。 */
    @Query("SELECT id FROM shot WHERE description LIKE '%' || :keyword || '%' ORDER BY id")
    suspend fun likeIds(keyword: String): List<Long>

    /**
     * 文字查詢的第一頁。relevance 分層(規格第八節)：地點命中 2 分、標籤命中 1 分、
     * 只有關鍵字命中 0 分，同一張圖多種命中取最高分——`MAX(CASE...)` 就是在做這件事。
     * `keywordIds` 是呼叫端先用 [matchIds]／[likeIds] 併出來的候選(見 `RoomLibraryRepo.searchByQuery`)。
     *
     * 這個查詢是階段 2 建好的 [matchIds]／[likeIds] 第一個真正的消費者——它們原本已經測過、
     * 但沒有任何 repo 方法呼叫(死碼)，這裡把它們接上。
     */
    @Query(
        """
        WITH matches(id, relevance) AS (
            SELECT s.id AS id, MAX(
                CASE
                    WHEN s.place_id IN (:placeIds) THEN 2
                    WHEN st.tag_id IN (:tagIds) THEN 1
                    WHEN s.id IN (:keywordIds) THEN 0
                    ELSE -1
                END
            ) AS relevance
            FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
            WHERE s.event_date >= :since AND s.event_date <= :until AND s.event_date < :upToMonthBound
              AND (s.place_id IN (:placeIds) OR st.tag_id IN (:tagIds) OR s.id IN (:keywordIds))
            GROUP BY s.id
        )
        SELECT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level,
               s.event_date, p.name AS place, s.description, m.relevance
        FROM matches m JOIN shot s ON s.id = m.id $JOIN_PLACE
        ORDER BY m.relevance DESC, s.event_date DESC, s.id DESC
        LIMIT :limit
        """
    )
    suspend fun queryFirst(
        since: String,
        until: String,
        upToMonthBound: String,
        placeIds: List<Long>,
        tagIds: List<Long>,
        keywordIds: List<Long>,
        limit: Int,
    ): List<SearchHitProjection>

    /**
     * 接續頁。游標比對用 SQLite 的 row value(`(a,b,c) < (x,y,z)`，3.15 起支援，
     * `BundledSQLiteDriver` 是 3.50.1)——跟 `relevance DESC, event_date DESC, id DESC`
     * 這個排序方向完全對應，不必再手寫三段 `OR` 展開。
     */
    @Query(
        """
        WITH matches(id, relevance) AS (
            SELECT s.id AS id, MAX(
                CASE
                    WHEN s.place_id IN (:placeIds) THEN 2
                    WHEN st.tag_id IN (:tagIds) THEN 1
                    WHEN s.id IN (:keywordIds) THEN 0
                    ELSE -1
                END
            ) AS relevance
            FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
            WHERE s.event_date >= :since AND s.event_date <= :until AND s.event_date < :upToMonthBound
              AND (s.place_id IN (:placeIds) OR st.tag_id IN (:tagIds) OR s.id IN (:keywordIds))
            GROUP BY s.id
        )
        SELECT s.id, s.video_id, s.at_sec, s.source, s.frame_index, s.sb_level,
               s.event_date, p.name AS place, s.description, m.relevance
        FROM matches m JOIN shot s ON s.id = m.id $JOIN_PLACE
        WHERE (m.relevance, s.event_date, s.id) < (:curRelevance, :curEventDate, :curId)
        ORDER BY m.relevance DESC, s.event_date DESC, s.id DESC
        LIMIT :limit
        """
    )
    suspend fun queryAfter(
        since: String,
        until: String,
        upToMonthBound: String,
        placeIds: List<Long>,
        tagIds: List<Long>,
        keywordIds: List<Long>,
        curRelevance: Int,
        curEventDate: String,
        curId: Long,
        limit: Int,
    ): List<SearchHitProjection>

    /** 結果列的「N 張」，最多數到 `cap`（呼叫端傳 `RESULT_COUNT_CAP + 1`）。 */
    @Query(
        """
        WITH matches(id) AS (
            SELECT s.id FROM shot s LEFT JOIN shot_tag st ON st.shot_id = s.id
            WHERE s.event_date >= :since AND s.event_date <= :until AND s.event_date < :upToMonthBound
              AND (s.place_id IN (:placeIds) OR st.tag_id IN (:tagIds) OR s.id IN (:keywordIds))
            GROUP BY s.id
        )
        SELECT COUNT(*) FROM (SELECT id FROM matches LIMIT :cap)
        """
    )
    suspend fun queryCount(
        since: String,
        until: String,
        upToMonthBound: String,
        placeIds: List<Long>,
        tagIds: List<Long>,
        keywordIds: List<Long>,
        cap: Int,
    ): Int
}

data class SearchHitProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "at_sec") val atSec: Double,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "frame_index") val frameIndex: Int?,
    @ColumnInfo(name = "sb_level") val sbLevel: Int?,
    @ColumnInfo(name = "event_date") val eventDate: String,
    @ColumnInfo(name = "place") val place: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "relevance") val relevance: Int,
)
