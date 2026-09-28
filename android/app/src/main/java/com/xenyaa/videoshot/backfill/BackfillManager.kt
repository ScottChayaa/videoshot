package com.xenyaa.videoshot.backfill

import com.xenyaa.videoshot.core.backfill.BackfillPolicy
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.thumbs.HarvestResult
import com.xenyaa.videoshot.thumbs.RelocatedFrame
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.Thumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class BackfillProgress(val done: Int, val total: Int, val lostCount: Int)

sealed interface BackfillRunResult {
    /** 目前沒有到期該處理的格子。 */
    data object Idle : BackfillRunResult

    /** 遇到 429 或頁面結構解析失敗（規格第十一節）：整批暫停,呼叫端該回 `Result.retry()` 交給 WorkManager 的退避。 */
    data object Paused : BackfillRunResult

    /** 這次處理了一些，[remaining] 代表還有沒有其他到期的格子在等（呼叫端據此決定要不要接著排下一個 work）。 */
    data class Continued(val remaining: Boolean) : BackfillRunResult
}

/**
 * 縮圖回填的協調者（規格第十一節）。**不直接依賴 `Youtube`／`SheetHarvester`**——只收
 * [fetchWatchPage]／[harvest]／[harvestRelocated] 三個函式，比照 `BackupManager` 收
 * `snapshotTo` 函式的既有理由：核心判斷邏輯（分類、退避、暫停、進度）因此能用一般 JVM
 * 測試＋假物件驗證，不必掛 Robolectric 或連網（真正呼叫 harvest 之後怎麼裁圖已經在
 * `SheetHarvesterTest` 驗證過）。
 *
 * **同一支影片的 storyboard shot 一律共用同一個 `sbLevel`**——`Storyboard.pickLevel` 對
 * 同一份 spec 是決定性的（見 `AppContainer.frameSourceFor` 的既有註解），取圖精靈永遠只用
 * 一個層級寫入。[harvestVideo] 因此不必逐格分層處理，直接用第一筆的 `sbLevel` 代表整支影片。
 */
