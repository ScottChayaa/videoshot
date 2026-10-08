package com.xenyaa.videoshot.data.library.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity

@Dao
interface TagDao {
    @Insert suspend fun insert(tag: TagEntity): Long

    @Insert suspend fun link(link: ShotTagEntity)

    @Query("SELECT COUNT(*) FROM tag")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM shot_tag WHERE shot_id = :shotId")
    suspend fun linkCountOfShot(shotId: Long): Int

    @Query("DELETE FROM shot_tag WHERE shot_id = :shotId")
    suspend fun unlinkAllOfShot(shotId: Long)

    @Query("SELECT * FROM tag WHERE name = :name")
    suspend fun byName(name: String): TagEntity?

    @Query("SELECT name FROM tag ORDER BY name")
    suspend fun allNames(): List<String>

    @Query(
        """
        SELECT t.name FROM shot_tag st
        JOIN tag t ON t.id = st.tag_id
        WHERE st.shot_id = :shotId ORDER BY t.name
        """
    )
    suspend fun namesOfShot(shotId: Long): List<String>

    /** 規則式解析的詞彙表：所有標籤的名稱與別名 JSON（規格第四節，別名檢索時視同 name）。 */
    @Query("SELECT name, aliases FROM tag")
    suspend fun allWithAliases(): List<TagAliasProjection>

    /**
     * 名稱或別名轉 id（別名視同本名，合併後打舊名照樣找得到）。查不到的名字直接略過——檢索不像 `commitPicks`／`patchShots` 會新建標籤，
     * 使用者勾的是「既有」的標籤 chip，查詢裡不該無中生有一個新標籤。
     */
    @Query(
        """
        SELECT id FROM tag
        WHERE name IN (:names) OR EXISTS (
            SELECT 1 FROM json_each(CASE WHEN json_valid(tag.aliases) THEN tag.aliases ELSE '[]' END) j WHERE j.value IN (:names)
        )
        """
    )
    suspend fun idsByNames(names: List<String>): List<Long>

    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun byId(id: Long): TagEntity?

    @Query("UPDATE tag SET name = :name, kind = :kind, aliases = :aliases WHERE id = :id")
    suspend fun update(id: Long, name: String, kind: String, aliases: String)

    /**
     * 標籤合併：把 `oldTagId` 的關聯轉給 `newTagId`。**`OR IGNORE`**——
     * 一張圖如果同時已經關聯過這兩個標籤，轉移會撞到 `shot_tag` 的複合主鍵 `(shot_id, tag_id)`，
     * `OR IGNORE` 讓那一列維持原樣（還連著 `oldTagId`），呼叫端接著刪掉 `oldTagId`
     * 那一列標籤時，靠 `shot_tag` 的外鍵 `ON DELETE CASCADE` 把殘餘的那一列一併清掉。
     */
    @Query("UPDATE OR IGNORE shot_tag SET tag_id = :newTagId WHERE tag_id = :oldTagId")
    suspend fun reassignLinks(oldTagId: Long, newTagId: Long)

    /** 只刪標籤列本身；`shot_tag` 由外鍵 `ON DELETE CASCADE` 連動刪除（圖不動）。 */
    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun deleteById(id: Long)
}

data class TagAliasProjection(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "aliases") val aliases: String,
)

data class TagUsageProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "aliases") val aliases: String,
    @ColumnInfo(name = "shotCount") val shotCount: Int,
)
