package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.capture.Capture
import com.xenyaa.videoshot.capture.FakeCapture
import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.draft.DraftCodec
import com.xenyaa.videoshot.core.draft.DraftDetails
import com.xenyaa.videoshot.core.draft.DraftManual
import com.xenyaa.videoshot.core.draft.DraftMeta
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
        /** 沒網路：watch page 抓不到（續做失敗的情境）。 */
        var offline = false
        override suspend fun watchPage(videoId: String): WatchPage {
            watchPageCalls++
            if (offline) return WatchPage(FetchResult.FETCH_FAILED, null, storyboardSpec = null)
            return WatchPage(
                FetchResult.OK,
                VideoMeta("v1", "宜蘭兩天一夜", "ch", "2025-07-12T10:00:00-07:00", 600, "public", true),
                storyboardSpec = null,
            )
        }
        var recentCalls = 0
        override suspend fun recentVideos(limit: Int): List<RecentVideo> { recentCalls++; return emptyList() }
        val syncedTitles = mutableListOf<String>()
        var titleChanged = false
        override suspend fun syncVideoTitle(videoId: String, meta: VideoMeta): Boolean {
            syncedTitles += meta.title
            return titleChanged
        }
        override suspend fun takenFrameIndexes(videoId: String, level: Int) = emptySet<Int>()
        override suspend fun distinctPlaces() = emptyList<String>()
        override suspend fun tagSuggestions() = emptyList<com.xenyaa.videoshot.data.library.dao.TagNameKind>()
        override suspend fun cropThumbs(
            videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit,
        ) = CropOutcome(frameIndexes, emptyList())
        override suspend fun commit(video: VideoEntity, picks: List<NewShot>) = picks.indices.map { it.toLong() }
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) { stored = json }
        override suspend fun currentDraft(): String? = stored
        override suspend fun clearDraft(videoId: String) { clearedVideoId = videoId; stored = null }
        val deletedFiles = mutableListOf<String>()
        override suspend fun deleteDraftFiles(videoId: String) { deletedFiles += videoId }
        var sweepKeep: (() -> Set<String>)? = null
        override suspend fun sweepDraftFiles(keep: () -> Set<String>, olderThanMillis: Long) { sweepKeep = keep }
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
    fun 有草稿時進場直接續做不再詢問() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 3, level = 3, frameCount = 4, selected = listOf(0))
        val vm = vmWith(DraftData(DraftCodec.encode(payload)))
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.DETAILS, vm.step.value)
    }

    @Test
    fun 草稿比進場晚讀到也會續做() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4, selected = listOf(1))
        val vm = vmWith(DraftData(DraftCodec.encode(payload)))
        // app 啟動時就停在取圖分頁：畫面進場的當下草稿還沒讀完
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.PICK, vm.step.value)
    }

    @Test
    fun 沒進精靈就不續做() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        // ViewModel 跟著 app 一起建，使用者還沒按【取圖】—— 不該先去抓網路
        assertEquals(0, data.watchPageCalls)
        assertEquals(WizardStep.URL, vm.step.value)
    }

    @Test
    fun 沒有草稿就停在第一步() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.URL, vm.step.value)
        assertEquals(0, data.watchPageCalls)
    }

    @Test
    fun 草稿壞掉就當作沒有草稿() = runTest(dispatcher) {
        val data = DraftData("{ 這不是 json")
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.URL, vm.step.value)
        assertEquals(0, data.watchPageCalls)
    }

    @Test
    fun 續做時沒網路就留著草稿_下次進場再接() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4, selected = listOf(1))
        val data = DraftData(DraftCodec.encode(payload)).apply { offline = true }
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()
        // 停在第二步（骨架屏換成錯誤與【重試】），不退回第一步
        assertEquals(WizardStep.PICK, vm.step.value)
        assertNull(vm.step2.value)
        assertEquals("取圖需要網路。", vm.resumeError.value)
        assertNull(data.clearedVideoId)

        data.offline = false
        vm.onLeave()
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.PICK, vm.step.value)
        assertNull(vm.resumeError.value)
        assertEquals(setOf(1), vm.step2.value!!.state.value.selected)
    }

    @Test
    fun 沒網路時按重試會再接一次() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4, selected = listOf(1))
        val data = DraftData(DraftCodec.encode(payload)).apply { offline = true }
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter(); advanceUntilIdle()
        data.offline = false
        vm.retryResume(); advanceUntilIdle()
        assertNull(vm.resumeError.value)
        assertEquals(setOf(1), vm.step2.value!!.state.value.selected)
    }

    @Test
    fun 接不上時按返回回第一步_草稿留著下次再接() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 3, level = 3, frameCount = 4, selected = listOf(1))
        val data = DraftData(DraftCodec.encode(payload)).apply { offline = true }
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter(); advanceUntilIdle()
        assertEquals(WizardStep.DETAILS, vm.step.value)

        assertTrue(vm.back())
        assertEquals(WizardStep.URL, vm.step.value)
        assertNull(vm.resumeError.value)
        assertNull(data.clearedVideoId)

        data.offline = false
        vm.onLeave()
        vm.onEnter(); advanceUntilIdle()
        assertEquals(WizardStep.DETAILS, vm.step.value)
    }

    @Test
    fun 續做載入中按返回_不會被帶回草稿那一步() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter()
        assertTrue(vm.back())
        advanceUntilIdle()
        assertEquals(WizardStep.URL, vm.step.value)
    }

    @Test
    fun 一進場就切到草稿停的那一步_資料還沒好也一樣() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 3, level = 3, frameCount = 4, selected = listOf(0))
        val vm = vmWith(DraftData(DraftCodec.encode(payload)))
        advanceUntilIdle()
        vm.onEnter()
        // 還沒跑協程：畫面已經在第三步（畫骨架屏），不是先停在第一步
        assertEquals(WizardStep.DETAILS, vm.step.value)
        assertNull(vm.step3.value)
        advanceUntilIdle()
        assertNotNull(vm.step3.value)
    }

    @Test
    fun 草稿存了spec與影片資訊_續做不等網路() = runTest(dispatcher) {
        val payload = DraftPayload(
            "v1", step = 2, level = 3, frameCount = 4, selected = listOf(2),
            meta = DraftMeta("宜蘭兩天一夜", "ch", "2025-07-12T10:00:00-07:00", 600, "public", true),
        )
        val data = DraftData(DraftCodec.encode(payload)).apply { offline = true }
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter(); advanceUntilIdle()
        assertEquals(0, data.watchPageCalls)
        assertNull(vm.resumeError.value)
        assertEquals("宜蘭兩天一夜", vm.loaded.value!!.page.meta!!.title)
        assertEquals(setOf(2), vm.step2.value!!.state.value.selected)
    }

    @Test
    fun 存草稿時一起存spec與影片資訊() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        val saved = DraftCodec.decode(data.stored!!)!!
        assertEquals("宜蘭兩天一夜", saved.meta!!.title)
        assertEquals(600, saved.meta!!.durationSec)
    }

    @Test
    fun 返回到第一步離開後_再進場回到第二步() = runTest(dispatcher) {
        val vm = vmWith(DraftData(null))
        vm.openRecent("v1")
        advanceUntilIdle()
        vm.step2.value!!.toggle(2)
        vm.back()
        assertTrue(!vm.back()) // 第一步：交給畫面離開精靈
        vm.onExitKeepingDraft()
        vm.onLeave()

        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.PICK, vm.step.value)
        assertEquals(setOf(2), vm.step2.value!!.state.value.selected)
    }

    @Test
    fun 停在第一步時畫面重建不會跳回第二步() = runTest(dispatcher) {
        val vm = vmWith(DraftData(null))
        vm.openRecent("v1")
        advanceUntilIdle()
        vm.back()
        // 轉螢幕：畫面重建、再進場一次，但使用者沒有離開精靈
        vm.onLeave()
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.URL, vm.step.value)
    }

    // ---- 草稿目錄的清理（2026-10-18）----

    @Test
    fun 啟動時清理草稿資料夾_保留目前草稿那支() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val data = DraftData(DraftCodec.encode(payload))
        vmWith(data)
        advanceUntilIdle()
        assertEquals(setOf("v1"), data.sweepKeep!!())
    }

    @Test
    fun 啟動時清理草稿資料夾_沒有草稿就什麼都不留() = runTest(dispatcher) {
        val data = DraftData(null)
        vmWith(data)
        advanceUntilIdle()
        assertEquals(emptySet<String>(), data.sweepKeep!!())
    }

    @Test
    fun 清理時正在開的影片也不刪() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.openRecent("v2"); advanceUntilIdle()
        // keep 每刪一個目錄前都重新問：清到一半使用者開了影片，那支也要留著
        assertEquals(setOf("v2"), data.sweepKeep!!())
    }

    @Test
    fun 換一支影片會清掉上一支的目錄_草稿列不動() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.back()
        vm.openRecent("v2"); advanceUntilIdle()
        assertEquals(listOf("v1"), data.deletedFiles)
        assertNull(data.clearedVideoId)
        assertEquals("v2", DraftCodec.decode(data.stored!!)!!.videoId)
    }

    @Test
    fun 重開同一支影片不清目錄() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.back()
        vm.openRecent("v1"); advanceUntilIdle()
        assertEquals(emptyList<String>(), data.deletedFiles)
    }

    @Test
    fun 續做沒接上就改開別支_清掉沒接上那支的目錄() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val data = DraftData(DraftCodec.encode(payload)).apply { offline = true }
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter(); advanceUntilIdle()
        vm.back() // 接不上：回第一步
        data.offline = false
        vm.openRecent("v2"); advanceUntilIdle()
        assertEquals(listOf("v1"), data.deletedFiles)
    }

    // ---- 歷史紀錄的標題同步（2026-10-18）----

    @Test
    fun 開影片時把圖庫標題同步成YouTube的_有改到就重讀歷史紀錄() = runTest(dispatcher) {
        val data = DraftData(null).apply { titleChanged = true }
        val vm = vmWith(data)
        advanceUntilIdle()
        val before = data.recentCalls
        vm.openRecent("v1"); advanceUntilIdle()
        assertEquals(listOf("宜蘭兩天一夜"), data.syncedTitles)
        assertEquals(before + 1, data.recentCalls)
    }

    @Test
    fun 標題本來就一樣就不重讀歷史紀錄() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        advanceUntilIdle()
        val before = data.recentCalls
        vm.openRecent("v1"); advanceUntilIdle()
        assertEquals(before, data.recentCalls)
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
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.DETAILS, vm.step.value)
        assertEquals(listOf(0, 2), vm.step3.value!!.state.value.cells.map { it.cell })
        assertEquals("冬山河", vm.step3.value!!.state.value.details[0]!!.place)
    }

    @Test
    fun 繼續時會重抓watchpage刷新簽章() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4, selected = listOf(1))
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(1, data.watchPageCalls)
        assertEquals(setOf(1), vm.step2.value!!.state.value.selected)
    }

    @Test
    fun 按X會清掉草稿與目錄_再進場從第一步開始() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.PICK, vm.step.value)

        vm.abandon()
        vm.onLeave()
        advanceUntilIdle()
        assertEquals("v1", data.clearedVideoId)
        assertNull(data.stored)
        assertEquals(WizardStep.URL, vm.step.value)
        assertNull(vm.step2.value)

        vm.onEnter()
        advanceUntilIdle()
        assertEquals(WizardStep.URL, vm.step.value)
        assertEquals(1, data.watchPageCalls)
    }

    @Test
    fun 續做還在載入時按X_不會被帶回第二步() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val data = DraftData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()
        vm.onEnter()
        vm.abandon()
        advanceUntilIdle()
        assertEquals(WizardStep.URL, vm.step.value)
        assertNull(vm.step2.value)
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
    fun 選擇器寫入後會把圖資存進草稿() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.applyDetails(DetailsPatch(place = "冬山河"))
        advanceUntilIdle()
        val saved = DraftCodec.decode(data.stored!!)!!
        assertEquals(3, saved.step)
        assertEquals("冬山河", saved.details["0"]!!.place)
        assertTrue(saved.details["0"]!!.applied)
    }

    @Test
    fun 返回上一步會存草稿() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(1)
        data.stored = null
        assertTrue(vm.back())
        advanceUntilIdle()
        val saved = DraftCodec.decode(data.stored!!)!!
        assertEquals(2, saved.step)
        assertEquals(listOf(1), saved.selected)
    }

    @Test
    fun 完成之後草稿就不見了() = runTest(dispatcher) {
        val data = DraftData(null)
        val vm = vmWith(data)
        vm.openRecent("v1"); advanceUntilIdle()
        vm.step2.value!!.toggle(0)
        vm.goTo(WizardStep.DETAILS); advanceUntilIdle()
        vm.applyDetails(DetailsPatch(place = "冬山河")); vm.finish()
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
        vm.onEnter()
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
        vm.onEnter()
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
        // 第三步把勾選縮到只剩其中一張，再用選擇器寫入
        val s3 = vm.step3.value!!
        s3.selectNone()
        s3.toggle(0)
        vm.applyDetails(DetailsPatch(place = "冬山河"))
        advanceUntilIdle()
        // 第三步的 selected 是「選擇器會寫進哪幾張」，存進草稿的話另外兩張會在續做時消失
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
        vm.onEnter(); advanceUntilIdle()
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
        vm.onEnter(); advanceUntilIdle()
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
        vm.onEnter(); advanceUntilIdle()
        val state = vm.step2.value!!.state.value
        // 兩張都還在牆上（換層級不影響手動圖本身），但格號整批往後搬
        assertEquals(listOf(6, 7), state.manual.map { it.cellIndex })
        // **勾選狀態要跟著搬** —— 一律勾上的話，使用者刻意取消的那張會被寫進 library.db
        assertEquals(setOf(7), state.selected)
    }
}