class BackfillManager(
    private val libraryRepo: LibraryRepo,
    private val cacheRepo: CacheRepo,
    private val shotDeleter: ShotDeleter,
    private val thumbs: Thumbs,
    private val fetchWatchPage: suspend (videoId: String) -> WatchPage,
    private val harvest: suspend (
        videoId: String,
        spec: StoryboardSpec,
        level: StoryboardLevel,
        frameIndexes: List<Int>,
    ) -> HarvestResult,
    private val harvestRelocated: suspend (
        videoId: String,
        spec: StoryboardSpec,
        targetLevel: Int,
        sourceLevel: StoryboardLevel,
        frames: List<RelocatedFrame>,
    ) -> HarvestResult,
    private val io: CoroutineDispatcher,
    private val nowSec: () -> Long,
    /** 影片間節流的實際等待方式——注入而不是直接呼叫 `kotlinx.coroutines.delay`，測試才能設成 no-op。
     *  刻意不取名叫 `delay`：那會跟 `kotlinx.coroutines.delay` 頂層函式同名，容易讓人誤讀成自我遞迴。 */
    private val sleep: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val videoGapMs: Long = 3_000L,
    private val batchLimit: Int = 500,
) {
    /** 規格第十一節步驟 1：掃 library.db 全部 storyboard shot,缺檔的補進 thumb_state。已追蹤的不動。 */
    suspend fun scanForMissing(): Unit = withContext(io) {
        val now = nowSec()
        val toInsert = mutableListOf<ThumbStateEntity>()
        for (shot in libraryRepo.storyboardShots()) {
            val level = shot.sbLevel ?: continue
            val frameIndex = shot.frameIndex ?: continue
            if (thumbs.exists(ThumbKey(shot.videoId, level, frameIndex))) continue
            if (cacheRepo.thumbState(shot.videoId, level, frameIndex) == null) {
                toInsert += ThumbStateEntity(shot.videoId, level, frameIndex, "missing", 0, now, null)
            }
        }
        if (toInsert.isNotEmpty()) cacheRepo.putThumbStates(toInsert)
    }

    /**
     * 掃描一次、依影片分組處理一批到期的格子（規格第十一節步驟 2）。
     * 影片與影片之間依 [videoGapMs] 節流；遇到 [FetchResult.RATE_LIMITED] 或
     * [FetchResult.PARSE_FAILED] 立刻停止，不繼續處理下一支影片（規格：「整批暫停」）。
     */
    suspend fun runBatch(): BackfillRunResult = withContext(io) {
        scanForMissing()
        val due = cacheRepo.thumbsDueForRetry(nowSec(), batchLimit)
        if (due.isEmpty()) return@withContext BackfillRunResult.Idle

        val byVideo = due.groupBy { it.videoId }
        var first = true
        for ((videoId, entries) in byVideo) {
            if (!first) sleep(videoGapMs)
            first = false

            val page = fetchWatchPage(videoId)
            when (page.result) {
                FetchResult.RATE_LIMITED, FetchResult.PARSE_FAILED -> return@withContext BackfillRunResult.Paused
                FetchResult.VIDEO_UNAVAILABLE -> markLost(entries, "video_unavailable")
                FetchResult.NO_STORYBOARD -> markLost(entries, "no_storyboard")
                FetchResult.FETCH_FAILED -> markRetryOrGiveUp(entries)
                FetchResult.OK -> harvestVideo(videoId, page.storyboardSpec!!, entries)
            }
        }
        val remaining = cacheRepo.thumbsDueForRetry(nowSec(), 1).isNotEmpty()
        BackfillRunResult.Continued(remaining)
    }

    suspend fun progress(): BackfillProgress = withContext(io) {
        val ok = cacheRepo.countByState("ok")
        val missing = cacheRepo.countByState("missing")
        val lost = cacheRepo.countByState("lost")
        BackfillProgress(done = ok, total = ok + missing, lostCount = lost)
    }

    /** 【稍後重試】：全部 lost 的格子重設成 missing。 */
    suspend fun retryLost(): Unit = withContext(io) { cacheRepo.resetLostToMissing(nowSec()) }

    /**
     * 【刪除這些收藏】：只刪掉 lost 對應的那幾張 shot（不是整支影片——同一支影片可能還有
     * 其他狀態正常的格子）。反查 shotId 靠 `shotsOfVideo` 比對 `(sbLevel, frameIndex)`，
     * 刪除交給既有的 `ShotDeleter`（一次處理 library.db／thumbs/／thumb_state 三邊）。
     */
    suspend fun deleteLostShots(): Unit = withContext(io) {
        val byVideo = cacheRepo.lostThumbs().groupBy { it.videoId }
        for ((videoId, entries) in byVideo) {
            val keys = entries.map { it.sbLevel to it.frameIndex }.toSet()
            libraryRepo.shotsOfVideo(videoId)
                .filter { (it.sbLevel to it.frameIndex) in keys }
                .forEach { shotDeleter.delete(it.id) }
        }
    }

    private suspend fun markLost(entries: List<ThumbStateEntity>, reason: String) {
        cacheRepo.putThumbStates(entries.map { it.copy(state = "lost", lostReason = reason) })
    }

    private suspend fun markRetryOrGiveUp(entries: List<ThumbStateEntity>) {
        val now = nowSec()
        cacheRepo.putThumbStates(
            entries.map { e ->
                val attempts = e.attempts + 1
                if (BackfillPolicy.shouldGiveUp(attempts)) {
                    e.copy(state = "lost", attempts = attempts, lostReason = "retries_exhausted")
                } else {
                    e.copy(state = "missing", attempts = attempts, nextTryAt = BackfillPolicy.nextTryAt(now, attempts))
                }
            }
        )
    }

    private suspend fun harvestVideo(videoId: String, specString: String, entries: List<ThumbStateEntity>) {
        val spec = Storyboard.parse(specString)
        if (spec == null) { markRetryOrGiveUp(entries); return }

        val originalLevel = entries.first().sbLevel
        val picked = Storyboard.pickLevel(spec, preferred = originalLevel)
        if (picked == null) { markRetryOrGiveUp(entries); return }

        val result = if (picked.level == originalLevel) {
            harvest(videoId, spec, picked, entries.map { it.frameIndex })
        } else {
            // 原本的層級不見了：用每張 shot 的 at_sec 在新挑到的層級重新定位，
            // 裁出來的圖仍寫回原本的識別碼（originalLevel／frameIndex 不變）。
            val atSecByFrame = libraryRepo.shotsOfVideo(videoId)
                .filter { it.sbLevel == originalLevel }
                .associate { it.frameIndex to it.atSec }
            // 找不到對應 shot 的孤兒列（理論上該由 ShotDeleter 清掉，但它清檔案與清 cache
            // 共用同一個 runCatching，檔案刪除失敗時 cache.forgetThumb 不會執行，孤兒列因此
            // 可能殘留）一併送進 markRetryOrGiveUp，讓它照既有的退避／放棄機制走──
            // 不能悄悄丟掉，否則這格會永遠卡在 missing、attempts 永遠不增加、每次 runBatch()
            // 都白白重抓一次 watch page（全分支最終審查 Important 發現）。
            val (relocatable, unresolved) = entries.partition { atSecByFrame.containsKey(it.frameIndex) }
            if (unresolved.isNotEmpty()) markRetryOrGiveUp(unresolved)
            val relocations = relocatable.map { e ->
                val atSec = atSecByFrame.getValue(e.frameIndex)
                RelocatedFrame(
                    targetFrameIndex = e.frameIndex,
                    sourceFrameIndex = Storyboard.frameAt(picked, atSec).frameIndex,
                )
            }
            harvestRelocated(videoId, spec, originalLevel, picked, relocations)
        }

        if (result.written.isNotEmpty()) {
            cacheRepo.putThumbStates(
                result.written.map { ThumbStateEntity(it.videoId, it.level, it.frameIndex, "ok", 0, 0L, null) }
            )
        }
        val failedEntries = entries.filter { it.frameIndex in result.failed }
        if (failedEntries.isNotEmpty()) markRetryOrGiveUp(failedEntries)
    }
}
