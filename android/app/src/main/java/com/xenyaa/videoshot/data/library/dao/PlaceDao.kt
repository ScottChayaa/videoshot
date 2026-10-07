package com.xenyaa.videoshot.data.library.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xenyaa.videoshot.data.library.entity.PlaceEntity

@Dao
interface PlaceDao {
    @Insert suspend fun insert(place: PlaceEntity): Long

    @Query("SELECT * FROM place WHERE name = :name")
    suspend fun byName(name: String): PlaceEntity?

    /** 名稱轉 id；查不到的名字略過（查詢不會新建地點，同 `TagDao.idsByNames`）。 */
    @Query("SELECT id FROM place WHERE name IN (:names)")
    suspend fun idsByNames(names: List<String>): List<Long>

    /**
     * 名稱包含關鍵字的地點（描述查詢用：地點不在全文索引裡，關鍵字另外比對地點名稱，規格第八節）。
     * 地點表只有幾千列，LIKE 掃一遍是毫秒級。
     */
    @Query("SELECT id FROM place WHERE name LIKE '%' || :keyword || '%'")
    suspend fun idsNameContains(keyword: String): List<Long>

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
