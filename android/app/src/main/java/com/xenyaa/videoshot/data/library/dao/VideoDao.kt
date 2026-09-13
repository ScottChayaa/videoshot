package com.xenyaa.videoshot.data.library.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xenyaa.videoshot.data.library.entity.VideoEntity

@Dao
interface VideoDao {
    @Upsert suspend fun upsert(video: VideoEntity)

    @Query("SELECT * FROM video WHERE id = :id")
    suspend fun byId(id: String): VideoEntity?
}
