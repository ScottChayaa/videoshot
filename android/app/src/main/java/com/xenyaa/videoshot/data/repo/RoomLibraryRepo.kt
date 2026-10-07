package com.xenyaa.videoshot.data.repo

import androidx.room.Transactor
import androidx.room.useWriterConnection
import com.xenyaa.videoshot.core.home.nextMonthStart
import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.core.paging.SearchCursor
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.query.ParsedQuery
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.core.query.TagAlias
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.dao.SearchHitProjection
import com.xenyaa.videoshot.data.library.dao.ShotRowProjection
import com.xenyaa.videoshot.data.library.entity.FolderEntity
import com.xenyaa.videoshot.data.library.entity.PlaceEntity
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.ShotFolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotImageEntity
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FACET_UNION_THRESHOLD
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.RESULT_COUNT_CAP
import com.xenyaa.videoshot.data.repo.model.FolderPage
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.SearchPage
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.data.repo.model.TagUsage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomLibraryRepo(
    private val db: LibraryDatabase,
    private val io: CoroutineDispatcher,
    /** 每次成功寫入 library.db 之後呼叫；自動備份用它判斷「有沒有變更」（規格第十節）。 */
    private val onChanged: suspend () -> Unit = {},
    /** 查詢結果第一頁的查法分界（設計決議 4）；測試用 0／Long.MAX_VALUE 強迫走某一條。 */
    private val facetUnionThreshold: Long = FACET_UNION_THRESHOLD,
) : LibraryRepo {

    /** 沒有篩選時的上界。任何合法的 event_date 都比它小。 */
    private fun boundOf(upToMonth: String?): String =
        upToMonth?.let { nextMonthStart(it) } ?: "9999-99-99"

    override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?): Page<ShotRow> =
        withContext(io) {
            val before = boundOf(upToMonth)
            val rows = if (after == null) {
                db.shotDao().feedFirst(before, limit)
            } else {
                db.shotDao().feedAfter(before, after.eventDate, after.id, limit)
            }
            // 撈滿才可能有下一頁；沒撈滿代表到底了，游標給 null 讓畫面停止請求
            val next = if (rows.size < limit) null else rows.last().let { ShotCursor(it.eventDate, it.id) }
            Page(rows.map { it.toRow() }, next)
        }

    override suspend fun shotCount(upToMonth: String?): Int = withContext(io) {
        db.shotDao().countBefore(boundOf(upToMonth))
    }

    override suspend fun monthFacets(month: String): List<MonthFacet> = withContext(io) {
        db.statsDao().monthFacets(month).map { MonthFacet(it.name, it.kind, it.count, it.tagKind) }
    }

    override suspend fun searchFacets(upToMonth: String?, limit: Int): List<MonthFacet> = withContext(io) {
        db.statsDao().candidates(upToMonth, limit).map { MonthFacet(it.name, it.kind, it.count, it.tagKind) }
    }

    override suspend fun searchByFacets(
        places: Set<String>,
        tagNames: Set<String>,
        upToMonth: String?,
        after: ShotCursor?,
        limit: Int,
    ): Page<ShotRow> = withContext(io) {
        val before = boundOf(upToMonth)
        val placeIds = resolvePlaceIds(places)
        val tagIds = resolveTagIds(tagNames)
        val useUnion = db.statsDao().selectedTotal(placeIds, tagIds) < facetUnionThreshold
        val rows = when {
            useUnion -> db.shotDao().facetSearchUnion(
                before, placeIds, tagIds,
                after?.eventDate ?: "9999-99-99", after?.id ?: Long.MAX_VALUE, limit,
            )
            after == null -> db.shotDao().facetSearchFirst(before, placeIds, tagIds, limit)
            else -> db.shotDao().facetSearchAfter(before, placeIds, tagIds, after.eventDate, after.id, limit)
        }
        val next = if (rows.size < limit) null else rows.last().let { ShotCursor(it.eventDate, it.id) }
        Page(rows.map { it.toRow() }, next)
    }

    override suspend fun searchByFacetsCount(places: Set<String>, tagNames: Set<String>, upToMonth: String?): Int =
        withContext(io) {
            db.shotDao().facetSearchCount(boundOf(upToMonth), resolvePlaceIds(places), resolveTagIds(tagNames), RESULT_COUNT_CAP + 1)
        }

    override suspend fun searchByQuery(query: ParsedQuery, upToMonth: String?, after: SearchCursor?, limit: Int): SearchPage =
        withContext(io) {
            val tagIds = resolveTagIds(query.tags.toSet())
            val keywordIds = keywordIdsOf(query.keywords)
            val placeIds = placeIdsOf(query)
            val since = query.dateFrom ?: "0000-00-00"
            val until = query.dateTo ?: "9999-99-99"
            val upToMonthBound = boundOf(upToMonth)
            val rows = if (after == null) {
                db.searchDao().queryFirst(since, until, upToMonthBound, placeIds, tagIds, keywordIds, limit)
            } else {
                db.searchDao().queryAfter(
                    since, until, upToMonthBound, placeIds, tagIds, keywordIds,
                    after.relevance, after.eventDate, after.id, limit,
                )
            }
            val next = if (rows.size < limit) null else rows.last().let { SearchCursor(it.relevance, it.eventDate, it.id) }
            SearchPage(rows.map { it.toRow() }, next)
        }

    override suspend fun searchByQueryCount(query: ParsedQuery, upToMonth: String?): Int = withContext(io) {
        val placeIds = placeIdsOf(query)
        db.searchDao().queryCount(
            query.dateFrom ?: "0000-00-00",
            query.dateTo ?: "9999-99-99",
            boundOf(upToMonth),
            placeIds,
            resolveTagIds(query.tags.toSet()),
            keywordIdsOf(query.keywords),
            RESULT_COUNT_CAP + 1,
        )
    }

    /**
     * ≥3 字走 FTS trigram，<3 字退回 LIKE(規格第八節)；多個關鍵字併集，任一個命中就算(OR)。
     *
     * **併出來的 id 集合會被塞進 `queryFirst`／`queryAfter`／`queryCount` 的 `IN (:keywordIds)`
     * 兩次(CASE 一次、WHERE 一次)**——SQLite 的 bind 變數上限大約 32766 個，單一關鍵字理論上
     * 有機會撞到(例如極短、極常見的字，LIKE 掃出全庫一大半)。以這個 app 的定位(單人用的個人相簿，
     * 規格目標規模 100 萬張；這個上限會撞到，已記為描述查詢的已知問題（階段 16 暫緩）。
     * 真的要解可能要改成暫存表或分批查詢。
     */
    private suspend fun keywordIdsOf(keywords: List<String>): List<Long> =
        keywords.flatMap { kw ->
            if (kw.length >= 3) db.searchDao().matchIds(kw.toFtsPhrase()) else db.searchDao().likeIds(kw)
        }.distinct()

    /**
     * 把使用者的自由文字包成 FTS5 的「片語」字面值，避免 `"`、`(`、`)`、`:`、`*` 這些
     * FTS5 查詢語法的保留字元被誤判成運算子而丟 `fts5: syntax error`。
     * 關鍵字來自 `RuleBasedParser.parse` 剝除詞彙表命中後剩下的任意文字，不能假設它是乾淨的。
     * 規則：整段包上雙引號，字串內既有的雙引號雙寫跳脫(SQLite 對帶引號字面值的標準跳脫)。
     */
    private fun String.toFtsPhrase(): String = "\"" + this.replace("\"", "\"\"") + "\""

    override suspend fun tagsOfShot(shotId: Long): List<String> = withContext(io) {
        db.tagDao().namesOfShot(shotId)
    }

    override suspend fun monthCounts(): List<MonthCount> = withContext(io) {
        db.statsDao().monthCounts().map { MonthCount(it.month, it.count) }
    }

    override suspend fun shotsOfVideo(videoId: String): List<ShotRow> = withContext(io) {
        db.shotDao().ofVideo(videoId).map { it.toRow() }
    }

    override suspend fun shotById(id: Long): ShotRow? = withContext(io) {
        db.shotDao().rowById(id)?.toRow()
    }

    override suspend fun videoById(videoId: String): VideoEntity? = withContext(io) {
        db.videoDao().byId(videoId)
    }

    override suspend fun shotImage(shotId: Long): ByteArray? = withContext(io) {
        db.shotDao().imageOf(shotId)?.webp
    }

    override suspend fun recentVideos(limit: Int): List<RecentVideo> = withContext(io) {
        db.videoDao().recent(limit).map { RecentVideo(it.videoId, it.title, it.addedAt, it.shotCount) }
    }

    override suspend fun storyboardShots(): List<ShotRow> = withContext(io) {
        db.shotDao().allStoryboardShots().map { it.toRow() }
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
                        placeId = placeIdForWrite(pick.place),
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

    override suspend fun distinctPlaces(): List<String> = withContext(io) { db.placeDao().usedNames() }

    override suspend fun allTagNames(): List<String> = withContext(io) { db.tagDao().allNames() }

    override suspend fun queryVocabulary(): QueryVocabulary = withContext(io) {
        val places = db.placeDao().usedNames()
        val tags = db.tagDao().allWithAliases().map { TagAlias(it.name, decodeAliases(it.aliases)) }
        QueryVocabulary(places, tags)
    }

    /** 標籤名轉 id；查不到的名字略過（同 `TagDao.idsByNames` 的 KDoc：查詢不會新建標籤）。 */
    private suspend fun resolveTagIds(names: Set<String>): List<Long> =
        if (names.isEmpty()) emptyList() else db.tagDao().idsByNames(names.toList())

    /**
     * 地點名稱轉 id，**不存在就建**（寫入路徑用；呼叫端必須在寫入交易內，理由同標籤：
     * 整批回滾時新地點要跟著消失）。空白＝沒有地點。
     */
    private suspend fun placeIdForWrite(name: String?): Long? {
        val trimmed = name?.ifBlank { null } ?: return null
        return db.placeDao().byName(trimmed)?.id ?: db.placeDao().insert(PlaceEntity(0, trimmed, "[]"))
    }

    /** 地點名稱轉 id；查不到的略過（查詢不會新建地點）。 */
    private suspend fun resolvePlaceIds(names: Collection<String>): List<Long> =
        if (names.isEmpty()) emptyList() else db.placeDao().idsByNames(names.distinct())

    /**
     * 描述查詢的地點條件：解析出來的地點名稱，加上「名稱包含關鍵字」的地點。
     * 地點不在全文索引裡（規格第八節），關鍵字靠這裡比對地點名稱；命中的算地點層（相關度 2）。
     */
    private suspend fun placeIdsOf(query: ParsedQuery): List<Long> =
        (resolvePlaceIds(query.places) + query.keywords.flatMap { db.placeDao().idsNameContains(it) }).distinct()

    override suspend fun patchShots(ids: List<Long>, patch: ShotPatch): Unit = withContext(io) {
        require(patch.tagIds == null || patch.tagNames == null) {
            "tagIds 與 tagNames 只能給一個 —— 兩個都給的話，誰蓋誰要看實作順序"
        }
        db.inWriteTransaction {
            // 名稱→id 的解析在交易內（理由同 commitPicks：回滾後不留空標籤）
            val tagIds = patch.tagNames?.map { name ->
                db.tagDao().byName(name)?.id
                    ?: db.tagDao().insert(TagEntity(id = 0, name = name, kind = "other", aliases = "[]"))
            } ?: patch.tagIds
            // 名稱→id 的解析在交易內（理由同標籤）；null＝沒動過，空字串＝清空
            val placeId = patch.place?.let { placeIdForWrite(it) }
            for (id in ids) {
                // event_date 是 TEXT NOT NULL，不能像 place／description 存 null 來清空 ——
                // 空白在這一欄沒有「清空」的意義，所以直接不寫，維持原值（規格第四節、ShotPatch KDoc）
                patch.eventDate?.ifBlank { null }?.let { db.shotDao().updateEventDate(id, it) }
                // 空字串＝清空（與 :core 的 DetailsPatch 同一個約定）
                if (patch.place != null) db.shotDao().updatePlace(id, placeId)
                patch.description?.let { db.shotDao().updateDescription(id, it.ifBlank { null }) }
                tagIds?.let { resolved ->
                    db.tagDao().unlinkAllOfShot(id)
                    resolved.forEach { db.tagDao().link(ShotTagEntity(id, it, "human")) }
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

    override suspend fun renameFolder(id: Long, name: String): Unit = withContext(io) {
        require(name.isNotBlank()) { "資料夾名稱不可空白" }
        require(name.length <= 50) { "資料夾名稱上限 50 字，收到 ${name.length} 字" }
        db.inWriteTransaction {
            val parentId = db.folderDao().parentOf(id)
            require(db.folderDao().countSameNameInLayerExcept(parentId, name, id) == 0) {
                "同一層已經有「$name」了"
            }
            db.folderDao().rename(id, name)
        }
        onChanged()
    }

    override suspend fun deleteFolder(id: Long): Unit = withContext(io) {
        // 子資料夾與 shot_folder 都是 ON DELETE CASCADE，刪這一列就夠；shot 不在連動範圍內
        db.folderDao().deleteById(id)
        onChanged()
    }

    override suspend fun folderNode(id: Long): FolderNode? = withContext(io) {
        db.folderDao().nodeById(id)?.let { FolderNode(it.id, it.parentId, it.name, it.depth) }
    }

    override suspend fun folderTree(): List<FolderNode> = withContext(io) {
        db.folderDao().tree().map { FolderNode(it.id, it.parentId, it.name, it.depth) }
    }

    override suspend fun folderCards(parentId: Long?): List<FolderCard> = withContext(io) {
        val cards = db.folderDao().cardsIn(parentId)
        val previews = db.folderDao().previewsIn(parentId).groupBy { it.cardId }
        cards.map { card ->
            FolderCard(
                id = card.id,
                name = card.name,
                shotCount = card.shotCount,
                // 一張圖都沒有的資料夾用建立時間當「最近活動」，「最近加入」排序才排得出先後
                lastActivityAt = card.lastAddedAt ?: card.createdAt,
                preview = previews[card.id].orEmpty().map { it.shot.toRow() },
            )
        }
    }

    override suspend fun folderShots(folderId: Long, after: FolderCursor?, limit: Int): FolderPage =
        withContext(io) {
            val rows = if (after == null) {
                db.folderDao().shotsFirst(folderId, limit)
            } else {
                db.folderDao().shotsAfter(folderId, after.addedAt, after.shotId, limit)
            }
            // 撈滿才可能有下一頁；游標的 added_at 要回查，它不在 shot 的欄位裡
            val next = if (rows.size < limit) {
                null
            } else {
                rows.last().let { last ->
                    db.folderDao().addedAtOf(folderId, last.id)?.let { FolderCursor(it, last.id) }
                }
            }
            FolderPage(rows.map { it.toRow() }, next)
        }

    override suspend fun folderShotCount(folderId: Long): Int = withContext(io) {
        db.folderDao().shotCountIn(folderId)
    }

    override suspend fun foldersOf(shotId: Long): Set<Long> = withContext(io) {
        db.folderDao().folderIdsOf(shotId).toSet()
    }

    override suspend fun addShotToFolder(shotId: Long, folderId: Long, atSec: Long): Unit = withContext(io) {
        db.folderDao().link(ShotFolderEntity(shotId, folderId, atSec))
        onChanged()
    }

    override suspend fun removeShotFromFolder(shotId: Long, folderId: Long): Unit = withContext(io) {
        db.folderDao().unlink(shotId, folderId)
        onChanged()
    }

    override suspend fun accountStats(thisMonth: String): AccountStats = withContext(io) {
        AccountStats(
            totalShots = db.statsDao().totalShots(),
            thisMonthShots = db.statsDao().shotsInMonth(thisMonth),
            distinctVideos = db.videoDao().count(),
        )
    }

    override suspend fun allTagsWithUsage(): List<TagUsage> = withContext(io) {
        db.statsDao().tagsWithUsage().map { TagUsage(it.id, it.name, it.kind, decodeAliases(it.aliases), it.shotCount) }
    }

    override suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>): Unit =
        withContext(io) {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "標籤名稱不能是空的" }
            db.inWriteTransaction {
                val existing = db.tagDao().byName(trimmed)
                if (existing != null && existing.id != id) {
                    db.tagDao().reassignLinks(id, existing.id)
                    db.tagDao().deleteById(id)
                } else {
                    db.tagDao().update(id, trimmed, kind, encodeAliases(aliases))
                }
            }
            onChanged()
        }

    override suspend fun deleteTag(id: Long): Unit = withContext(io) {
        db.tagDao().deleteById(id)
        onChanged()
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

private fun SearchHitProjection.toRow() = ShotRow(
    id = id, videoId = videoId, atSec = atSec, source = source, frameIndex = frameIndex,
    sbLevel = sbLevel, eventDate = eventDate, place = place, description = description,
)

/** `tag.aliases` 是 JSON 陣列字串（規格第四節）；解不出來（不該發生，但寫壞的資料不該讓查詢整個炸掉）就當沒有別名。 */
private fun decodeAliases(json: String): List<String> =
    runCatching { Json.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())

/** [decodeAliases] 的反向：標籤管理頁存別名時用。 */
private fun encodeAliases(aliases: List<String>): String = Json.encodeToString(aliases)

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
