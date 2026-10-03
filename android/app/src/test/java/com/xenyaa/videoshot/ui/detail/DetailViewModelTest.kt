package com.xenyaa.videoshot.ui.detail

import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.FakePlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun shot(id: Long, atSec: Double, place: String? = null, desc: String? = null) = ShotRow(
        id = id, videoId = "v1", atSec = atSec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
        eventDate = "2026-03-01", place = place, description = desc,
    )

    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var shots: List<ShotRow> = emptyList()
        var video: VideoEntity? = VideoEntity("v1", "旅行影片", "c", "2026-03-01T00:00:00Z", 600, "public", null, 1L)
        var tagsById: Map<Long, List<String>> = emptyMap()
        var throwOnShots = false
        var usage: List<com.xenyaa.videoshot.data.repo.model.TagUsage> = emptyList()
        var throwOnUsage = false

        /** 非 null 時 `tagsOfShot` 會卡到它完成為止——用來製造「讀標籤期間狀態被別處改過」的競態。 */
        var tagsGate: CompletableDeferred<Unit>? = null

        /** 非 null 時 `shotsOfVideo` 會卡到它完成為止——實機上 DB 讀取在 IO 執行緒上真的會暫停。 */
        var shotsGate: CompletableDeferred<Unit>? = null

        override suspend fun shotsOfVideo(videoId: String): List<ShotRow> {
            shotsGate?.await()
            if (throwOnShots) throw RuntimeException("模擬讀取失敗")
            return shots
        }
        override suspend fun videoById(videoId: String) = video
        override suspend fun allTagsWithUsage(): List<com.xenyaa.videoshot.data.repo.model.TagUsage> {
            if (throwOnUsage) throw RuntimeException("模擬讀取失敗")
            return usage
        }
        override suspend fun tagsOfShot(shotId: Long): List<String> {
            tagsGate?.await()
            return tagsById[shotId].orEmpty()
        }
    }

    private fun okPage(playableInEmbed: Boolean = true) = WatchPage(
        result = FetchResult.OK,
        meta = VideoMeta("v1", "旅行影片", "c", "2026-03-01T00:00:00Z", 600, "public", playableInEmbed),
        storyboardSpec = null,
    )

    @Test
    fun 讀到圖資與標題_保留一開始帶進來的聚焦張() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 20.0)) }
        val vm = DetailViewModel("v1", initialFocusShotId = 2L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals("旅行影片", s.title)
        assertEquals(2, s.shots.size)
        assertEquals(2L, s.focusedShotId)
        assertEquals(2L, s.focused?.id)
    }

    @Test
    fun 讀取失敗時surface錯誤而不是當機() = runTest(dispatcher) {
        val repo = Repo().apply { throwOnShots = true }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertEquals("讀取失敗，請再試一次", vm.state.value.error)
    }

    @Test
    fun watchPage拿得到meta時播放器就緒() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0)) }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage(playableInEmbed = false) })
        advanceUntilIdle()
        val avail = vm.state.value.player as DetailViewModel.PlayerAvailability.Ready
        assertFalse(avail.playableInEmbed)
    }

    @Test
    fun 影片抓不到時播放器不可用但頁面不整頁失敗() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0)) }
        val vm = DetailViewModel(
            "v1", initialFocusShotId = 1L, library = repo,
            watchPage = { WatchPage(FetchResult.VIDEO_UNAVAILABLE, null, null) },
        )
        advanceUntilIdle()
        val avail = vm.state.value.player as DetailViewModel.PlayerAvailability.Unavailable
        assertFalse(avail.retryable)
        // 圖資仍然在——這是規格第六節那句話的斷言重點
        assertEquals(1, vm.state.value.shots.size)
    }

    @Test
    fun 沒有網路時播放器可以重試() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0)) }
        val vm = DetailViewModel(
            "v1", initialFocusShotId = 1L, library = repo,
            watchPage = { WatchPage(FetchResult.FETCH_FAILED, null, null) },
        )
        advanceUntilIdle()
        val avail = vm.state.value.player as DetailViewModel.PlayerAvailability.Unavailable
        assertTrue(avail.retryable)
    }

    @Test
    fun attachPlayer後會跳到聚焦張的秒數並播放() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 40.0)) }
        val vm = DetailViewModel("v1", initialFocusShotId = 2L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()
        val player = FakePlayer()
        vm.attachPlayer(player)
        advanceUntilIdle()
        assertEquals(listOf(40.0), player.seeks)
        assertTrue(player.playing)
    }

    @Test
    fun focus換張會換聚焦並帶動播放器跳播() = runTest(dispatcher) {
        val repo = Repo().apply {
            shots = listOf(shot(1, 10.0), shot(2, 40.0))
            tagsById = mapOf(2L to listOf("露營"))
        }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()
        val player = FakePlayer()
        vm.attachPlayer(player)
        advanceUntilIdle()

        vm.focus(shot(2, 40.0))
        advanceUntilIdle()

        assertEquals(2L, vm.state.value.focusedShotId)
        assertEquals(listOf("露營"), vm.state.value.focusedTags)
        assertEquals(listOf(10.0, 40.0), player.seeks)
    }

    @Test
    fun onShotChanged只替換那一張的圖資() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0, place = "宜蘭")) }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()

        vm.onShotChanged(shot(1, 10.0, place = "台北"))
        advanceUntilIdle()

        assertEquals("台北", vm.state.value.focused?.place)
    }

    /**
     * 最終審查 Finding 2：VM 用 videoId 當 key 被 `AppRoot` 快取，同一支影片再進一次詳情頁
     * 拿到的是同一個實例——這時要靠 `reload(focusShotId = ...)` 蓋過目前聚焦，
     * 不能是「盡量保留原本聚焦」（那是 `reload()` 沒帶參數時的行為，給別處重查用）。
     */
    @Test
    fun reload帶focusShotId會蓋過目前聚焦() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 40.0)) }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()
        assertEquals(1L, vm.state.value.focusedShotId)

        vm.reload(focusShotId = 2L)
        advanceUntilIdle()

        assertEquals(2L, vm.state.value.focusedShotId)
    }

    /** 對照組：不帶參數的 `reload()`（批次編輯完成、編輯 sheet 存檔後走這條）要維持原本聚焦不變。 */
    @Test
    fun reload不帶參數會保留原本聚焦() = runTest(dispatcher) {
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 40.0)) }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()

        vm.focus(shot(2, 40.0))
        advanceUntilIdle()
        assertEquals(2L, vm.state.value.focusedShotId)

        vm.reload()
        advanceUntilIdle()

        assertEquals(2L, vm.state.value.focusedShotId)
    }

    /**
     * 2026-10-01 實機回報「詳情頁永遠停在正在載入」的回歸測試。
     * watch page 走網路，幾乎一定比 library.db 的讀取晚回來；原本寫成
     * `_state.value = _state.value.copy(player = availabilityOf(watchPage(videoId)))`——
     * Kotlin 先取 `_state.value`（那時 `loading` 還是 true）才去等網路，回來後把那份舊快照整份寫回，
     * 圖資讀取早就寫好的 `loading = false`／shots／title 全被蓋掉。
     */
    @Test
    fun 播放器資訊比圖資晚回來時不會把畫面蓋回載入中() = runTest(dispatcher) {
        val shotsGate = CompletableDeferred<Unit>()
        val repo = Repo().apply { shots = listOf(shot(1, 10.0), shot(2, 20.0)); this.shotsGate = shotsGate }
        val pageGate = CompletableDeferred<WatchPage>()
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { pageGate.await() })
        advanceUntilIdle()                    // 兩個讀取都已發出、都還沒回來
        shotsGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)   // 圖資先到

        pageGate.complete(okPage())
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals("旅行影片", s.title)
        assertEquals(2, s.shots.size)
        assertEquals(DetailViewModel.PlayerAvailability.Ready(playableInEmbed = true), s.player)
    }

    @Test
    fun 讀聚焦張標籤期間的編輯不會被讀完的標籤蓋掉() = runTest(dispatcher) {
        val repo = Repo().apply {
            shots = listOf(shot(1, 10.0), shot(2, 20.0))
            tagsById = mapOf(1L to listOf("海邊"))
        }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repo.tagsGate = gate
        vm.focus(vm.state.value.shots[0])          // 開始讀標籤，卡在 gate
        advanceUntilIdle()
        vm.onShotChanged(shot(2, 20.0, place = "台北"))   // 標籤還沒回來前，另一張被編輯
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("台北", vm.state.value.shots.first { it.id == 2L }.place)
        assertEquals(listOf("海邊"), vm.state.value.focusedTags)
    }

    private fun usage(id: Long, name: String, kind: String) =
        com.xenyaa.videoshot.data.repo.model.TagUsage(id, name, kind, emptyList(), 1)

    /** 圖資卡的標籤小膠囊要帶種類，kind 從既有的 allTagsWithUsage 對出來，不加新 SQL。 */
    @Test fun 聚焦那張的標籤帶種類() = runTest(dispatcher) {
        val repo = Repo().apply {
            shots = listOf(shot(1, 10.0))
            tagsById = mapOf(1L to listOf("夜潛", "龍蝦"))
            usage = listOf(usage(1, "夜潛", "topic"), usage(2, "龍蝦", "other"), usage(3, "別張的", "person"))
        }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()
        assertEquals(listOf("夜潛", "龍蝦"), vm.state.value.focusedTags)
        assertEquals(mapOf("夜潛" to "topic", "龍蝦" to "other"), vm.state.value.focusedTagKinds)
    }

    /** 種類只是輔助資訊：讀不到不能讓標籤本身也消失，更不能讓整頁跳錯誤。 */
    @Test fun 讀不到種類時標籤照樣顯示且不跳錯() = runTest(dispatcher) {
        val repo = Repo().apply {
            shots = listOf(shot(1, 10.0))
            tagsById = mapOf(1L to listOf("夜潛"))
            throwOnUsage = true
        }
        val vm = DetailViewModel("v1", initialFocusShotId = 1L, library = repo, watchPage = { okPage() })
        advanceUntilIdle()
        assertEquals(listOf("夜潛"), vm.state.value.focusedTags)
        assertEquals(emptyMap<String, String>(), vm.state.value.focusedTagKinds)
        assertNull(vm.state.value.error)
    }
}
