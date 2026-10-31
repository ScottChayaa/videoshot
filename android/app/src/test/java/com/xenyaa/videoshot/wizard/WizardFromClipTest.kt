package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.core.draft.DraftCodec
import com.xenyaa.videoshot.core.draft.DraftMeta
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
import kotlinx.coroutines.async
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 剪輯「已完成」的任務直接開精靈第二步（規格第十九節「完成後」）：別支草稿的偵測與放棄、進不了第二步時。 */
@OptIn(ExperimentalCoroutinesApi::class)
class WizardFromClipTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule val tmp = TemporaryFolder()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** 記下草稿的讀寫順序；watch page 對哪支影片都回那支的資訊。 */
    private class FakeData(var stored: String?) : WizardData {
        val log = mutableListOf<String>()
        var offline = false
        override suspend fun watchPage(videoId: String): WatchPage {
            if (offline) return WatchPage(FetchResult.FETCH_FAILED, null, storyboardSpec = null)
            return WatchPage(
                FetchResult.OK,
                VideoMeta(videoId, "片 $videoId", "ch", "2026-10-31T10:00:00+08:00", 40, "unlisted", true),
                storyboardSpec = null,
            )
        }
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int) = emptySet<Int>()
        override suspend fun distinctPlaces() = emptyList<String>()
        override suspend fun tagSuggestions() = emptyList<String>()
        override suspend fun cropThumbs(
            videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit,
        ) = CropOutcome(frameIndexes, emptyList())
        override suspend fun commit(video: VideoEntity, picks: List<NewShot>) = picks.indices.map { it.toLong() }
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) {
            stored = json
            log += "save:" + DraftCodec.decode(json)!!.videoId
        }
        override suspend fun currentDraft(): String? = stored
        override suspend fun clearDraft(videoId: String) { stored = null; log += "clear:$videoId" }
        override suspend fun deleteDraftFiles(videoId: String) { log += "files:$videoId" }
    }

    private fun vmWith(data: FakeData) = WizardViewModel(
        data = data,
        frameSourceFactory = { FakeFrameSource.of(frameCount = 4, intervalSec = 10.0) },
        strength = flowOf(FilterStrength.MEDIUM),
        manualImages = { videoId -> ManualImageStore(File(tmp.root, videoId)) },
        captureFor = { null },
        today = { "2026-10-31" },
    )

    private val meta = DraftMeta("宜蘭兩天一夜", "ch", "2025-07-12T10:00:00-07:00", 600, "public", true)

    @Test
    fun 沒有草稿時otherDraft是null() = runTest(dispatcher) {
        val vm = vmWith(FakeData(null))
        advanceUntilIdle()
        assertNull(vm.otherDraft("x9"))
    }

    @Test
    fun 磁碟上有別支草稿時回它的標題() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4, meta = meta)
        val vm = vmWith(FakeData(DraftCodec.encode(payload)))
        advanceUntilIdle()
        assertEquals(OtherDraft("宜蘭兩天一夜"), vm.otherDraft("x9"))
        assertNull(vm.otherDraft("v1"))
    }

    @Test
    fun 舊草稿沒存影片資訊時標題是null() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4)
        val vm = vmWith(FakeData(DraftCodec.encode(payload)))
        advanceUntilIdle()
        assertEquals(OtherDraft(null), vm.otherDraft("x9"))
    }

    @Test
    fun 記憶體裡開著別支影片的草稿也算() = runTest(dispatcher) {
        val vm = vmWith(FakeData(null))
        advanceUntilIdle()
        vm.openRecent("v1")
        advanceUntilIdle()
        assertEquals(OtherDraft("片 v1"), vm.otherDraft("x9"))
    }

    @Test
    fun openFromClip放棄別支草稿後進第二步() = runTest(dispatcher) {
        val payload = DraftPayload("v1", step = 2, level = 3, frameCount = 4, meta = meta)
        val data = FakeData(DraftCodec.encode(payload))
        val vm = vmWith(data)
        advanceUntilIdle()

        val entered = async { vm.openFromClip("x9") }
        advanceUntilIdle()

        assertEquals(true, entered.await())
        assertEquals(WizardStep.PICK, vm.step.value)
        assertEquals("x9", vm.loaded.value?.videoId)
        assertNull(vm.otherDraft("x9"))
        // clearDraft 在正式環境連 drafts/v1/ 目錄一起刪
        assertTrue("v1 的草稿要清：${data.log}", "clear:v1" in data.log)
        // 舊草稿整列清掉要發生在新影片存草稿之前，不然會把新的也清掉
        assertTrue(data.log.indexOf("clear:v1") < data.log.indexOf("save:x9"))
        assertEquals("x9", DraftCodec.decode(data.stored!!)!!.videoId)
    }

    @Test
    fun openFromClip抓不到影片回false停在第一步() = runTest(dispatcher) {
        val data = FakeData(null).apply { offline = true }
        val vm = vmWith(data)
        advanceUntilIdle()

        val entered = async { vm.openFromClip("x9") }
        advanceUntilIdle()

        assertEquals(false, entered.await())
        assertEquals(WizardStep.URL, vm.step.value)
        assertEquals(Step1Status.Error("取圖需要網路。"), vm.status.value)
        assertFalse(data.log.any { it.startsWith("save:") })
    }
}
