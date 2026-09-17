package com.xenyaa.videoshot.data.cache.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity

@Dao
interface ThumbStateDao {
    @Upsert suspend fun upsert(state: ThumbStateEntity)

    @Upsert suspend fun upsertAll(states: List<ThumbStateEntity>)

    @Query("SELECT * FROM thumb_state WHERE video_id = :videoId AND sb_level = :sbLevel AND frame_index = :frameIndex")
    suspend fun byKey(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity?

    @Query("SELECT COUNT(*) FROM thumb_state WHERE video_id = :videoId")
    suspend fun countOfVideo(videoId: String): Int

    /** 回填作業要處理的：還缺、而且已經到重試時間的。依影片分組交給呼叫端。 */
    @Query(
        """
        SELECT * FROM thumb_state
        WHERE state = 'missing' AND next_try_at <= :now
        ORDER BY video_id, frame_index
        LIMIT :limit
        """
    )
    suspend fun dueForRetry(now: Long, limit: Int): List<ThumbStateEntity>

    @Query("DELETE FROM thumb_state WHERE video_id = :videoId")
    suspend fun deleteVideo(videoId: String)

    @Query("DELETE FROM thumb_state WHERE video_id = :videoId AND sb_level = :sbLevel AND frame_index = :frameIndex")
    suspend fun deleteByKey(videoId: String, sbLevel: Int, frameIndex: Int)

    @Query("DELETE FROM thumb_state")
    suspend fun clear()
}

@Dao
interface DraftDao {
    @Upsert suspend fun put(draft: DraftEntity)

    @Query("SELECT * FROM draft WHERE id = ${DraftEntity.ONLY_ROW}")
    suspend fun current(): DraftEntity?

    @Query("SELECT COUNT(*) FROM draft")
    suspend fun count(): Int

    @Query("DELETE FROM draft")
    suspend fun clear()
}
