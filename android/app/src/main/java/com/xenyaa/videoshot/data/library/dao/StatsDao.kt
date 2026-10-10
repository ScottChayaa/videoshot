package com.xenyaa.videoshot.data.library.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification

/**
 * 讀統計表（規格第四節「統計表」）。`shot_stat`／`shot_stat_total` 跟 `shot_fts` 一樣是原生 SQL 建的表，
 * Room 的編譯期驗證看不到，所以每個方法都 `@SkipQueryVerification`——**欄位別名必須跟投影類別的
 * `@ColumnInfo` 一字不差**，寫錯只會在執行期才發現（`StatsReadTest` 守著）。
 *
 * kind：0＝全部（ref_id 0）、1＝地點（place.id）、2＝標籤（tag.id）。**一律 `cnt > 0`**。
 */
@Dao
interface StatsDao {

    /** 月份選單：有收藏的月份與張數，新到舊。走 `shot_stat_by_ref (kind, ref_id, month)`。 */
    @SkipQueryVerification
    @Query("SELECT month AS month, cnt AS count FROM shot_stat WHERE kind = 0 AND ref_id = 0 AND cnt > 0 ORDER BY month DESC")
    suspend fun monthCounts(): List<MonthCountProjection>

    /** 某個月的地點與標籤，依張數多到少、同張數依名稱（跟改版前的 `GROUP BY` 版本同一個順序）。走主鍵 `(month, kind, ref_id)`。 */
    @SkipQueryVerification
    @Query(
        """
        SELECT COALESCE(p.name, t.name) AS name,
               CASE s.kind WHEN 1 THEN 'place' ELSE 'tag' END AS kind,
               CASE s.kind WHEN 1 THEN 'other' ELSE t.kind END AS tag_kind,
               s.cnt AS cnt
        FROM shot_stat s
        LEFT JOIN place p ON s.kind = 1 AND p.id = s.ref_id
        LEFT JOIN tag t ON s.kind = 2 AND t.id = s.ref_id
        WHERE s.month = :month AND s.kind IN (1, 2) AND s.cnt > 0
        ORDER BY s.cnt DESC, name, s.kind
        """
    )
    suspend fun monthFacets(month: String): List<MonthFacetProjection>

    /**
     * 查詢頁候選清單（設計決議 3）：依**全部時間**的張數排序；`upToMonth` 只拿掉「那個月（含）以前沒有任何圖」的項目。
     * `upToMonth` 為 null＝不限時間。`EXISTS` 走 `shot_stat_by_ref (kind, ref_id, month)`，每個項目一次索引查找。
     * 回傳的 `cnt` 是全部時間的張數（小膠囊不顯示張數，只用來排序）。
     */
    @SkipQueryVerification
    @Query(
        """
        SELECT COALESCE(p.name, t.name) AS name,
               CASE g.kind WHEN 1 THEN 'place' ELSE 'tag' END AS kind,
               CASE g.kind WHEN 1 THEN 'other' ELSE t.kind END AS tag_kind,
               g.cnt AS cnt
        FROM shot_stat_total g
        LEFT JOIN place p ON g.kind = 1 AND p.id = g.ref_id
        LEFT JOIN tag t ON g.kind = 2 AND t.id = g.ref_id
        WHERE g.kind IN (1, 2) AND g.cnt > 0
          AND (:upToMonth IS NULL OR EXISTS (
                SELECT 1 FROM shot_stat s
                WHERE s.kind = g.kind AND s.ref_id = g.ref_id AND s.month <= :upToMonth AND s.cnt > 0))
        ORDER BY g.cnt DESC, name, g.kind
        LIMIT :limit
        """
    )
    suspend fun candidates(upToMonth: String?, limit: Int): List<MonthFacetProjection>

