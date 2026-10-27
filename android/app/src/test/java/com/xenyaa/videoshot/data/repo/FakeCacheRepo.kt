package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.FacetRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * `CacheRepo` 的記憶體版測試替身（比照 `FakeLibraryRepo` 的風格：`open class`，
 * 各方法用最直白的資料結構實作，測試需要特殊行為時繼承後覆寫單一方法）。
 */
open class FakeCacheRepo : CacheRepo {
    private val states = mutableMapOf<Triple<String, Int, Int>, ThumbStateEntity>()
    private var draft: DraftEntity? = null
    private var clipDraft: ClipDraft? = null

    /** `saveClipDraft` 被叫過幾次（測試用）。 */
    var clipDraftSaveCount: Int = 0
        private set
    private val recent = mutableMapOf<FacetRef, Long>()

    /** 輸出任務，編號小的在前（對外的 [editTasks] 再反過來）。 */
    private val tasks = MutableStateFlow<List<EditTask>>(emptyList())
    private var nextTaskId = 1L

    private fun key(videoId: String, sbLevel: Int, frameIndex: Int) = Triple(videoId, sbLevel, frameIndex)

    override suspend fun putThumbStates(states: List<ThumbStateEntity>) {
        states.forEach { this.states[key(it.videoId, it.sbLevel, it.frameIndex)] = it }
    }

    override suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity? =
        states[key(videoId, sbLevel, frameIndex)]

    override suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity> =
        states.values.filter { it.state == "missing" && it.nextTryAt <= now }
            .sortedWith(compareBy({ it.videoId }, { it.frameIndex }))
            .take(limit)

    override suspend fun forgetVideoThumbs(videoId: String) {
        states.keys.filter { it.first == videoId }.forEach { states.remove(it) }
    }

    override suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int) {
        states.remove(key(videoId, sbLevel, frameIndex))
    }

    override suspend fun countByState(state: String): Int = states.values.count { it.state == state }

    override suspend fun lostThumbs(): List<ThumbStateEntity> = states.values.filter { it.state == "lost" }

    override suspend fun resetLostToMissing(now: Long) {
        states.values.filter { it.state == "lost" }.forEach {
            states[key(it.videoId, it.sbLevel, it.frameIndex)] =
                it.copy(state = "missing", attempts = 0, nextTryAt = now, lostReason = null)
        }
    }

    override suspend fun saveDraft(draft: DraftEntity) { this.draft = draft }
    override suspend fun currentDraft(): DraftEntity? = draft
    override suspend fun clearDraft() { draft = null }

    override suspend fun saveClipDraft(draft: ClipDraft, now: Long) {
        clipDraft = draft
        clipDraftSaveCount++
    }
    override suspend fun currentClipDraft(): ClipDraft? = clipDraft
    override suspend fun clearClipDraft() { clipDraft = null }

    override suspend fun startEditTask(draft: ClipDraft, title: String, now: Long): Long {
        val id = nextTaskId++
        val task = EditTask(
            id = id,
            state = EditTaskState.QUEUED,
            progress = 0,
            title = title,
            draft = draft.copy(history = EditHistory(draft.timeline)),
            autoUpload = true,
            failReason = null,
            skippedSources = 0,
            galleryUri = null,
            createdAt = now,
        )
        tasks.update { it + task }
        clipDraft = null
        return id
    }

    override fun editTasks(): Flow<List<EditTask>> = tasks.map { it.asReversed() }
    override fun editTask(id: Long): Flow<EditTask?> = tasks.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun pendingEditTaskIds(): List<Long> =
        tasks.value.filter { it.state == EditTaskState.QUEUED || it.state == EditTaskState.RENDERING }.map { it.id }

    /** 改指定任務；編號不存在就什麼都不做。 */
    private fun changeTask(id: Long, change: (EditTask) -> EditTask) =
        tasks.update { list -> list.map { if (it.id == id) change(it) else it } }

    override suspend fun setEditTaskProgress(id: Long, state: EditTaskState, progress: Int, now: Long) =
        changeTask(id) { it.copy(state = state, progress = progress) }

    override suspend fun setEditTaskRendered(id: Long, skipped: Int, galleryUri: String?, now: Long) =
        changeTask(id) {
            it.copy(state = EditTaskState.RENDERED, progress = 100, skippedSources = skipped, galleryUri = galleryUri, failReason = null)
        }

    override suspend fun setEditTaskFailed(id: Long, reason: String, now: Long) =
        changeTask(id) { it.copy(state = EditTaskState.FAILED, failReason = reason) }

    override suspend fun setEditTaskAutoUpload(id: Long, on: Boolean, now: Long) =
        changeTask(id) { it.copy(autoUpload = on) }

    override suspend fun requeueEditTask(id: Long, now: Long) =
        changeTask(id) { it.copy(state = EditTaskState.QUEUED, progress = 0, failReason = null) }

    override suspend fun deleteEditTask(id: Long) { tasks.update { list -> list.filterNot { it.id == id } } }

    override suspend fun touchFacets(refs: Collection<FacetRef>, usedAt: Long) {
        refs.forEach { recent[it] = usedAt }
    }

    override suspend fun facetRecent(): Map<FacetRef, Long> = recent.toMap()

    override suspend fun mergeFacetRecent(kind: Int, fromId: Long, toId: Long) {
        val from = recent.remove(FacetRef(kind, fromId)) ?: return
        val to = FacetRef(kind, toId)
        recent[to] = maxOf(from, recent[to] ?: Long.MIN_VALUE)
    }

    override suspend fun forgetFacetRecent(kind: Int, id: Long) {
        recent.remove(FacetRef(kind, id))
    }

    override suspend fun clearAll() {
        states.clear()
        draft = null
        recent.clear()
    }
}
