package com.xenyaa.videoshot.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.EditDraftEntity
import com.xenyaa.videoshot.data.cache.entity.EditTaskEntity
import com.xenyaa.videoshot.data.cache.entity.FacetRecentEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT COUNT(*) FROM thumb_state WHERE state = :state")
    suspend fun countByState(state: String): Int

    @Query("SELECT * FROM thumb_state WHERE state = 'lost'")
    suspend fun lost(): List<ThumbStateEntity>

    /** 【稍後重試】：把全部 lost 的格子重設成 missing，立刻可以重試（規格第十一節）。 */
    @Query(
        "UPDATE thumb_state SET state = 'missing', attempts = 0, next_try_at = :now, lost_reason = NULL " +
            "WHERE state = 'lost'"
    )
    suspend fun resetLostToMissing(now: Long)

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

@Dao
interface FacetRecentDao {
    @Upsert suspend fun upsertAll(rows: List<FacetRecentEntity>)

    @Query("SELECT * FROM facet_recent")
    suspend fun all(): List<FacetRecentEntity>

    @Query("SELECT * FROM facet_recent WHERE kind = :kind AND ref_id = :refId")
    suspend fun byKey(kind: Int, refId: Long): FacetRecentEntity?

    @Query("DELETE FROM facet_recent WHERE kind = :kind AND ref_id = :refId")
    suspend fun delete(kind: Int, refId: Long)

    @Query("DELETE FROM facet_recent")
    suspend fun clear()
}

@Dao
interface EditDraftDao {
    @Upsert suspend fun put(draft: EditDraftEntity)

    @Query("SELECT * FROM edit_draft WHERE id = ${EditDraftEntity.ONLY_ROW}")
    suspend fun current(): EditDraftEntity?

    @Query("DELETE FROM edit_draft")
    suspend fun clear()
}

@Dao
interface EditTaskDao {
    @Insert suspend fun insert(task: EditTaskEntity): Long

    @Query("SELECT * FROM edit_task ORDER BY id DESC")
    fun all(): Flow<List<EditTaskEntity>>

    @Query("SELECT * FROM edit_task WHERE id = :id")
    fun byId(id: Long): Flow<EditTaskEntity?>

    @Query("SELECT * FROM edit_task WHERE id = :id")
    suspend fun once(id: Long): EditTaskEntity?

    @Query("SELECT id FROM edit_task WHERE state IN ('queued', 'rendering', 'uploading') ORDER BY id")
    suspend fun pendingIds(): List<Long>

    @Query("SELECT id FROM edit_task WHERE state = 'processing' ORDER BY id")
    suspend fun processingIds(): List<Long>

    @Query("SELECT COUNT(*) FROM edit_task WHERE state = 'done'")
    fun doneCount(): Flow<Int>

    @Query("UPDATE edit_task SET state = :state, progress = :progress, updated_at = :now WHERE id = :id")
    suspend fun setProgress(id: Long, state: String, progress: Int, now: Long)

    @Query(
        """
        UPDATE edit_task
        SET state = 'rendered', progress = 100, skipped_sources = :skipped, gallery_uri = :galleryUri,
            fail_reason = NULL, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun setRendered(id: Long, skipped: Int, galleryUri: String?, now: Long)

    @Query("UPDATE edit_task SET state = 'uploading', progress = 0, skipped_sources = :skipped, updated_at = :now WHERE id = :id")
    suspend fun setReadyToUpload(id: Long, skipped: Int, now: Long)

    @Query("UPDATE edit_task SET upload_url = :url, updated_at = :now WHERE id = :id")
    suspend fun setUploadUrl(id: Long, url: String?, now: Long)

    @Query(
        """
        UPDATE edit_task
        SET state = 'processing', progress = 100, video_id = :videoId, upload_url = NULL,
            fail_reason = NULL, failed_step = NULL, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun setUploaded(id: Long, videoId: String, now: Long)

    @Query("UPDATE edit_task SET state = 'done', updated_at = :now WHERE id = :id")
    suspend fun setDone(id: Long, now: Long)

    @Query("UPDATE edit_task SET state = 'failed', fail_reason = :reason, failed_step = :step, updated_at = :now WHERE id = :id")
    suspend fun setFailed(id: Long, reason: String, step: String, now: Long)

    /** 只動等待中與輸出中的：勾選框只在這兩個狀態顯示，之後的狀態（尤其處理中靠 `updated_at` 計時）不能被改到。 */
    @Query("UPDATE edit_task SET auto_upload = :on, updated_at = :now WHERE id = :id AND state IN ('queued','rendering')")
    suspend fun setAutoUpload(id: Long, on: Int, now: Long)

    /** 只動輸出中的：上傳中與處理中的任務有自己的接續方式，不能退回等待輸出。 */
    @Query("UPDATE edit_task SET state = 'queued', progress = 0, fail_reason = NULL, updated_at = :now WHERE id = :id AND state = 'rendering'")
    suspend fun requeue(id: Long, now: Long)

    /** 失敗任務依失敗的步驟回到那一步的開頭；三個都只動 `failed` 的列。 */
    @Query("UPDATE edit_task SET state = 'queued', progress = 0, fail_reason = NULL, failed_step = NULL, updated_at = :now WHERE id = :id AND state = 'failed'")
    suspend fun retryRender(id: Long, now: Long)

    @Query("UPDATE edit_task SET state = 'uploading', fail_reason = NULL, failed_step = NULL, updated_at = :now WHERE id = :id AND state = 'failed'")
    suspend fun retryUpload(id: Long, now: Long)

    @Query("UPDATE edit_task SET state = 'processing', fail_reason = NULL, failed_step = NULL, updated_at = :now WHERE id = :id AND state = 'failed'")
    suspend fun retryStoryboard(id: Long, now: Long)

    @Query("DELETE FROM edit_task WHERE id = :id")
    suspend fun delete(id: Long)
}
