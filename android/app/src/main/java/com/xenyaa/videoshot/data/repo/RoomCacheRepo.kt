package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
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

    override suspend fun saveDraft(draft: DraftEntity) = withContext(io) { db.draftDao().put(draft) }

    override suspend fun currentDraft(): DraftEntity? = withContext(io) { db.draftDao().current() }

    override suspend fun clearDraft() = withContext(io) { db.draftDao().clear() }

    override suspend fun clearAll() = withContext(io) {
        db.thumbStateDao().clear()
        db.draftDao().clear()
    }
}
