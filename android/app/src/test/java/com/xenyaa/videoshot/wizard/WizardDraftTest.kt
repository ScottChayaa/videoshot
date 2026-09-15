package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.core.draft.DraftCodec
import com.xenyaa.videoshot.core.draft.DraftDetails
import com.xenyaa.videoshot.core.draft.DraftManual
import com.xenyaa.videoshot.core.draft.DraftPayload
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WizardDraftTest {

    private val dispatcher = StandardTestDispatcher()

    /** 手動圖的還原測試要真的有檔案可以 `exists()`。 */
    @get:Rule val tmp = TemporaryFolder()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val meta = VideoMeta(
        videoId = "v1", title = "宜蘭兩天一夜", channelTitle = "ch",
        publishedAt = "2025-07-12T10:00:00-07:00", durationSec = 600,
        privacy = "public", playableInEmbed = true,
    )

    private class DraftData(var stored: String?) : WizardData {
        var watchPageCalls = 0
        var clearedVideoId: String? = null
        override suspend fun watchPage(videoId: String): WatchPage {
            watchPageCalls++
            return WatchPage(
                FetchResult.OK,
                VideoMeta("v1", "宜蘭兩天一夜", "ch", "2025-07-12T10:00:00-07:00", 600, "public", true),
                storyboardSpec = null,
            )
        }
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int) = emptySet<Int>()
        override suspend fun distinctPlaces() = emptyList<String>()
        override suspend fun allTagNames() = emptyList<String>()
        override suspend fun cropThumbs(
            videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit,
        ) = CropOutcome(frameIndexes, emptyList())
        override suspend fun commit(video: VideoEntity, picks: List<NewShot>) = picks.indices.map { it.toLong() }
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) { stored = json }
        override suspend fun currentDraft(): String? = stored
        override suspend fun clearDraft(videoId: String) { clearedVideoId = videoId; stored = null }
    }

    private fun vmWith(data: DraftData) = WizardViewModel(
        data = data,
        frameSourceFactory = { FakeFrameSource.of(frameCount = 4, intervalSec = 10.0) },
        strength = flowOf(FilterStrength.MEDIUM),
        hintSeen = flowOf(true),
        onHintSeen = {},
        // 每支影片各自一個子目錄，與正式作 `drafts/{videoId}/manual/` 的切法一致；
        // 用 File(...) 而不是 tmp.newFolder(...) —— 還原多張手動圖時同一個 videoId 會被問好幾次，
        // newFolder 對已存在的目錄會丟例外
        manualImages = { videoId -> ManualImageStore(File(tmp.root, videoId)) },
        captureFor = { null },
        today = { "2026-09-15" },
    )

    @Test
    fun 有草稿時進場會問要不要繼續() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 3, level = 3, frameCount = 4, selected = listOf(0))
        val vm = vmWith(DraftData(DraftCodec.encode(payload)))
        advanceUntilIdle()
        assertEquals(DraftPrompt("v1", 3), vm.draftPrompt.value)
    }

    @Test
    fun 沒有草稿就不問() = runTest(dispatcher) {
        val vm = vmWith(DraftData(null))
        advanceUntilIdle()
        assertNull(vm.draftPrompt.value)
    }

    @Test
    fun 草稿壞掉就當作沒有草稿() = runTest(dispatcher) {
        val vm = vmWith(DraftData("{ 這不是 json"))
        advanceUntilIdle()
        assertNull(vm.draftPrompt.value)
    }

    // ---- 案例 13：繼續 → 回到原步驟與原選擇 ----

    @Test
    fun 繼續會回到原步驟與原選擇() = runTest(dispatcher) {
        val payload = DraftPayload(
            "v1", step = 3, level = 3, frameCount = 4,
            selected = listOf(0, 2),
            details = mapOf("0" to DraftDetails("2025-07-12", place = "冬山河", applied = true)),
        )
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.resumeDraft()
        advanceUntilIdle()
        assertEquals(WizardStep.DETAILS, vm.step.value)
        assertEquals(listOf(0, 2), vm.step3.value!!.state.value.cells.map { it.cell })
        assertEquals("冬山河", vm.step3.value!!.state.value.details[0]!!.place)
        assertNull(vm.draftPrompt.value)
    }

    @Test
    fun 繼續時會重抓watchpage刷新簽章() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4, selected = listOf(1))
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.resumeDraft()
        advanceUntilIdle()
        assertEquals(1, data.watchPageCalls)
        assertEquals(setOf(1), vm.step2.value!!.state.value.selected)
    }

    @Test
    fun 重新開始會清掉草稿與目錄() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.startOver()
        advanceUntilIdle()
        assertEquals("v1", data.clearedVideoId)
        assertNull(vm.draftPrompt.value)
        assertEquals(WizardStep.URL, vm.step.value)
    }

    // ---- 存檔時機 ----

    @Test
    fun 進第二步就存了一份草稿() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1")
        advanceUntilIdle()
        val saved = DraftCodec.decode(data.stored!!)!!
        assertEquals("v1", saved.videoId)
        assertEquals(2, saved.step)
    }

    @Test
    fun 套用之後會把圖資存進草稿() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.step3.value!!.editPlace("冬山河")
        vm.applyDetails()
        advanceUntilIdle()
        val saved = DraftCodec.decode(data.stored!!)!!
        assertEquals(3, saved.step)
        assertEquals("冬山河", saved.details["0"]!!.place)
        assertTrue(saved.details["0"]!!.applied)
    }

    @Test
    fun 保留草稿並離開會存一份() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(1)
        data.stored = null
        vm.keepDraft()
        advanceUntilIdle()
        assertNotNull(data.stored)
        assertEquals(listOf(1), DraftCodec.decode(data.stored!!)!!.selected)
    }

    @Test
    fun 捨棄草稿會刪掉目錄() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.discardDraft()
        advanceUntilIdle()
        assertEquals("v1", data.clearedVideoId)
    }

    @Test
    fun 完成之後草稿就不見了() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.finish(force = true)
        advanceUntilIdle()
        assertNull(data.stored)
    }

    // ---- 手動圖的還原（Critical 修正：不能借用 addManual 的自動編號與自動勾選） ----

    @Test
    fun 續做會照草稿還原手動圖的勾選_不是一律勾上() = runTest(dispatcher) {
        // 兩張手動圖：第 4 格草稿裡是勾的，第 5 格不是
        val dir = File(tmp.root, "v1").apply { mkdirs() }
        File(dir, "a.webp").writeBytes(byteArrayOf(1))
        File(dir, "b.webp").writeBytes(byteArrayOf(2))
        val payload = DraftPayload(
            "v1", step = 2, level = 3, frameCount = 4,
            selected = listOf(4),
            manual = listOf(
                DraftManual(cell = 4, atSec = 10.0, fileName = "a.webp", fromGallery = false),
                DraftManual(cell = 5, atSec = 20.0, fileName = "b.webp", fromGallery = true),
            ),
        )
        val vm = vmWith(DraftData(DraftCodec.encode(payload)))
        advanceUntilIdle()
        vm.resumeDraft()
        advanceUntilIdle()
        val state = vm.step2.value!!.state.value
        assertEquals(setOf(4), state.selected)
        assertEquals(listOf(4, 5), state.manual.map { it.cellIndex })
    }

    @Test
    fun 手動圖的檔案不見了_其餘的格號不會被往前擠() = runTest(dispatcher) {
        val dir = File(tmp.root, "v1").apply { mkdirs() }
        File(dir, "b.webp").writeBytes(byteArrayOf(2))
        val payload = DraftPayload(
            "v1", step = 2, level = 3, frameCount = 4,
            selected = listOf(4, 5),
            manual = listOf(
                DraftManual(cell = 4, atSec = 10.0, fileName = "gone.webp", fromGallery = false),
                DraftManual(cell = 5, atSec = 20.0, fileName = "b.webp", fromGallery = true),
            ),
        )
        val vm = vmWith(DraftData(DraftCodec.encode(payload)))
        advanceUntilIdle()
        vm.resumeDraft()
        advanceUntilIdle()
        val state = vm.step2.value!!.state.value
        // 第 5 格還是第 5 格 —— 第三步的 details 是用這個號碼當鍵的
        assertEquals(listOf(5), state.manual.map { it.cellIndex })
        // 不見的那張不留在勾選裡
        assertEquals(setOf(5), state.selected)
    }
}
