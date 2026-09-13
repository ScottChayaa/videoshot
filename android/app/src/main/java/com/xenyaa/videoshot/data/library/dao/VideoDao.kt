package com.xenyaa.videoshot.data.library.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xenyaa.videoshot.data.library.entity.VideoEntity

@Dao
interface VideoDao {
    @Upsert suspend fun upsert(video: VideoEntity)

    @Query("SELECT * FROM video WHERE id = :id")
    suspend fun byId(id: String): VideoEntity?

    @Query("DELETE FROM video WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        SELECT v.id AS video_id, v.title AS title, v.added_at AS added_at,
               (SELECT COUNT(*) FROM shot s WHERE s.video_id = v.id) AS shot_count
        FROM video v ORDER BY v.added_at DESC LIMIT :limit
        """
    )
    suspend fun recent(limit: Int): List<RecentVideoProjection>

    @Query("SELECT frame_index FROM shot WHERE video_id = :videoId AND frame_index IS NOT NULL")
    suspend fun takenFrameIndexes(videoId: String): List<Int>
}

data class RecentVideoProjection(
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "shot_count") val shotCount: Int,
)