    /**
     * 首頁篩選抽屜的候選（階段 17）：全部地點與標籤，依**全部時間**張數排序；`upToMonth` 只過濾（同 [candidates]）。
     * 跟 [candidates] 的差別：不設上限、帶別名（抽屜的搜尋框要比對）。
     */
    @SkipQueryVerification
    @Query(
        """
        SELECT COALESCE(p.name, t.name) AS name,
               g.kind AS kind,
               CASE g.kind WHEN 1 THEN 'other' ELSE t.kind END AS tag_kind,
               COALESCE(p.aliases, t.aliases) AS aliases,
               g.ref_id AS ref_id
        FROM shot_stat_total g
        LEFT JOIN place p ON g.kind = 1 AND p.id = g.ref_id
        LEFT JOIN tag t ON g.kind = 2 AND t.id = g.ref_id
        WHERE g.kind IN (1, 2) AND g.cnt > 0
          AND (:upToMonth IS NULL OR EXISTS (
                SELECT 1 FROM shot_stat s
                WHERE s.kind = g.kind AND s.ref_id = g.ref_id AND s.month <= :upToMonth AND s.cnt > 0))
        ORDER BY g.cnt DESC, name, g.kind
        """
    )
    suspend fun filterOptions(upToMonth: String?): List<FilterOptionProjection>

    /** 有圖的月份，新到舊（月份選單；不帶張數，階段 17 設計決議 5）。 */
    @SkipQueryVerification
    @Query("SELECT month FROM shot_stat WHERE kind = 0 AND ref_id = 0 AND cnt > 0 ORDER BY month DESC")
    suspend fun allMonths(): List<String>

    /** 任一選取的地點或標籤有圖的月份，新到舊。走 `shot_stat_by_ref (kind, ref_id, month)`。 */
    @SkipQueryVerification
    @Query(
        """
        SELECT DISTINCT month FROM shot_stat
        WHERE cnt > 0 AND ((kind = 1 AND ref_id IN (:placeIds)) OR (kind = 2 AND ref_id IN (:tagIds)))
        ORDER BY month DESC
        """
    )
    suspend fun monthsOf(placeIds: List<Long>, tagIds: List<Long>): List<String>

    @SkipQueryVerification
    @Query("SELECT COALESCE((SELECT cnt FROM shot_stat_total WHERE kind = 0 AND ref_id = 0), 0)")
    suspend fun totalShots(): Int

    @SkipQueryVerification
    @Query("SELECT COALESCE((SELECT cnt FROM shot_stat WHERE month = :month AND kind = 0 AND ref_id = 0), 0)")
    suspend fun shotsInMonth(month: String): Int

    /** 地點管理：全部地點 ＋ 張數（沒有圖的是 0），依名稱排序。 */
    @SkipQueryVerification
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.aliases AS aliases, COALESCE(g.cnt, 0) AS shotCount
        FROM place p LEFT JOIN shot_stat_total g ON g.kind = 1 AND g.ref_id = p.id
        ORDER BY p.name
        """
    )
    suspend fun placesWithUsage(): List<PlaceUsageProjection>

    /** 標籤管理：全部標籤 ＋ 張數（沒有圖的標籤是 0），依名稱排序。 */
    @SkipQueryVerification
    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.kind AS kind, t.aliases AS aliases, COALESCE(g.cnt, 0) AS shotCount
        FROM tag t LEFT JOIN shot_stat_total g ON g.kind = 2 AND g.ref_id = t.id
        ORDER BY t.name
        """
    )
    suspend fun tagsWithUsage(): List<TagUsageProjection>

    /**
     * 選取的地點與標籤的總張數相加（不看時間範圍、同一張圖可能重複計算）——只拿來決定查詢結果第一頁用哪一種查法
     * （設計決議 4），不是顯示用的張數。
     */
    @SkipQueryVerification
    @Query(
        """
        SELECT COALESCE(SUM(cnt), 0) FROM shot_stat_total
        WHERE (kind = 1 AND ref_id IN (:placeIds)) OR (kind = 2 AND ref_id IN (:tagIds))
        """
    )
    suspend fun selectedTotal(placeIds: List<Long>, tagIds: List<Long>): Long
}

/** [StatsDao.filterOptions] 的一列；欄位別名要跟這裡的 `@ColumnInfo` 一字不差。 */
data class FilterOptionProjection(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: Int,
    @ColumnInfo(name = "tag_kind") val tagKind: String,
    @ColumnInfo(name = "aliases") val aliases: String,
    @ColumnInfo(name = "ref_id") val refId: Long,
)
