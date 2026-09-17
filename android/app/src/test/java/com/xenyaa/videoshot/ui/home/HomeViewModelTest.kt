package com.xenyaa.videoshot.ui.home

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * `HomeViewModel` 是階段 7 每個任務都會經過、卻沒有任何一個任務的測試單獨照到它的地方
 * （見階段 7 全盤覆查第 7 點）。這裡用假 repo 釘住三件事：
 * - repo 丟例外不會當機，`loading` 會回到 false，錯誤要能被畫面讀到，之後還能重試成功；
 * - 換篩選要蓋掉還沒回來的舊請求（不是被它蓋掉）——這是第 3 點那個競態的判別測試；
 * - 刪除／編輯都要讓月份選項重新整理 —— 第 6 點。
 */
@ExperimentalCoroutinesApi
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun row(id: Long, date: String) = ShotRow(
        id = id, videoId = "v1", atSec = 1.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = date, place = null, description = null,
    )

    /** `LibraryRepo` 只留 `HomeViewModel` 用得到的部分可控，其餘回傳空值就好。 */
    private class FakeLibraryRepo : LibraryRepo {
        var monthCounts: List<MonthCount> = emptyList()
        var homeFeedThrows = false
        var itemsByMonth: Map<String?, List<ShotRow>> = emptyMap()

        /** 只卡「下一次」`homeFeed` 呼叫，取用後自動歸零 —— 用來製造第 3 點描述的競態。 */
        var homeFeedGate: CompletableDeferred<Unit>? = null
        val homeFeedCallsByMonth = mutableListOf<String?>()

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?): Page<ShotRow> {
            homeFeedCallsByMonth += upToMonth
            homeFeedGate?.let { gate ->
                homeFeedGate = null
                gate.await()
            }
            if (homeFeedThrows) throw RuntimeException("模擬讀取失敗")
            return Page(itemsByMonth[upToMonth].orEmpty(), null)
        }

        override suspend fun monthCounts(): List<MonthCount> = monthCounts
        override suspend fun shotsOfVideo(videoId: String): List<ShotRow> = emptyList()
        override suspend fun shotById(id: Long): ShotRow? = null
        override suspend fun shotCount(upToMonth: String?): Int = itemsByMonth[upToMonth].orEmpty().size
        override suspend fun monthFacets(month: String): List<MonthFacet> = emptyList()
        override suspend fun tagsOfShot(shotId: Long): List<String> = emptyList()
        override suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long> = emptyList()
        override suspend fun distinctPlaces(): List<String> = emptyList()
        override suspend fun allTagNames(): List<String> = emptyList()
        override suspend fun patchShots(ids: List<Long>, patch: ShotPatch) = Unit
        override suspend fun deleteShot(id: Long) = Unit
        override suspend fun deleteVideo(videoId: String) = Unit
        override suspend fun createFolder(parentId: Long?, name: String): Long = 0L
        override suspend fun shotImage(shotId: Long): ByteArray? = null
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
    }

    @Test
    fun repo丟例外時不當機_loading回到false_錯誤surface_之後還能重試成功() = runTest(dispatcher) {
        val repo = FakeLibraryRepo().apply { homeFeedThrows = true }
        val vm = HomeViewModel(repo, pageSize = 10)
        advanceUntilIdle()

        assertFalse("讀取失敗不能讓 loading 卡住", vm.state.value.loading)
        assertNotNull("失敗要能被畫面讀到才顯示得出重試列", vm.state.value.error)
        assertEquals(0, vm.state.value.items.size)

        // 修好之後（相當於使用者按了畫面上的「重試」）要能正常成功
        repo.homeFeedThrows = false
        repo.itemsByMonth = mapOf(null to listOf(row(1, "2026-03-05")))
        vm.loadMore()
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertNull("重試成功要把上一次的錯誤清掉", vm.state.value.error)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })
    }

    /**
     * 判別測試：不取消舊請求的話，A（沒篩選、晚回來）會把它的游標／`endReached`／`total`
     * 蓋回已經是「篩選 2026-03」的狀態上，混進不該出現的資料。這個測試在修之前會失敗。
     */
    @Test
    fun 換篩選會取消還沒回來的舊請求_不會被它蓋掉() = runTest(dispatcher) {
        val repo = FakeLibraryRepo().apply {
            itemsByMonth = mapOf(
                null to listOf(row(1, "2026-05-01")),
                "2026-03" to listOf(row(2, "2026-03-01")),
            )
        }
        val gate = CompletableDeferred<Unit>()
        repo.homeFeedGate = gate // 卡住 init { reload() } 觸發的第一次呼叫（A）
        val vm = HomeViewModel(repo, pageSize = 10)

        // 讓 A 真的跑起來、卡在 gate.await()
        advanceUntilIdle()
        assertEquals(listOf(null), repo.homeFeedCallsByMonth)
        assertEquals(0, vm.state.value.items.size)

        // 使用者在 A 回來之前先換了篩選：B 沒有被卡住（gate 只卡第一次），應該正常跑完
        vm.setFilter("2026-03")
        advanceUntilIdle()
        assertEquals(listOf(null, "2026-03"), repo.homeFeedCallsByMonth)
        assertEquals("2026-03", vm.state.value.upToMonth)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })

        // A 現在才回來：它撈的是舊條件，不該再有機會寫狀態
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("換篩選之後 upToMonth 不能被晚到的舊請求蓋掉", "2026-03", vm.state.value.upToMonth)
        assertEquals("A 的資料不能混進已經換過的篩選結果裡", listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun 刪除與編輯都會重新整理月份選項() = runTest(dispatcher) {
        val repo = FakeLibraryRepo().apply {
            monthCounts = listOf(MonthCount("2026-03", 1))
            itemsByMonth = mapOf(null to listOf(row(1, "2026-03-05")))
        }
        val vm = HomeViewModel(repo, pageSize = 10)
        advanceUntilIdle()
        assertEquals(listOf(MonthCount("2026-03", 1)), vm.state.value.months)

        // 刪掉那個月唯一的一張：月份選項要重新整理，不能還留著已經不存在的月份
        repo.monthCounts = emptyList()
        vm.onShotDeleted(1L)
        advanceUntilIdle()
        assertEquals(emptyList<MonthCount>(), vm.state.value.months)

        // 編輯把日期改到別的月份：理由相同
        repo.monthCounts = listOf(MonthCount("2026-04", 1))
        vm.onShotChanged(row(1, "2026-04-01"))
        advanceUntilIdle()
        assertEquals(listOf(MonthCount("2026-04", 1)), vm.state.value.months)
    }
}
