package com.xenyaa.videoshot.ui.detail

import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
class BatchEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun shot(id: Long, atSec: Double, place: String? = null, tags: List<String> = emptyList()) =
        ShotRow(
            id = id, videoId = "v1", atSec = atSec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
            eventDate = "2026-03-01", place = place, description = null,
        ) to tags

    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var shots: List<ShotRow> = emptyList()
        var tagsById: Map<Long, List<String>> = emptyMap()
        val patchCalls = mutableListOf<Pair<List<Long>, ShotPatch>>()

        override suspend fun shotsOfVideo(videoId: String) = shots
        override suspend fun tagsOfShot(shotId: Long) = tagsById[shotId].orEmpty()
        override suspend fun patchShots(ids: List<Long>, patch: ShotPatch) { patchCalls += ids to patch }
    }

    @Test
    fun 讀進來的每一格都帶著現有圖資_不是空白() = runTest(dispatcher) {
        val (s1, t1) = shot(1, 10.0, place = "宜蘭", tags = listOf("露營"))
        val repo = Repo().apply { shots = listOf(s1); tagsById = mapOf(1L to t1) }
        val vm = BatchEditViewModel("v1", repo)
        advanceUntilIdle()

        val ready = vm.state.value as BatchEditState.Ready
        val step3State = ready.store.state.value
        assertEquals("宜蘭", step3State.details[0]?.place)
        assertEquals(listOf("露營"), step3State.details[0]?.tags)
        // 剛讀進來、還沒編輯過——不該顯示綠點
        assertEquals(false, step3State.details[0]?.applied)
    }

    @Test
    fun 沒有任何收藏時是錯誤狀態() = runTest(dispatcher) {
        val vm = BatchEditViewModel("v1", Repo())
        advanceUntilIdle()
        assertTrue(vm.state.value is BatchEditState.Error)
    }

    @Test
    fun 完成時只寫套用過的那幾張_其餘不呼叫patchShots() = runTest(dispatcher) {
        val (s1, _) = shot(1, 10.0, place = "宜蘭")
        val (s2, _) = shot(2, 20.0, place = "台北")
        val repo = Repo().apply { shots = listOf(s1, s2) }
        val vm = BatchEditViewModel("v1", repo)
        advanceUntilIdle()

        vm.toggle(1) // 只留 cell 0 勾選（進場預設全選，toggle 掉 cell 1）
        vm.editPlace("羅東")
        vm.apply()
        vm.finish()
        advanceUntilIdle()

        assertEquals(1, repo.patchCalls.size)
        val (ids, patch) = repo.patchCalls.single()
        assertEquals(listOf(1L), ids)
        assertEquals("羅東", patch.place)
        assertEquals("2026-03-01", patch.eventDate)
    }

    /**
     * 最終審查 Finding 1：`AppRoot` 用 videoId 當 key 快取這個 VM，同一支影片第二次進批次編輯
     * 拿到的是同一個實例，`init{}` 不會再跑——呼叫端（`AppRoot.kt` 的 `LaunchedEffect(Unit)`）
     * 改叫公開的 `reload()`，這裡直接驗證 `reload()` 本身會重建一個乾淨的 `Step3Store`：
     * 上一輪套用過的 cell 0，重查之後不該還帶著 `applied = true`。
     */
    @Test
    fun reload會建立全新的store_不殘留上一輪的套用狀態() = runTest(dispatcher) {
        val (s1, _) = shot(1, 10.0, place = "宜蘭")
        val (s2, _) = shot(2, 20.0, place = "台北")
        val repo = Repo().apply { shots = listOf(s1, s2) }
        val vm = BatchEditViewModel("v1", repo)
        advanceUntilIdle()

        vm.toggle(1)
        vm.editPlace("羅東")
        vm.apply()
        vm.finish()
        advanceUntilIdle()
        val beforeReload = (vm.state.value as BatchEditState.Ready).store.state.value
        assertEquals(true, beforeReload.details[0]?.applied)

        // 模擬使用者離開又重新進來這支影片的批次編輯——同一個實例，呼叫 reload()
        vm.reload()
        advanceUntilIdle()

        val afterReload = (vm.state.value as BatchEditState.Ready).store.state.value
        assertEquals(false, afterReload.details[0]?.applied)
        assertEquals(0, afterReload.appliedCount)
    }

    @Test
    fun 完成後finished會發出一次事件() = runTest(dispatcher) {
        val (s1, _) = shot(1, 10.0)
        val repo = Repo().apply { shots = listOf(s1) }
        val vm = BatchEditViewModel("v1", repo)
        advanceUntilIdle()

        var fired = false
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        val job = GlobalScope.launch(dispatcher) { vm.finished.collect { fired = true } }
        advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()
        assertTrue(fired)
        job.cancel()
    }

    @Test
    fun 完成後記下這一輪套用過的地點與標籤_原本就有的不算() = runTest(dispatcher) {
        val used = mutableListOf<Pair<Set<String>, Set<String>>>()
        val (s1, t1) = shot(1, 10.0, place = "宜蘭", tags = listOf("露營"))
        val repo = Repo().apply { shots = listOf(s1); tagsById = mapOf(1L to t1) }
        val vm = BatchEditViewModel("v1", repo) { p, t -> used += p to t }
        advanceUntilIdle()
        vm.editPlace("羅東")
        vm.apply()
        vm.finish()
        advanceUntilIdle()
        assertEquals(listOf(setOf("羅東") to emptySet<String>()), used)
    }

    @Test
    fun 沒有套用過就完成_不記() = runTest(dispatcher) {
        val used = mutableListOf<Pair<Set<String>, Set<String>>>()
        val (s1, _) = shot(1, 10.0, place = "宜蘭")
        val repo = Repo().apply { shots = listOf(s1) }
        val vm = BatchEditViewModel("v1", repo) { p, t -> used += p to t }
        advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()
        assertEquals(emptyList<Pair<Set<String>, Set<String>>>(), used)
    }
}
