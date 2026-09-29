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
 * **同一支影片的 storyboard shot 可能橫跨多個 `sbLevel`**——`Storyboard.pickLevel` 對同一份
 * spec 確實是決定性的（見 `AppContainer.frameSourceFor` 的既有註解），但 spec 本身會隨時間
 * 改變（這正是回填這個功能存在的理由）：同一支影片分兩次取圖、中間 YouTube 換過 storyboard
 * spec 的話，兩批 shot 的 `sbLevel` 就會不一樣。既有的
 * `LibraryRepo.takenFrameIndexes(videoId, level)` 帶層級參數也是同一個理由。因此 [runBatch]
 * 依 **`(videoId, sbLevel)`** 分組交給 [harvestAtLevel] 處理，但 watch page 仍然**每支影片
 * 只抓一次**——storyboard spec 跟層級無關，同一支影片的各層級群組共用同一次
 * [fetchWatchPage] 結果（全分支最終審查 Important 發現）。
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

        // 到期格子裡，檔案其實已經在 thumbs/ 的先就地標成 ok、不進下面的處理迴圈——這是
        // 全分支最終審查 Critical 發現的關鍵修法：`SheetHarvester.harvestFrames` 對「目標
        // 檔案已存在」的格子完全不回報（既不在 written 也不在 failed），交給它處理的話這格
        // 會永遠卡在 missing、每一輪都白白重抓一次 watch page，而且 `remaining` 恆為 true
        // 會讓 `BackfillWorker` 一直自我重排（無上限的迴圈）。在這裡先攔下來，後面收到的
        // entries 保證都是真的缺檔的。
        val (alreadyOnDisk, stillMissing) = due.partition {
            thumbs.exists(ThumbKey(it.videoId, it.sbLevel, it.frameIndex))
        }
        if (alreadyOnDisk.isNotEmpty()) {
            cacheRepo.putThumbStates(
                alreadyOnDisk.map { it.copy(state = "ok", attempts = 0, nextTryAt = 0L, lostReason = null) }
            )
        }
        if (stillMissing.isEmpty()) {
            // 整批都只是狀態沒對上檔案，沒有真的下載任何東西。`remaining` 照樣照實查——
            // 這一批被 batchLimit 截斷時，後面還有到期的格子在等，回報 false 會讓
            // BackfillWorker 不再接續，那些格子要等下次開機才會被處理。不會變成迴圈：
            // 這一批的每一列都已經改成 ok，不再滿足 thumbsDueForRetry 的條件。
            return@withContext BackfillRunResult.Continued(
                remaining = cacheRepo.thumbsDueForRetry(nowSec(), 1).isNotEmpty()
            )
        }

        // 依 videoId 分組抓 watch page（每支影片只抓一次），同一支影片內再依 sbLevel 分組
        // 處理——同一支影片可能因為兩次取圖之間 YouTube 換過 storyboard spec，而留下不同
        // sbLevel 的 shot（見類別 KDoc）。
        val byVideo = stillMissing.groupBy { it.videoId }
        var first = true
        for ((videoId, videoEntries) in byVideo) {
            if (!first) sleep(videoGapMs)
            first = false

            val page = fetchWatchPage(videoId)
            when (page.result) {
                FetchResult.RATE_LIMITED, FetchResult.PARSE_FAILED -> return@withContext BackfillRunResult.Paused
                FetchResult.VIDEO_UNAVAILABLE -> markLost(videoEntries, "video_unavailable")
                FetchResult.NO_STORYBOARD -> markLost(videoEntries, "no_storyboard")
                FetchResult.FETCH_FAILED -> markRetryOrGiveUp(videoEntries)
                FetchResult.OK -> {
                    val spec = Storyboard.parse(page.storyboardSpec!!)
                    if (spec == null) {
                        markRetryOrGiveUp(videoEntries)
                    } else {
                        for ((originalLevel, levelEntries) in videoEntries.groupBy { it.sbLevel }) {
                            harvestAtLevel(videoId, spec, originalLevel, levelEntries)
                        }
                    }
                }
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

    /**
     * 處理同一支影片、同一個 `sbLevel` 的一組到期格子。[spec] 由 [runBatch] 每支影片解析一次
     * 傳進來（同一支影片的各層級群組共用），[originalLevel] 是這一組 shot 當初寫入時用的層級。
     */
    private suspend fun harvestAtLevel(
        videoId: String,
        spec: StoryboardSpec,
        originalLevel: Int,
        entries: List<ThumbStateEntity>,
    ) {
        val picked = Storyboard.pickLevel(spec, preferred = originalLevel)
        if (picked == null) { markRetryOrGiveUp(entries); return }

        val result: HarvestResult
        if (picked.level == originalLevel) {
            result = harvest(videoId, spec, picked, entries.map { it.frameIndex })
        } else {
            // 原本的層級不見了：用每張 shot 的 at_sec 在新挑到的層級重新定位，
            // 裁出來的圖仍寫回原本的識別碼（originalLevel／frameIndex 不變）。
            val atSecByFrame = libraryRepo.shotsOfVideo(videoId)
                .filter { it.sbLevel == originalLevel }
                .associate { it.frameIndex to it.atSec }
            val (relocatable, unresolved) = entries.partition { atSecByFrame.containsKey(it.frameIndex) }
            val relocations = relocatable.map { e ->
                val atSec = atSecByFrame.getValue(e.frameIndex)
                RelocatedFrame(
                    targetFrameIndex = e.frameIndex,
                    sourceFrameIndex = Storyboard.frameAt(picked, atSec).frameIndex,
                )
            }
            val relocatedResult = harvestRelocated(videoId, spec, originalLevel, picked, relocations)
            // 找不到對應 shot 的孤兒列（理論上該由 ShotDeleter 清掉，但它清檔案與清 cache
            // 共用同一個 runCatching，檔案刪除失敗時 cache.forgetThumb 不會執行，孤兒列因此
            // 可能殘留）併進 failed，交給下面統一的「沒有寫成功就當失敗」邏輯處理——不要在
            // 這裡就近呼叫 markRetryOrGiveUp，那會跟下面重複處理同一批，attempts 被算兩次。
            result = relocatedResult.copy(failed = relocatedResult.failed + unresolved.map { it.frameIndex })
        }

        if (result.written.isNotEmpty()) {
            cacheRepo.putThumbStates(
                result.written.map { ThumbStateEntity(it.videoId, it.level, it.frameIndex, "ok", 0, 0L, null) }
            )
        }
        // 沒有成功寫入的格子一律走既有的退避／放棄機制：harvest 明確回報 failed 的，以及**既
        // 不在 written 也不在 failed** 的（`SheetHarvester.harvestFrames` 對目標檔案已存在的
        // 格子整個跳過回報）。後者不能悄悄放著不管，否則會永遠卡在 missing、attempts 永遠不
        // 增加、每一輪 runBatch() 都白白重抓一次 watch page（全分支最終審查 Critical 發現）。
        // 用「不在 written」而不是「不在 written∪failed」來篩：兩種情況要走的處理完全相同，
        // 合成一次呼叫既不會漏掉 failed、也不會對同一列重複增加 attempts。
        val writtenFrameIndexes = result.written.map { it.frameIndex }.toSet()
        val notWritten = entries.filterNot { it.frameIndex in writtenFrameIndexes }
        if (notWritten.isNotEmpty()) markRetryOrGiveUp(notWritten)
    }
}
