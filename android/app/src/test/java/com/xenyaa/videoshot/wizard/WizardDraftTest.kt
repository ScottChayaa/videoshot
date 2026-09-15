package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.capture.Capture
import com.xenyaa.videoshot.capture.FakeCapture
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
import com.xenyaa.videoshot.player.FakePlayer
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import com.xenyaa.videoshot.wizard.frames.FramePlan
import com.xenyaa.videoshot.wizard.frames.FrameSource
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

    /**
     * @param frameSource 續做時重抓 watch page 可能挑到**別的層級** —— 驗那一段的測試
     *        要能讓第二次建出來的來源與草稿裡記的不一樣
     * @param capture 只有驗「截圖的格號」的測試需要它，其餘一律沒有截圖器
     */
    private fun vmWith(
        data: DraftData,
        frameSource: () -> FrameSource = { FakeFrameSource.of(frameCount = 4, intervalSec = 10.0) },
        capture: Capture? = null,
    ) = WizardViewModel(
        data = data,
        frameSourceFactory = { frameSource() },
        strength = flowOf(FilterStrength.MEDIUM),
        hintSeen = flowOf(true),
        onHintSeen = {},
        // 每支影片各自一個子目錄，與正式作 `drafts/{videoId}/manual/` 的切法一致；
        // 用 File(...) 而不是 tmp.newFolder(...) —— 還原多張手動圖時同一個 videoId 會被問好幾次，
        // newFolder 對已存在的目錄會丟例外
        manualImages = { videoId -> ManualImageStore(File(tmp.root, videoId)) },
        captureFor = { capture },
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

    // ---- 最終複審 Finding 1：草稿存的必須是「第二步挑了哪些」 ----

    @Test
    fun 在第三步只挑幾張套用圖資_草稿存的仍是第二步的全部勾選() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.step2.value!!.toggle(1)
        vm.step2.value!!.toggle(2)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        // 第三步把抽屜縮到只編輯其中一張，再【套用到 1 張】
        val s3 = vm.step3.value!!
        s3.selectNone()
        s3.toggle(0)
        s3.editPlace("冬山河")
        vm.applyDetails()
        advanceUntilIdle()
        // 第三步的 selected 是「抽屜在編哪幾張」，存進草稿的話另外兩張會在續做時消失
        assertEquals(listOf(0, 1, 2), DraftCodec.decode(data.stored!!)!!.selected)
    }

    // ---- 最終複審 Finding 3：還原留下的缺號不能讓新截圖撞號 ----

    @Test
    fun 還原後有缺號_再截一張不會撞到既有的手動格號() = runTest(dispatcher) {
        val dir = File(tmp.root, "v1").apply { mkdirs() }
        File(dir, "b.webp").writeBytes(byteArrayOf(2))
        val payload = DraftPayload(
            "v1", step = 2, level = 3, frameCount = 4,
            selected = listOf(5),
            manual = listOf(
                // 第 4 格的檔案不見了 —— 還原後手動格只剩第 5 格，編號中間有一個洞
                DraftManual(cell = 4, atSec = 10.0, fileName = "gone.webp", fromGallery = false),
                DraftManual(cell = 5, atSec = 20.0, fileName = "b.webp", fromGallery = true),
            ),
        )
        val vm = vmWith(DraftData(DraftCodec.encode(payload)), capture = FakeCapture(atSec = 30.0))
        advanceUntilIdle()
        vm.resumeDraft(); advanceUntilIdle()
        // 播放器與截圖器要在 openStep2 之後才接得上（openStep2 會把上一支影片的清掉）
        vm.attachPlayer(FakePlayer())
        vm.takeShot(); advanceUntilIdle()
        val state = vm.step2.value!!.state.value
        assertEquals(listOf(5, 6), state.manual.map { it.cellIndex })
        // 牆上同一個格號出現兩次，LazyVerticalGrid 的 key 會直接丟例外
        assertEquals(state.visible.distinct(), state.visible)
    }

    // ---- 最終複審 Finding 5：續做時層級變了，格號空間整個換了一套 ----

    @Test
    fun 續做時層級變了_手動圖搬進新格號而storyboard的勾選整批丟掉() = runTest(dispatcher) {
        val dir = File(tmp.root, "v1").apply { mkdirs() }
        File(dir, "a.webp").writeBytes(byteArrayOf(1))
        val payload = DraftPayload(
            "v1", step = 3, level = 3, frameCount = 4,
            selected = listOf(1, 4),
            manual = listOf(DraftManual(cell = 4, atSec = 10.0, fileName = "a.webp", fromGallery = false)),
            details = mapOf("4" to DraftDetails("2025-07-12", place = "冬山河", applied = true)),
        )
        val vm = vmWith(
            DraftData(DraftCodec.encode(payload)),
            // 重抓之後挑到 L2：格數從 4 變成 6，舊的格號在新層級裡指的是別的時間點
            frameSource = {
                FakeFrameSource(
                    plan = FramePlan("v1", level = 2, atSec = List(6) { it * 5.0 }, lowQuality = true),
                    perSheet = 6,
                )
            },
        )
        advanceUntilIdle()
        vm.resumeDraft(); advanceUntilIdle()
        val state = vm.step2.value!!.state.value
        // 手動圖搬到新空間的第一格（frameCount = 6）
        assertEquals(listOf(6), state.manual.map { it.cellIndex })
        // storyboard 的第 1 格整批丟掉 —— 它在 L2 裡是別的畫面
        assertEquals(setOf(6), state.selected)
        // 使用者打過的字要跟著搬家，不能因為鍵換了就靜靜不見
        assertEquals("冬山河", vm.step3.value!!.state.value.details[6]!!.place)
    }

    @Test
    fun 續做時層級變了_沒勾的手動圖不會自己被勾回來() = runTest(dispatcher) {
        val dir = File(tmp.root, "v1").apply { mkdirs() }
        File(dir, "a.webp").writeBytes(byteArrayOf(1))
        File(dir, "b.webp").writeBytes(byteArrayOf(2))
        val payload = DraftPayload(
            "v1", step = 2, level = 3, frameCount = 4,
            // 兩張手動圖，使用者離開前只留下第 5 格 —— 第 4 格是他刻意取消掉的
            selected = listOf(5),
            manual = listOf(
                DraftManual(cell = 4, atSec = 10.0, fileName = "a.webp", fromGallery = false),
                DraftManual(cell = 5, atSec = 20.0, fileName = "b.webp", fromGallery = true),
            ),
        )
        val vm = vmWith(
            DraftData(DraftCodec.encode(payload)),
            frameSource = {
                FakeFrameSource(
                    plan = FramePlan("v1", level = 2, atSec = List(6) { it * 5.0 }, lowQuality = true),
                    perSheet = 6,
                )
            },
        )
        advanceUntilIdle()
        vm.resumeDraft(); advanceUntilIdle()
        val state = vm.step2.value!!.state.value
        // 兩張都還在牆上（換層級不影響手動圖本身），但格號整批往後搬
        assertEquals(listOf(6, 7), state.manual.map { it.cellIndex })
        // **勾選狀態要跟著搬** —— 一律勾上的話，使用者刻意取消的那張會被寫進 library.db
        assertEquals(setOf(7), state.selected)
    }
}
