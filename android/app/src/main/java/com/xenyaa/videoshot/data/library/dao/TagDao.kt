package com.xenyaa.videoshot.data.library.dao

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
}
