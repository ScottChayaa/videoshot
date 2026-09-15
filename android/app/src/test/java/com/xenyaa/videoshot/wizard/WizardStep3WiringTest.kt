package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WizardStep3WiringTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val meta = VideoMeta(
        videoId = "v1", title = "宜蘭兩天一夜", channelTitle = "ch",
        publishedAt = "2025-07-12T10:00:00-07:00", durationSec = 600,
        privacy = "public", playableInEmbed = true,
    )

    /** 記下 repo 收到什麼；不碰真的資料庫（那是儀器測試的事）。 */
    private class RecordingData(
        val page: WatchPage,
    ) : WizardData {
        var committed: Pair<VideoEntity, List<NewShot>>? = null
        var thumbStatesOk: List<Int> = emptyList()
        var thumbStatesMissing: List<Int> = emptyList()
        var draftCleared = false
        var cropped: List<Int> = emptyList()

        override suspend fun watchPage(videoId: String) = page
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int) = emptySet<Int>()
        override suspend fun distinctPlaces() = listOf("冬山河")
        override suspend fun allTagNames() = listOf("玩水")
        override suspend fun cropThumbs(
            videoId: String,
            sbSpec: String?,
            frameIndexes: List<Int>,
            onProgress: (Int, Int) -> Unit,
        ): CropOutcome {
            cropped = frameIndexes
            onProgress(frameIndexes.size, frameIndexes.size)
            return CropOutcome(written = frameIndexes.filter { it < 4 }, missing = emptyList())
        }
        override suspend fun commit(video: VideoEntity, picks: List<NewShot>): List<Long> {
            committed = video to picks
            return picks.indices.map { it.toLong() + 1 }
        }
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) {
            thumbStatesOk = ok
            thumbStatesMissing = missing
        }
        override suspend fun clearDraft(videoId: String) { draftCleared = true }
        override suspend fun saveDraft(json: String) = Unit
        override suspend fun currentDraft(): String? = null
    }

    private fun vmWith(data: RecordingData) = WizardViewModel(
        data = data,
        frameSourceFactory = { FakeFrameSource.of(frameCount = 4, intervalSec = 10.0) },
        strength = flowOf(FilterStrength.MEDIUM),
        hintSeen = flowOf(true),
        onHintSeen = {},
        manualImages = { error("這個測試不碰手動圖") },
        captureFor = { null },
        today = { "2026-09-15" },
    )

    private fun pageOf() = WatchPage(FetchResult.OK, meta, storyboardSpec = null)

    @Test
    fun 進第三步會帶進第二步勾選的那幾格() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1")
        advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.step2.value!!.toggle(2)
        vm.goTo(WizardStep.DETAILS)
        advanceUntilIdle()
        assertEquals(listOf(0, 2), vm.step3.value!!.state.value.cells.map { it.cell })
    }

    @Test
    fun 時間預設帶入上傳日期() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        assertEquals("2025-07-12", vm.step3.value!!.state.value.details[0]!!.eventDate)
    }

    @Test
    fun 進場會裁勾選的格子() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(1)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        assertEquals(listOf(1), data.cropped)
        assertTrue(!vm.step3.value!!.state.value.cropping)
    }

    @Test
    fun 抽屜的既有值建議來自圖庫() = runTest(dispatcher) {
        val vm = vmWith(RecordingData(pageOf()))
        advanceUntilIdle()
        assertEquals(listOf("冬山河"), vm.suggestions.value.places)
        assertEquals(listOf("玩水"), vm.suggestions.value.tags)
    }

    @Test
    fun 還有沒填的會先提醒而不是直接寫進去() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.step2.value!!.toggle(1)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()
        assertEquals(2, vm.pendingFinish.value)
        assertNull(data.committed)
    }

    @Test
    fun 提醒之後仍然可以完成_提醒不阻擋() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()
        vm.finish(force = true)
        advanceUntilIdle()
        assertNotNull(data.committed)
    }

    @Test
    fun 入庫的內容含影片列與圖資與標籤() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        val s3 = vm.step3.value!!
        s3.editPlace("冬山河")
        s3.editTags(listOf("玩水"))
        s3.applyPatch()
        vm.finish(force = true)
        advanceUntilIdle()
        val (video, picks) = data.committed!!
        assertEquals("v1", video.id)
        assertEquals("宜蘭兩天一夜", video.title)
        assertEquals(1, picks.size)
        assertEquals("storyboard", picks[0].source)
        assertEquals(0, picks[0].frameIndex)
        assertEquals(3, picks[0].sbLevel)
        assertEquals("冬山河", picks[0].place)
        assertEquals(listOf("玩水"), picks[0].tagNames)
        assertEquals("2025-07-12", picks[0].eventDate)
    }

    @Test
    fun 完成之後記下縮圖狀態並清掉草稿() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.finish(force = true)
        advanceUntilIdle()
        assertEquals(listOf(0), data.thumbStatesOk)
        assertTrue(data.draftCleared)
    }

    @Test
    fun 完成會回報月份與張數給外殼() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        val seen = mutableListOf<Finished>()
        val job = launch { vm.finished.collect { seen += it } }
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.finish(force = true)
        advanceUntilIdle()
        assertEquals(listOf(Finished("2025-07-12", 1)), seen)
        job.cancel()
    }

    @Test
    fun 完成之後回到第一步_不能停在已經入庫的第三步上() = runTest(dispatcher) {
        val data = RecordingData(pageOf())
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.finish(force = true)
        advanceUntilIdle()
        assertEquals(WizardStep.URL, vm.step.value)
        assertNull(vm.step3.value)
        assertNull(vm.step2.value)
    }
}
