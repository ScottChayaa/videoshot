package com.xenyaa.videoshot.data.repo

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipDraftCodec
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.EditDraftEntity
import com.xenyaa.videoshot.data.cache.entity.EditTaskEntity
import com.xenyaa.videoshot.data.cache.entity.FacetRecentEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.data.repo.model.FacetRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomCacheRepo(
    private val db: CacheDatabase,
    private val io: CoroutineDispatcher,
) : CacheRepo {

    override suspend fun putThumbStates(states: List<ThumbStateEntity>) = withContext(io) {
        db.thumbStateDao().upsertAll(states)
    }

    override suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity? =
        withContext(io) { db.thumbStateDao().byKey(videoId, sbLevel, frameIndex) }

    override suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity> =
        withContext(io) { db.thumbStateDao().dueForRetry(now, limit) }

    override suspend fun forgetVideoThumbs(videoId: String) = withContext(io) {
        db.thumbStateDao().deleteVideo(videoId)
    }

    override suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int) = withContext(io) {
        db.thumbStateDao().deleteByKey(videoId, sbLevel, frameIndex)
    }

    override suspend fun countByState(state: String): Int =
        withContext(io) { db.thumbStateDao().countByState(state) }

    override suspend fun lostThumbs(): List<ThumbStateEntity> =
        withContext(io) { db.thumbStateDao().lost() }

    override suspend fun resetLostToMissing(now: Long) = withContext(io) {
        db.thumbStateDao().resetLostToMissing(now)
    }

    override suspend fun saveDraft(draft: DraftEntity) = withContext(io) { db.draftDao().put(draft) }

    override suspend fun currentDraft(): DraftEntity? = withContext(io) { db.draftDao().current() }

    override suspend fun clearDraft() = withContext(io) { db.draftDao().clear() }

    override suspend fun saveClipDraft(draft: ClipDraft, now: Long) = withContext(io) {
        db.editDraftDao().put(EditDraftEntity(draft.orientation.name, ClipDraftCodec.encode(draft), now))
    }

    override suspend fun currentClipDraft(): ClipDraft? = withContext(io) {
        val row = db.editDraftDao().current() ?: return@withContext null
        ClipDraftCodec.decode(row.payload) ?: run {
            // 解不開的草稿留著只會每次都失敗，直接丟掉
            db.editDraftDao().clear()
            null
        }
    }

    override suspend fun clearClipDraft() = withContext(io) { db.editDraftDao().clear() }

    override suspend fun startEditTask(draft: ClipDraft, title: String, autoUpload: Boolean, now: Long): Long = withContext(io) {
        val task = draft.copy(history = EditHistory(draft.timeline)).let {
            EditTaskEntity(
                id = 0,
                state = EditTaskState.QUEUED.dbValue,
                progress = 0,
                title = title,
                orientation = it.orientation.name,
                durationMs = it.timeline.durationMs,
                payload = ClipDraftCodec.encode(it),
                autoUpload = if (autoUpload) 1 else 0,
                failReason = null,
                skippedSources = 0,
                galleryUri = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        db.useWriterConnection { transactor ->
            transactor.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                val id = db.editTaskDao().insert(task)
                db.editDraftDao().clear()
                id
            }
        }
    }

    // 解 payload（整份草稿的 JSON）放在 io：輸出中每秒寫一次進度，這兩個流就每秒重解一次，收集端常在主執行緒
    override fun editTasks(): Flow<List<EditTask>> =
        db.editTaskDao().all().map { rows -> rows.mapNotNull { it.toTask() } }.flowOn(io)

    override fun editTask(id: Long): Flow<EditTask?> =
        db.editTaskDao().byId(id).map { it?.toTask() }.flowOn(io)

    override suspend fun pendingEditTaskIds(): List<Long> = withContext(io) { db.editTaskDao().pendingIds() }

    override suspend fun setEditTaskProgress(id: Long, state: EditTaskState, progress: Int, now: Long) = withContext(io) {
        db.editTaskDao().setProgress(id, state.dbValue, progress, now)
    }

    override suspend fun setEditTaskRendered(id: Long, skipped: Int, galleryUri: String?, now: Long) = withContext(io) {
        db.editTaskDao().setRendered(id, skipped, galleryUri, now)
    }

    override suspend fun processingEditTaskIds(): List<Long> = withContext(io) { db.editTaskDao().processingIds() }

    override suspend fun waitingQuotaEditTaskIds(): List<Long> = withContext(io) { db.editTaskDao().waitingQuotaIds() }

    override fun doneEditTaskCount(): Flow<Int> = db.editTaskDao().doneCount().flowOn(io)

    override suspend fun setEditTaskReadyToUpload(id: Long, skipped: Int, now: Long) = withContext(io) {
        db.editTaskDao().setReadyToUpload(id, skipped, now)
    }

    override suspend fun setEditTaskUploadUrl(id: Long, url: String?, now: Long) = withContext(io) {
        db.editTaskDao().setUploadUrl(id, url, now)
    }

    override suspend fun setEditTaskUploaded(id: Long, videoId: String, now: Long) = withContext(io) {
        db.editTaskDao().setUploaded(id, videoId, now)
    }

    override suspend fun setEditTaskDone(id: Long, now: Long) = withContext(io) {
        db.editTaskDao().setDone(id, now)
    }

    override suspend fun setEditTaskFailed(id: Long, reason: String, step: EditTaskStep, now: Long) = withContext(io) {
        db.editTaskDao().setFailed(id, reason, step.dbValue, now)
    }

    override suspend fun setEditTaskWaitingQuota(id: Long, reason: String, now: Long) = withContext(io) {
        db.editTaskDao().setWaitingQuota(id, reason, now)
    }

    override suspend fun resumeEditTaskFromQuota(id: Long, now: Long): Boolean = withContext(io) {
        db.editTaskDao().resumeFromQuota(id, now) > 0
    }

    override suspend fun setEditTaskAutoUpload(id: Long, on: Boolean, now: Long) = withContext(io) {
        db.editTaskDao().setAutoUpload(id, if (on) 1 else 0, now)
    }

    override suspend fun requeueEditTask(id: Long, now: Long) = withContext(io) {
        db.editTaskDao().requeue(id, now)
    }

    override suspend fun retryEditTask(id: Long, now: Long): EditTaskState? = withContext(io) {
        // 讀步驟與改狀態同一個交易：中間不會被別的寫入插進來
        db.useWriterConnection { transactor ->
            transactor.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                val dao = db.editTaskDao()
                val row = dao.once(id)
                if (row == null || EditTaskState.fromDb(row.state) != EditTaskState.FAILED) {
                    null
                } else {
                    when (row.failedStep?.let(EditTaskStep::fromDb) ?: EditTaskStep.RENDER) {
                        EditTaskStep.RENDER -> { dao.retryRender(id, now); EditTaskState.QUEUED }
                        EditTaskStep.UPLOAD -> { dao.retryUpload(id, now); EditTaskState.UPLOADING }
                        EditTaskStep.STORYBOARD -> { dao.retryStoryboard(id, now); EditTaskState.PROCESSING }
                    }
                }
            }
        }
    }

    override suspend fun deleteEditTask(id: Long) = withContext(io) { db.editTaskDao().delete(id) }

    override suspend fun touchFacets(refs: Collection<FacetRef>, usedAt: Long) = withContext(io) {
        db.facetRecentDao().upsertAll(refs.distinct().map { FacetRecentEntity(it.kind, it.id, usedAt) })
    }

    override suspend fun facetRecent(): Map<FacetRef, Long> = withContext(io) {
        db.facetRecentDao().all().associate { FacetRef(it.kind, it.refId) to it.usedAt }
    }

    override suspend fun mergeFacetRecent(kind: Int, fromId: Long, toId: Long) = withContext(io) {
        db.useWriterConnection { transactor ->
            transactor.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                val dao = db.facetRecentDao()
                val from = dao.byKey(kind, fromId) ?: return@withTransaction
                val to = dao.byKey(kind, toId)
                if (to == null || to.usedAt < from.usedAt) {
                    dao.upsertAll(listOf(FacetRecentEntity(kind, toId, from.usedAt)))
                }
                dao.delete(kind, fromId)
            }
        }
    }

    override suspend fun forgetFacetRecent(kind: Int, id: Long) = withContext(io) {
        db.facetRecentDao().delete(kind, id)
    }

    override suspend fun clearAll() = withContext(io) {
        // facet_recent 放最前面：還原後舊的地點／標籤編號對不上，這張表最該被清到，後面失敗也不能漏掉
        db.facetRecentDao().clear()
        db.thumbStateDao().clear()
        db.draftDao().clear()
        // edit_draft（剪輯草稿）刻意不清：引用的是這台手機的媒體，還原圖庫不影響它
    }
}

/** payload 解不開（版本不認識、內容壞掉）回 null。 */
private fun EditTaskEntity.toTask(): EditTask? {
    val draft = ClipDraftCodec.decode(payload) ?: return null
    return EditTask(
        id = id,
        state = EditTaskState.fromDb(state),
        progress = progress,
        title = title,
        draft = draft,
        autoUpload = autoUpload != 0,
        failReason = failReason,
        skippedSources = skippedSources,
        galleryUri = galleryUri,
        createdAt = createdAt,
        failedStep = failedStep?.let(EditTaskStep::fromDb),
        uploadUrl = uploadUrl,
        videoId = videoId,
        updatedAt = updatedAt,
    )
}
