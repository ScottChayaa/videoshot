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
     * 名稱轉 id。查不到的名字直接略過——檢索不像 `commitPicks`／`patchShots` 會新建標籤，
     * 使用者勾的是「既有」的標籤 chip，查詢裡不該無中生有一個新標籤。
     */
    @Query("SELECT id FROM tag WHERE name IN (:names)")
    suspend fun idsByNames(names: List<String>): List<Long>
}

data class TagAliasProjection(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "aliases") val aliases: String,
)
