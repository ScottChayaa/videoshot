package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.dao.ShotRowProjection
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class RoomLibraryRepo(
    private val db: LibraryDatabase,
    private val io: CoroutineDispatcher,
) : LibraryRepo {

    override suspend fun homeFeed(after: ShotCursor?, limit: Int): Page<ShotRow> = withContext(io) {
        val rows = if (after == null) {
            db.shotDao().feedFirst(limit)
        } else {
            db.shotDao().feedAfter(after.eventDate, after.id, limit)
        }
        // 撈滿才可能有下一頁；沒撈滿代表到底了，游標給 null 讓畫面停止請求
        val next = if (rows.size < limit) null else rows.last().let { ShotCursor(it.eventDate, it.id) }
        Page(rows.map { it.toRow() }, next)
    }

    override suspend fun monthCounts(): List<MonthCount> = withContext(io) {
        db.shotDao().monthCounts().map { MonthCount(it.month, it.count) }
    }

    override suspend fun shotsOfVideo(videoId: String): List<ShotRow> = withContext(io) {
        db.shotDao().ofVideo(videoId).map { it.toRow() }
    }

    override suspend fun shotById(id: Long): ShotRow? = withContext(io) {
        db.shotDao().rowById(id)?.toRow()
    }
}

private fun ShotRowProjection.toRow() = ShotRow(
    id = id,
    videoId = videoId,
    atSec = atSec,
    source = source,
    frameIndex = frameIndex,
    sbLevel = sbLevel,
    eventDate = eventDate,
    place = place,
    description = description,
)
