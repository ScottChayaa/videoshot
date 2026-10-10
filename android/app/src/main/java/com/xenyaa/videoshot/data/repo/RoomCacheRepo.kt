package com.xenyaa.videoshot.data.repo

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.FacetRecentEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.model.FacetRef
import kotlinx.coroutines.CoroutineDispatcher
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
        db.thumbStateDao().clear()
        db.draftDao().clear()
        db.facetRecentDao().clear()
    }
}
