package com.xenyaa.videoshot.data.repo

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.dao.ShotRowProjection
import com.xenyaa.videoshot.data.library.entity.FolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.ShotImageEntity
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class RoomLibraryRepo(
    private val db: LibraryDatabase,
    private val io: CoroutineDispatcher,
    /** 每次成功寫入 library.db 之後呼叫；自動備份用它判斷「有沒有變更」（規格第十節）。 */
    private val onChanged: suspend () -> Unit = {},
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

    override suspend fun shotImage(shotId: Long): ByteArray? = withContext(io) {
        db.shotDao().imageOf(shotId)?.webp
    }

    override suspend fun recentVideos(limit: Int): List<RecentVideo> = withContext(io) {
        db.videoDao().recent(limit).map { RecentVideo(it.videoId, it.title, it.addedAt, it.shotCount) }
    }

    override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = withContext(io) {
        db.videoDao().takenFrameIndexes(videoId, level).toSet()
    }

    override suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long> = withContext(io) {
        val ids = db.inWriteTransaction {
            db.videoDao().upsert(video)

            // 先把這一批用到的標籤名解析成 id：同名只查一次、只建一次。
            // **在交易內**——整批回滾時這些新標籤要跟著消失，否則標籤管理頁會列出一堆沒有圖的空標籤
            val tagIds = mutableMapOf<String, Long>()
            for (name in picks.flatMap { it.tagNames }.distinct()) {
                tagIds[name] = db.tagDao().byName(name)?.id
                    // 精靈沒有問使用者這是人還是主題，猜一個就是騙人；分類留給階段 11 的標籤管理
                    ?: db.tagDao().insert(TagEntity(id = 0, name = name, kind = "other", aliases = "[]"))
            }

            picks.map { pick ->
                val id = db.shotDao().insert(
                    ShotEntity(
                        id = 0,
                        videoId = video.id,
                        atSec = pick.atSec,
                        source = pick.source,
                        frameIndex = pick.frameIndex,
                        sbLevel = pick.sbLevel,
                        eventDate = pick.eventDate,
                        place = pick.place,
                        description = pick.description,
                        aiTranscript = null,
                        aiVisualDesc = null,
                        aiRaw = null,
                        createdAt = System.currentTimeMillis() / 1000,
                    )
                )
                pick.webp?.let { db.shotDao().putImage(ShotImageEntity(id, it)) }
                pick.tagNames.forEach { name ->
                    db.tagDao().link(ShotTagEntity(id, tagIds.getValue(name), "human"))
                }
                id
            }
        }
        onChanged()
        ids
    }

    override suspend fun distinctPlaces(): List<String> = withContext(io) { db.shotDao().distinctPlaces() }

    override suspend fun allTagNames(): List<String> = withContext(io) { db.tagDao().allNames() }

    override suspend fun patchShots(ids: List<Long>, patch: ShotPatch): Unit = withContext(io) {
        db.inWriteTransaction {
            for (id in ids) {
                patch.eventDate?.let { db.shotDao().updateEventDate(id, it) }
                patch.place?.let { db.shotDao().updatePlace(id, it) }
                patch.description?.let { db.shotDao().updateDescription(id, it) }
                patch.tagIds?.let { tagIds ->
                    db.tagDao().unlinkAllOfShot(id)
                    tagIds.forEach { db.tagDao().link(ShotTagEntity(id, it, "human")) }
                }
            }
        }
        onChanged()
    }

    override suspend fun deleteShot(id: Long): Unit = withContext(io) {
        db.inWriteTransaction {
            val videoId = db.shotDao().rowById(id)?.videoId
            if (videoId != null) {
                db.shotDao().deleteById(id)
                // shot_image、shot_tag、shot_folder 由外鍵連動，shot_fts 由觸發器連動
                if (db.shotDao().countOfVideo(videoId) == 0) db.videoDao().deleteById(videoId)
            }
        }
        onChanged()
    }

    override suspend fun deleteVideo(videoId: String): Unit = withContext(io) {
        db.inWriteTransaction {
            db.shotDao().deleteOfVideo(videoId)
            db.videoDao().deleteById(videoId)
        }
        onChanged()
    }

    override suspend fun createFolder(parentId: Long?, name: String): Long = withContext(io) {
        require(name.isNotBlank()) { "資料夾名稱不可空白" }
        require(name.length <= 50) { "資料夾名稱上限 50 字，收到 ${name.length} 字" }
        val id = db.inWriteTransaction {
            // 同層不重名：SQLite 的唯一索引把多個 NULL 視為互異，根層擋不住，只能在這裡查
            require(db.folderDao().countSameNameInLayer(parentId, name) == 0) { "同一層已經有「$name」了" }
            var depth = 1
            var cursor = parentId
            while (cursor != null) {
                depth++
                require(depth <= 5) { "資料夾深度上限 5 層" }
                cursor = db.folderDao().parentOf(cursor)
            }
            db.folderDao().insert(FolderEntity(0, parentId, name, System.currentTimeMillis() / 1000))
        }
        onChanged()
        id
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

/**
 * 用 driver API 的寫入交易。
 * room-ktx 的 `RoomDatabase.withTransaction` 走舊的 SupportSQLiteOpenHelper 路徑，
 * 設了 `setDriver(BundledSQLiteDriver())` 之後那條路沒有接上，會丟
 * `Cannot return a SupportSQLiteOpenHelper since no SupportSQLiteOpenHelper.Factory was configured`。
 * IMMEDIATE 讓交易一開始就取得寫入鎖，避免寫到一半才發現拿不到鎖而失敗。
 */
private suspend fun <R> LibraryDatabase.inWriteTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor ->
        transactor.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { block() }
    }
