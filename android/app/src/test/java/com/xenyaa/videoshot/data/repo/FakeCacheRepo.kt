package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.model.FacetRef

/**
 * `CacheRepo` 的記憶體版測試替身（比照 `FakeLibraryRepo` 的風格：`open class`，
 * 各方法用最直白的資料結構實作，測試需要特殊行為時繼承後覆寫單一方法）。
 */
open class FakeCacheRepo : CacheRepo {
    private val states = mutableMapOf<Triple<String, Int, Int>, ThumbStateEntity>()
    private var draft: DraftEntity? = null
    private val recent = mutableMapOf<FacetRef, Long>()

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
