package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.capture.CaptureResult
import com.xenyaa.videoshot.capture.FakeCapture
import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.player.FakePlayer
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.io.File
import java.nio.file.Files

/**
 * 【截圖】的流程規則（規格第五節的流程圖）。不碰 WebView、不碰真的截圖 ——
 * 這裡驗的是「暫停 → 截 → 插進牆上 → 續播」以及每一種失敗的處置。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WizardCaptureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeData : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(
            FetchResult.OK,
            VideoMeta(videoId, "t", "c", "2025-01-01T00:00:00Z", 100, "public", true),
            "spec",
        )
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
        override suspend fun distinctPlaces() = emptyList<String>()
        override suspend fun allTagNames() = emptyList<String>()
        override suspend fun cropThumbs(
            videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit,
        ) = CropOutcome(frameIndexes, emptyList())
        override suspend fun commit(
            video: com.xenyaa.videoshot.data.library.entity.VideoEntity,
            picks: List<com.xenyaa.videoshot.data.repo.model.NewShot>,
        ) = picks.indices.map { it.toLong() }
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) = Unit
        override suspend fun currentDraft(): String? = null
        override suspend fun clearDraft(videoId: String) = Unit
    }

    private fun tempDir(): File = Files.createTempDirectory("manual").toFile()

    /**
     * 建好一個**已經停在第二步、播放器也接上了**的 ViewModel。
     *
     * 順序照真實流程：`submit` → 進第二步 → `PlayerSurface` 建好 WebView →
     * `onPlayerReady` → `attachPlayer`。反過來的話 `openStep2` 會把 player 清掉
     * （那是階段 4b 刻意的保險，不是 bug）。
     */
    private fun TestScope.readyViewModel(
        player: FakePlayer = FakePlayer(),
        capture: FakeCapture = FakeCapture(),
        manualDir: File = tempDir(),
        frameCount: Int = 4,
    ): WizardViewModel {
        val vm = WizardViewModel(
            data = FakeData(),
            frameSourceFactory = { FakeFrameSource.of(frameCount = frameCount) },
            strength = flowOf(FilterStrength.MEDIUM),
            hintSeen = flowOf(true),
            onHintSeen = {},
            manualImages = { ManualImageStore(manualDir) },
            captureFor = { capture },
        )
        vm.submit("aqz-KE-bpKQ")
        advanceUntilIdle()
        vm.attachPlayer(player)
        return vm
    }

    @Test
    fun 截圖成功會先暫停再續播() = runTest(dispatcher) {
        // 規格第五節的流程圖：暫停 → 截圖＋讀秒 → 續播。
        // 不先暫停的話，圖與秒數會差上幾十毫秒
        val player = FakePlayer()
        val vm = readyViewModel(player = player, capture = FakeCapture(atSec = 30.0))
        vm.takeShot()
        advanceUntilIdle()
        assertTrue("最後要繼續播", player.playing)
        assertTrue("應該暫停過", player.pauses > 0)
    }

    @Test
    fun 截圖成功會新增一個已勾選的手動格() = runTest(dispatcher) {
        val vm = readyViewModel(capture = FakeCapture(atSec = 30.0))
        vm.takeShot()
        advanceUntilIdle()
        val state = vm.step2.value!!.state.value
        assertEquals(1, state.manual.size)
        assertEquals(30.0, state.manual.first().atSec, 0.0)
        assertTrue(state.selected.contains(state.manual.first().cellIndex))
    }

    @Test
    fun 黑畫面不新增格子而且回報錯誤() = runTest(dispatcher) {
        val vm = readyViewModel(capture = FakeCapture().apply { nextResult = CaptureResult.BlackFrame })
        vm.takeShot()
        advanceUntilIdle()
        assertEquals(0, vm.step2.value!!.state.value.manual.size)
        assertEquals(CaptureError.BLACK_FRAME, vm.captureError.value)
    }

    @Test
    fun 廣告播放中回報AD_PLAYING() = runTest(dispatcher) {
        val vm = readyViewModel(capture = FakeCapture().apply { nextResult = CaptureResult.AdPlaying })
        vm.takeShot()
        advanceUntilIdle()
        assertEquals(CaptureError.AD_PLAYING, vm.captureError.value)
    }

    @Test
    fun 失敗之後播放器仍然要繼續播() = runTest(dispatcher) {
        // 截不到不是把播放器停在那裡不動的理由
        val player = FakePlayer()
        val vm = readyViewModel(player = player, capture = FakeCapture().apply { nextResult = CaptureResult.BlackFrame })
        vm.takeShot()
        advanceUntilIdle()
        assertTrue(player.playing)
    }

    @Test
    fun 錯誤可以清掉() = runTest(dispatcher) {
        val vm = readyViewModel(capture = FakeCapture().apply { nextResult = CaptureResult.BlackFrame })
        vm.takeShot()
        advanceUntilIdle()
        vm.dismissCaptureError()
        assertNull(vm.captureError.value)
    }

    @Test
    fun 存檔失敗也要回報而不是當機() = runTest(dispatcher) {
        // 儲存空間滿、目錄被佔住 —— 功能降級，絕不當機（規格第三節設計原則第 6 條）。
        // 用一個「同名檔案佔住目錄位置」的路徑來模擬 mkdirs 失敗
        val blocked = tempDir().resolve("blocked")
        blocked.writeText("我是檔案不是目錄")
        val vm = readyViewModel(manualDir = blocked.resolve("manual"))
        vm.takeShot()
        advanceUntilIdle()
        assertEquals(CaptureError.SAVE_FAILED, vm.captureError.value)
        assertEquals(0, vm.step2.value!!.state.value.manual.size)
    }

    @Test
    fun 成功截圖會把上一次的錯誤清掉() = runTest(dispatcher) {
        val capture = FakeCapture(atSec = 5.0).apply { nextResult = CaptureResult.BlackFrame }
        val vm = readyViewModel(capture = capture)
        vm.takeShot()
        advanceUntilIdle()
        assertEquals(CaptureError.BLACK_FRAME, vm.captureError.value)
        capture.nextResult = null
        vm.takeShot()
        advanceUntilIdle()
        assertNull("成功之後舊的錯誤訊息不該還留在畫面上", vm.captureError.value)
    }

    @Test
    fun 換一支影片之後舊的截圖器不會留著() = runTest(dispatcher) {
        // capture 握著**那一個** WebView 的 eval 入口，跟 player 是同一個生命週期。
        // 留著的話換了影片再按【截圖】，會對已經 destroy 的 WebView 下指令
        val vm = readyViewModel(capture = FakeCapture(atSec = 5.0))
        vm.takeShot()
        advanceUntilIdle()
        assertEquals(1, vm.step2.value!!.state.value.manual.size)

        vm.submit("dQw4w9WgXcQ")
        advanceUntilIdle()
        vm.takeShot()
        advanceUntilIdle()
        assertEquals(CaptureError.NOT_READY, vm.captureError.value)
        assertEquals(0, vm.step2.value!!.state.value.manual.size)
    }

    // ---- 相簿選圖（任務 9）----

    @Test
    fun 相簿選的圖以播放器當下秒數插進來() = runTest(dispatcher) {
        val vm = readyViewModel(player = FakePlayer(initialTime = 77.0))
        vm.addFromGallery(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        val manual = vm.step2.value!!.state.value.manual
        assertEquals(1, manual.size)
        assertEquals(77.0, manual.first().atSec, 0.0)
    }

    @Test
    fun 相簿的圖可以微調秒數() = runTest(dispatcher) {
        // 秒數不是從圖來的，只是「使用者當下停在哪」的近似值，所以**必須**可調
        val vm = readyViewModel(player = FakePlayer(initialTime = 77.0))
        vm.addFromGallery(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        val cell = vm.step2.value!!.state.value.manual.first().cellIndex
        vm.nudgeManual(cell, 1.0)
        assertEquals(78.0, vm.step2.value!!.state.value.atSecOf(cell), 0.0)
    }

    @Test
    fun 微調不會把秒數推到負數() = runTest(dispatcher) {
        val vm = readyViewModel(player = FakePlayer(initialTime = 0.5))
        vm.addFromGallery(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        val cell = vm.step2.value!!.state.value.manual.first().cellIndex
        vm.nudgeManual(cell, -1.0)
        assertEquals(0.0, vm.step2.value!!.state.value.atSecOf(cell), 0.0)
    }

    @Test
    fun 微調之後牆上的位置跟著變() = runTest(dispatcher) {
        // 10 秒一格的牆上，5 秒的圖排在第 1 格之後；調到 15 秒要跳到第 2 格之後
        val vm = readyViewModel(player = FakePlayer(initialTime = 5.0), frameCount = 4)
        vm.addFromGallery(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        val store = vm.step2.value!!
        store.setShowAll(true)
        val cell = store.state.value.manual.first().cellIndex
        assertEquals(1, store.state.value.visible.indexOf(cell))
        vm.nudgeManual(cell, 10.0)
        assertEquals(2, store.state.value.visible.indexOf(cell))
    }

    @Test
    fun 相簿的圖存不進去時回報而不是當機() = runTest(dispatcher) {
        val blocked = tempDir().resolve("blocked")
        blocked.writeText("我是檔案不是目錄")
        val vm = readyViewModel(manualDir = blocked.resolve("manual"))
        vm.addFromGallery(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        assertEquals(CaptureError.SAVE_FAILED, vm.captureError.value)
    }

    // ---- 哪些格子的時間可以調（規格第五節、手冊第 93 行）----

    @Test
    fun 截圖來的格子不可微調() {
        // 圖與秒數是同一瞬間取的，調了就對不上 —— 這是規格明訂的
        runTest(dispatcher) {
            val vm = readyViewModel(capture = FakeCapture(atSec = 30.0))
            vm.takeShot()
            advanceUntilIdle()
            assertFalse(vm.step2.value!!.state.value.manual.first().fromGallery)
        }
    }

    @Test
    fun 相簿來的格子可微調() {
        runTest(dispatcher) {
            val vm = readyViewModel(player = FakePlayer(initialTime = 10.0))
            vm.addFromGallery(byteArrayOf(1))
            advanceUntilIdle()
            assertTrue(vm.step2.value!!.state.value.manual.first().fromGallery)
        }
    }

    @Test
    fun 截圖來的格子就算被要求微調也不動() {
        // 畫面上不該給它微調鈕，但狀態機自己也要守住這條規則
        runTest(dispatcher) {
            val vm = readyViewModel(capture = FakeCapture(atSec = 30.0))
            vm.takeShot()
            advanceUntilIdle()
            val cell = vm.step2.value!!.state.value.manual.first().cellIndex
            vm.nudgeManual(cell, 1.0)
            assertEquals(30.0, vm.step2.value!!.state.value.atSecOf(cell), 0.0)
        }
    }
}
