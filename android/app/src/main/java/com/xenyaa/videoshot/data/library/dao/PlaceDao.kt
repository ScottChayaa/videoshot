package com.xenyaa.videoshot.data.library.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xenyaa.videoshot.data.library.entity.PlaceEntity

@Dao
interface PlaceDao {
    @Insert suspend fun insert(place: PlaceEntity): Long

    @Query("SELECT * FROM place WHERE name = :name")
    suspend fun byName(name: String): PlaceEntity?

    @Query("SELECT * FROM place WHERE id = :id")
    suspend fun byId(id: Long): PlaceEntity?

    @Query("UPDATE place SET name = :name, aliases = :aliases WHERE id = :id")
    suspend fun update(id: Long, name: String, aliases: String)

    /** 合併：來源地點的圖全部改指向目標。統計表由 16A 的觸發器（`shot_stat_shot_place`）跟上。 */
    @Query("UPDATE shot SET place_id = :toId WHERE place_id = :fromId")
    suspend fun reassignShots(fromId: Long, toId: Long)

    /**
     * 名稱或別名轉 id（16C：別名視同本名）。`json_each` 是 SQLite 內建的 JSON 函式，
     * 展開 `aliases` 這個 JSON 陣列。查不到的略過（查詢不會新建地點，同 `TagDao.idsByNames`）。
     */
    @Query(
        """
        SELECT id FROM place
        WHERE name IN (:names) OR EXISTS (SELECT 1 FROM json_each(CASE WHEN json_valid(place.aliases) THEN place.aliases ELSE '[]' END) j WHERE j.value IN (:names))
        """
    )
    suspend fun idsByNames(names: List<String>): List<Long>

    /** 名稱或別名包含關鍵字的地點（描述查詢用：地點不在全文索引裡，規格第八節）。地點表只有幾千列，掃一遍是毫秒級。 */
    @Query(
        """
        SELECT id FROM place
        WHERE name LIKE '%' || :keyword || '%'
           OR EXISTS (SELECT 1 FROM json_each(CASE WHEN json_valid(place.aliases) THEN place.aliases ELSE '[]' END) j WHERE j.value LIKE '%' || :keyword || '%')
        """
    )
    suspend fun idsNameContains(keyword: String): List<Long>

    /** 有圖在用的地點本名＋別名 JSON（規則式解析的詞彙表）。 */
    @Query(
        """
        SELECT p.name AS name, p.aliases AS aliases FROM place p
        WHERE EXISTS (SELECT 1 FROM shot s WHERE s.place_id = p.id)
        ORDER BY p.name
        """
    )
    suspend fun usedWithAliases(): List<TagAliasProjection>

    /**
     * 至少有一張圖在用的地點（取圖第三步的「用過的地點」與規則式解析的詞彙表）。
     * 沒有圖在用的地點留在表裡（之後地點管理可以看到、刪除），但不出現在建議裡——
     * 跟改版前 `SELECT DISTINCT place FROM shot` 的結果一致。走 `index_shot_place_id`。
     */
    @Query(
        """
        SELECT p.name FROM place p
        WHERE EXISTS (SELECT 1 FROM shot s WHERE s.place_id = p.id)
        ORDER BY p.name
        """
    )
    suspend fun usedNames(): List<String>

    /** 圖的 `place_id` 由外鍵 `ON DELETE SET NULL` 清空，圖本身不動。地點管理（16C）與測試用。 */
    @Query("DELETE FROM place WHERE id = :id")
    suspend fun deleteById(id: Long)
}

data class PlaceUsageProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "aliases") val aliases: String,
    @ColumnInfo(name = "shotCount") val shotCount: Int,
)
