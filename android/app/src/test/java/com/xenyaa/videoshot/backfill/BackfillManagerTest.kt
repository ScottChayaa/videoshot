package com.xenyaa.videoshot.backfill

import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.HarvestResult
import com.xenyaa.videoshot.thumbs.RelocatedFrame
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.thumbs.ThumbSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val SPEC_STRING = "https://i.ytimg.com/sb/v1/x|48#27#100#10#10#0#d#s|320#180#9#3#3#1000#M\$M#s"
private val SPEC = Storyboard.parse(SPEC_STRING)!!
private val LEVEL1 = Storyboard.pickLevel(SPEC, preferred = 1)!!

/** 只實作 `exists`——這個測試不碰真的檔案。 */
private class FakeThumbs(private val existing: MutableSet<ThumbKey> = mutableSetOf()) : Thumbs {
    override fun fileOf(key: ThumbKey) = error("不會用到")
    override fun exists(key: ThumbKey): Boolean = key in existing
    override suspend fun thumbFor(shot: ShotRow): ThumbSource = error("不會用到")
    override suspend fun delete(key: ThumbKey) { existing.remove(key) }
    override suspend fun deleteVideo(videoId: String) { existing.removeAll { it.videoId == videoId } }
}

class BackfillManagerTest {

    private lateinit var library: FakeLibraryRepo
    private lateinit var cache: FakeCacheRepo
    private lateinit var thumbs: FakeThumbs
    private var now = 1_000_000L
    private var watchPageAnswer: suspend (String) -> WatchPage = { WatchPage(FetchResult.FETCH_FAILED, null, null) }
    private var harvestAnswer: HarvestResult = HarvestResult(emptyList(), emptyList(), false)

    private fun manager(videoGapMs: Long = 0L) = BackfillManager(
        libraryRepo = library,
        cacheRepo = cache,
        shotDeleter = ShotDeleter(library, thumbs, cache, Dispatchers.IO),
        thumbs = thumbs,
        fetchWatchPage = { videoId -> watchPageAnswer(videoId) },
        harvest = { _, _, _, _ -> harvestAnswer },
        harvestRelocated = { _, _, _, _, _ -> harvestAnswer },
        io = Dispatchers.IO,
        nowSec = { now },
        sleep = { },
        videoGapMs = videoGapMs,
    )

    private fun shotRow(id: Long, videoId: String, frameIndex: Int, sbLevel: Int, atSec: Double = 0.0) = ShotRow(
        id = id, videoId = videoId, atSec = atSec, source = "storyboard",
        frameIndex = frameIndex, sbLevel = sbLevel, eventDate = "2026-01-01", place = null, description = null,
    )

