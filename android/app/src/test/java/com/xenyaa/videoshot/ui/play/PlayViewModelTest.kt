package com.xenyaa.videoshot.ui.play

import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.FakePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class PlayViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun shot(id: Long, atSec: Double, source: String = "storyboard") = ShotRow(
        id = id, videoId = "v1", atSec = atSec, source = source, frameIndex = id.toInt(), sbLevel = 3,
        eventDate = "2026-03-01", place = null, description = null,
    )

    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var shots: List<ShotRow> = emptyList()
        var video: VideoEntity? = VideoEntity("v1", "旅行影片", "c", "2026-03-01T00:00:00Z", 600, "public", null, 1L)
        var throwOnShots = false
        override suspend fun shotsOfVideo(videoId: String): List<ShotRow> {
            if (throwOnShots) throw RuntimeException("模擬讀取失敗")
            return shots
        }
        override suspend fun videoById(videoId: String) = video
    }

    private fun page(result: FetchResult = FetchResult.OK, withMeta: Boolean = true) = WatchPage(
        result = result,
        meta = if (withMeta) VideoMeta("v1", "旅行影片", "c", "2026-03-01T00:00:00Z", 600, "public", true) else null,
        storyboardSpec = null,
    )

    private fun vm(repo: Repo, startShotId: Long, landscape: Boolean? = null, page: WatchPage = page()) = PlayViewModel(
        videoId = "v1",
        startShotId = startShotId,
        library = repo,
        watchPage = { page },
        landscapeOf = { landscape },
    )

    @Test
    fun 從進來那一張的秒數開始播() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 40.0)) }
        val model = vm(repo, startShotId = 2)
        advanceUntilIdle()
        val player = FakePlayer()
        model.attachPlayer(player)
        advanceUntilIdle()
        assertEquals(listOf(40.0), player.seeks)
        assertTrue(player.playing)
    }

    @Test
    fun 播放器比圖庫先好_圖庫讀完補跳一次() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 40.0)) }
        val model = vm(repo, startShotId = 2)
        val player = FakePlayer()
        model.attachPlayer(player)
        advanceUntilIdle()
        assertEquals(40.0, player.seeks.last(), 0.0)
    }

    @Test
    fun 方向照縮圖比例_讀不到當成橫的() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0)) }
        val portrait = vm(repo, startShotId = 1, landscape = false)
        val unknown = vm(repo, startShotId = 1, landscape = null)
        advanceUntilIdle()
        assertEquals(false, portrait.state.value.landscape)
        assertEquals(true, unknown.state.value.landscape)
    }

    @Test
    fun 確認框的張數含截圖() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 20.0, source = "manual"), shot(3, 30.0)) }
        val model = vm(repo, startShotId = 1)
        advanceUntilIdle()
        assertEquals(3, model.state.value.shotCount)
        assertEquals(1, model.state.value.manualCount)
        assertEquals("旅行影片", model.state.value.title)
    }

    @Test
    fun 圖庫讀不到照樣能播_從頭播當成橫的() = runTest(dispatcher) {
        val repo = Repo().apply { throwOnShots = true }
        val model = vm(repo, startShotId = 1)
        advanceUntilIdle()
        assertEquals(true, model.state.value.landscape)
        assertEquals(0.0, model.state.value.startSec, 0.0)
        assertTrue(model.state.value.player is PlayViewModel.PlayerAvailability.Ready)
    }

    @Test
    fun 沒網路可重試_影片不見了不能重試() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0)) }
        val offline = vm(repo, 1, page = page(FetchResult.FETCH_FAILED, withMeta = false))
        val gone = vm(repo, 1, page = page(FetchResult.VIDEO_UNAVAILABLE, withMeta = false))
        advanceUntilIdle()
        assertEquals(true, (offline.state.value.player as PlayViewModel.PlayerAvailability.Unavailable).retryable)
        assertEquals(false, (gone.state.value.player as PlayViewModel.PlayerAvailability.Unavailable).retryable)
    }
}