    @Test
    fun 掃描把缺檔的格子標成missing_已追蹤的不覆蓋() = runTest {
        library = object : FakeLibraryRepo() {
            override suspend fun storyboardShots() = listOf(shotRow(1, "v1", 0, 3), shotRow(2, "v1", 1, 3))
        }
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 1, "lost", 6, 0L, "video_unavailable")))
        thumbs = FakeThumbs() // 兩格都不存在檔案

        manager().scanForMissing()

        assertEquals("missing", cache.thumbState("v1", 3, 0)!!.state)
        assertEquals("lost", cache.thumbState("v1", 3, 1)!!.state) // 已追蹤的沒被掃描蓋掉
    }

    @Test
    fun 影片不存在或沒storyboard直接轉lost() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 0, 0L, null)))
        thumbs = FakeThumbs()
        watchPageAnswer = { WatchPage(FetchResult.VIDEO_UNAVAILABLE, null, null) }

        manager().runBatch()

        val state = cache.thumbState("v1", 3, 0)!!
        assertEquals("lost", state.state)
        assertEquals("video_unavailable", state.lostReason)
    }

    @Test
    fun 網路錯誤增加次數並排入退避() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 2, 0L, null)))
        thumbs = FakeThumbs()
        watchPageAnswer = { WatchPage(FetchResult.FETCH_FAILED, null, null) }

        manager().runBatch()

        val state = cache.thumbState("v1", 3, 0)!!
        assertEquals("missing", state.state)
        assertEquals(3, state.attempts)
        assertEquals(now + 4 * 86_400L, state.nextTryAt) // attempts=3 → 4 天
    }

    @Test
    fun 網路錯誤超過門檻轉lost() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 5, 0L, null))) // 這次是第 6 次
        thumbs = FakeThumbs()
        watchPageAnswer = { WatchPage(FetchResult.FETCH_FAILED, null, null) }

        manager().runBatch()

        val state = cache.thumbState("v1", 3, 0)!!
        assertEquals("lost", state.state)
        assertEquals("retries_exhausted", state.lostReason)
    }

    @Test
    fun 遇到429整批暫停不繼續處理下一支影片() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(
            listOf(
                ThumbStateEntity("v1", 3, 0, "missing", 0, 0L, null),
                ThumbStateEntity("v2", 3, 0, "missing", 0, 0L, null),
            )
        )
        thumbs = FakeThumbs()
        var calls = 0
        watchPageAnswer = { calls++; WatchPage(FetchResult.RATE_LIMITED, null, null) }

        val result = manager().runBatch()

        assertEquals(BackfillRunResult.Paused, result)
        assertEquals(1, calls) // 只打了第一支就暫停，沒有繼續打第二支
        // 兩支影片的 attempts 都不變 —— 不是它們的錯
        assertEquals(0, cache.thumbState("v1", 3, 0)!!.attempts)
        assertEquals(0, cache.thumbState("v2", 3, 0)!!.attempts)
    }

    @Test
    fun 頁面結構解析失敗也視同暫停() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 0, 0L, null)))
        thumbs = FakeThumbs()
        watchPageAnswer = { WatchPage(FetchResult.PARSE_FAILED, null, null) }

        assertEquals(BackfillRunResult.Paused, manager().runBatch())
    }

    @Test
    fun 抓得到就照harvest結果標成ok並清掉失敗計數() = runTest {
        library = object : FakeLibraryRepo() {
            override suspend fun shotsOfVideo(videoId: String) = listOf(shotRow(1, "v1", 0, 1))
        }
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 1, 0, "missing", 2, 0L, null)))
        thumbs = FakeThumbs()
        watchPageAnswer = { WatchPage(FetchResult.OK, VideoMeta("v1", "t", "c", "2026-01-01T00:00:00Z", 60, "public", true), SPEC_STRING) }
        harvestAnswer = HarvestResult(written = listOf(ThumbKey("v1", 1, 0)), failed = emptyList(), degradedToCover = false)

        manager().runBatch()

        val state = cache.thumbState("v1", 1, 0)!!
        assertEquals("ok", state.state)
        assertEquals(0, state.attempts)
    }

    @Test
    fun 沒有到期的格子就是閒置() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 0, now + 999, null)))
        thumbs = FakeThumbs()

        assertEquals(BackfillRunResult.Idle, manager().runBatch())
    }

    @Test
    fun 進度是ok與missing的計數lost另外算() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(
            listOf(
                ThumbStateEntity("v1", 3, 0, "ok", 0, 0L, null),
                ThumbStateEntity("v1", 3, 1, "missing", 0, 0L, null),
                ThumbStateEntity("v1", 3, 2, "lost", 6, 0L, "retries_exhausted"),
            )
        )
        thumbs = FakeThumbs()

        val progress = manager().progress()

        assertEquals(1, progress.done)
        assertEquals(2, progress.total) // ok(1) + missing(1)，不含 lost
        assertEquals(1, progress.lostCount)
    }

    @Test
    fun 稍後重試把lost重設成missing() = runTest {
        library = FakeLibraryRepo()
        cache = FakeCacheRepo()
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "lost", 6, 0L, "video_unavailable")))
        thumbs = FakeThumbs()

        manager().retryLost()

        assertEquals("missing", cache.thumbState("v1", 3, 0)!!.state)
    }

    @Test
    fun 刪除這些收藏只刪lost對應的那幾張shot() = runTest {
        var deletedIds = mutableListOf<Long>()
        library = object : FakeLibraryRepo() {
            override suspend fun shotsOfVideo(videoId: String) =
                if (videoId == "v1") listOf(shotRow(1, "v1", 0, 3), shotRow(2, "v1", 1, 3)) else emptyList()
            override suspend fun shotById(id: Long) = if (id == 1L) shotRow(1, "v1", 0, 3) else null
            override suspend fun deleteShot(id: Long) { deletedIds += id }
        }
        cache = FakeCacheRepo()
        cache.putThumbStates(
            listOf(
                ThumbStateEntity("v1", 3, 0, "lost", 6, 0L, "video_unavailable"),
                ThumbStateEntity("v1", 3, 1, "ok", 0, 0L, null), // 同一支影片,這格沒事不該被刪
            )
        )
        thumbs = FakeThumbs()

        manager().deleteLostShots()

        assertEquals(listOf(1L), deletedIds)
    }

    @Test
    fun 重新定位找不到對應shot的格子照樣走退避而不是卡住() = runTest {
        library = object : FakeLibraryRepo() {
            override suspend fun shotsOfVideo(videoId: String): List<ShotRow> = emptyList()  // 孤兒：沒有對應的shot
        }
        cache = FakeCacheRepo()
        // 用一個不存在於SPEC的sbLevel（9），強制觸發relocation分支
        // （因為pickLevel會從available levels回落到不同的level）
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 9, 0, "missing", 0, 0L, null)))
        thumbs = FakeThumbs()
        watchPageAnswer = { WatchPage(FetchResult.OK, VideoMeta("v1", "t", "c", "2026-01-01T00:00:00Z", 60, "public", true), SPEC_STRING) }
        harvestAnswer = HarvestResult(written = emptyList(), failed = emptyList(), degradedToCover = false)

        manager().runBatch()

        // 修復前：entry 會被 mapNotNull 悄悄丟掉，attempts 永遠不會增加（卡在 missing）
        // 修復後：entry 應該被送進 markRetryOrGiveUp，attempts 應該增加到 1
        val state = cache.thumbState("v1", 9, 0)!!
        assertEquals(1, state.attempts)
    }
}
