package com.xenyaa.videoshot.ui.home

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private class FakeLibraryRepo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var monthsValue: List<String> = emptyList()
        var filterOptionsValue: List<FilterOption> = emptyList()
        var monthsMatchingValue: List<String> = emptyList()
        val monthsMatchingCalls = mutableListOf<Pair<Set<String>, Set<String>>>()
        val filterOptionsCalls = mutableListOf<String?>()
        val monthFacetsCalls = mutableListOf<String>()
        val searchByFacetsCalls = mutableListOf<List<Any?>>()
        var searchItems: List<ShotRow> = emptyList()

        /** 只卡「下一次」`searchByFacets`，取用後自動歸零。 */
        var searchGate: CompletableDeferred<Unit>? = null
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

        /** 非 null 時 `months` 會卡到它完成為止。 */
        var monthsGate: CompletableDeferred<Unit>? = null

        override suspend fun months(): List<String> {
            monthsGate?.await()
            return monthsValue
        }

        override suspend fun monthsMatching(places: Set<String>, tagNames: Set<String>): List<String> {
            monthsMatchingCalls += places to tagNames
            return monthsMatchingValue
        }

        override suspend fun filterOptions(upToMonth: String?): List<FilterOption> {
            filterOptionsCalls += upToMonth
            return filterOptionsValue
        }

        override suspend fun monthFacets(month: String): List<MonthFacet> {
            monthFacetsCalls += month
            return emptyList()
        }

        override suspend fun searchByFacets(
            places: Set<String>, tagNames: Set<String>, upToMonth: String?, after: ShotCursor?, limit: Int,
        ): Page<ShotRow> {
            searchByFacetsCalls += listOf(places, tagNames, upToMonth, after, limit)
            searchGate?.let { gate ->
                searchGate = null
                gate.await()
            }
            return Page(searchItems, null)
        }
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
     * 判別測試：不取消舊請求的話，A（沒篩選、晚回來）會把它的游標／`endReached`
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
            monthsValue = listOf("2026-03")
            itemsByMonth = mapOf(null to listOf(row(1, "2026-03-05")))
        }
        val vm = HomeViewModel(repo, pageSize = 10)
        advanceUntilIdle()
        assertEquals(listOf("2026-03"), vm.state.value.months)

        // 刪掉那個月唯一的一張：月份選項要重新整理，不能還留著已經不存在的月份
        repo.monthsValue = emptyList()
        vm.onShotDeleted(1L)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.state.value.months)

        // 編輯把日期改到別的月份：理由相同
        repo.monthsValue = listOf("2026-04")
        vm.onShotChanged(row(1, "2026-04-01"))
        advanceUntilIdle()
        assertEquals(listOf("2026-04"), vm.state.value.months)
    }

    /**
     * 月份選項跟第一頁是同時發出去的兩個讀取。原本寫成
     * `_state.value = _state.value.copy(months = repo.monthCounts())`——先取了「還在載入、沒有任何列」
     * 的舊快照才去等 repo，月份比第一頁晚回來時會把已經載入的列表整份蓋回載入中
     * （跟詳情頁 2026-10-01「永遠正在載入」同一個根因）。
     */
    @Test
    fun 月份選項比第一頁晚回來時不會把已載入的列表蓋掉() = runTest(dispatcher) {
        val repo = FakeLibraryRepo().apply {
            itemsByMonth = mapOf(null to listOf(row(1, "2026-03-05")))
            monthsValue = listOf("2026-03")
        }
        val gate = CompletableDeferred<Unit>()
        repo.monthsGate = gate
        val vm = HomeViewModel(repo, pageSize = 10)
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })   // 第一頁先到

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })
        assertEquals(listOf("2026-03"), vm.state.value.months)
    }

    // ---- 階段 17：首頁篩選 ----

    private fun place(name: String) = FilterOption(name, true, "other", emptyList())
    private fun tag(name: String) = FilterOption(name, false, "topic", emptyList())

    private fun TestScope.newVm(configure: FakeLibraryRepo.() -> Unit = {}): Pair<HomeViewModel, FakeLibraryRepo> {
        val repo = FakeLibraryRepo().apply {
            itemsByMonth = mapOf(null to listOf(row(1, "2026-03-05")))
            monthsValue = listOf("2026-03", "2026-01")
            monthsMatchingValue = listOf("2026-03")
            searchItems = listOf(row(9, "2026-03-09"))
            filterOptionsValue = listOf(place("礁溪"), tag("溫泉"))
            configure()
        }
        val vm = HomeViewModel(repo, pageSize = 10)
        advanceUntilIdle()
        return vm to repo
    }

    @Test
    fun 初始化讀月份清單並走homeFeed() = runTest(dispatcher) {
        val (vm, repo) = newVm()
        assertEquals(listOf("2026-03", "2026-01"), vm.state.value.months)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.filter.isEmpty)
        assertTrue(repo.searchByFacetsCalls.isEmpty())
    }

    @Test
    fun openFilter帶入已套用的篩選並讀候選() = runTest(dispatcher) {
        val (vm, repo) = newVm()
        vm.setFilter("2026-02")
        advanceUntilIdle()
        vm.openFilter()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(FilterDraft(emptySet(), emptySet()), s.draft)
        assertEquals(listOf(place("礁溪"), tag("溫泉")), s.filterOptions)
        assertEquals(listOf("2026-02"), repo.filterOptionsCalls)

        vm.toggleDraft(place("礁溪"))
        vm.applyFilter()
        advanceUntilIdle()
        vm.openFilter()
        assertEquals(setOf("礁溪"), vm.state.value.draft!!.places)
    }

    @Test
    fun toggleDraft只改草稿不重新載入() = runTest(dispatcher) {
        val (vm, repo) = newVm()
        val feedCalls = repo.homeFeedCallsByMonth.size
        vm.openFilter()
        advanceUntilIdle()
        vm.toggleDraft(place("礁溪"))
        vm.toggleDraft(tag("溫泉"))
        advanceUntilIdle()
        assertEquals(setOf("礁溪"), vm.state.value.draft!!.places)
        assertEquals(setOf("溫泉"), vm.state.value.draft!!.tags)
        assertTrue(vm.state.value.filter.isEmpty)
        assertEquals(feedCalls, repo.homeFeedCallsByMonth.size)
        assertTrue(repo.searchByFacetsCalls.isEmpty())

        vm.toggleDraft(place("礁溪")) // 再按一次取消勾選
        assertEquals(emptySet<String>(), vm.state.value.draft!!.places)
    }

    @Test
    fun 草稿的搜尋字與展開() = runTest(dispatcher) {
        val (vm, _) = newVm()
        vm.openFilter()
        advanceUntilIdle()
        vm.setDraftQuery(true, "礁")
        vm.expandDraft(false)
        val d = vm.state.value.draft!!
        assertEquals("礁", d.query[true])
        assertEquals("", d.query[false])
        assertEquals(setOf(false), d.expanded)
    }

    @Test
    fun dismissFilter丟掉草稿_filter不變() = runTest(dispatcher) {
        val (vm, _) = newVm()
        vm.applySingle(place("礁溪"))
        advanceUntilIdle()
        vm.openFilter()
        advanceUntilIdle()
        vm.clearDraft()
        vm.dismissFilter()
        assertNull(vm.state.value.draft)
        assertEquals(HomeFilter(setOf("礁溪"), emptySet()), vm.state.value.filter)
    }

    @Test
    fun applyFilter改走searchByFacets_月份改讀monthsMatching() = runTest(dispatcher) {
        val (vm, repo) = newVm { monthsMatchingValue = listOf("2026-03") }
        vm.openFilter()
        advanceUntilIdle()
        vm.toggleDraft(place("礁溪"))
        vm.toggleDraft(tag("溫泉"))
        vm.applyFilter()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(HomeFilter(setOf("礁溪"), setOf("溫泉")), s.filter)
        assertNull(s.draft)
        assertEquals(listOf(9L), s.items.map { it.id })
        assertEquals(listOf("2026-03"), s.months)
        assertEquals(listOf(setOf("礁溪") to setOf("溫泉")), repo.monthsMatchingCalls)
        assertEquals(listOf(listOf(setOf("礁溪"), setOf("溫泉"), null, null, 10)), repo.searchByFacetsCalls)
    }

    @Test
    fun clearDraft清空草稿但filter不變_套用後回到homeFeed() = runTest(dispatcher) {
        val (vm, repo) = newVm()
        vm.applySingle(place("礁溪"))
        advanceUntilIdle()
        val feedCalls = repo.homeFeedCallsByMonth.size

        vm.openFilter()
        advanceUntilIdle()
        vm.clearDraft()
        assertEquals(FilterDraft(emptySet(), emptySet()), vm.state.value.draft)
        assertEquals(HomeFilter(setOf("礁溪"), emptySet()), vm.state.value.filter)

        vm.applyFilter()
        advanceUntilIdle()
        assertTrue(vm.state.value.filter.isEmpty)
        assertEquals(feedCalls + 1, repo.homeFeedCallsByMonth.size)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })
        assertEquals(listOf("2026-03", "2026-01"), vm.state.value.months)
    }

    @Test
    fun 篩選中不查月份小膠囊_也不產生標籤列() = runTest(dispatcher) {
        val (vm, repo) = newVm()
        // 沒篩選時有查
        assertEquals(listOf("2026-03"), repo.monthFacetsCalls)
        repo.monthFacetsCalls.clear()
        vm.applySingle(place("礁溪"))
        advanceUntilIdle()
        assertTrue(repo.monthFacetsCalls.isEmpty())
        assertTrue(HomeStore.slots(vm.state.value).none { it is HomeSlot.Facets })
    }

    @Test
    fun applySingle只含這一個_時間範圍不變() = runTest(dispatcher) {
        val (vm, repo) = newVm()
        vm.setFilter("2026-02")
        advanceUntilIdle()
        vm.applySingle(tag("溫泉"))
        advanceUntilIdle()
        assertEquals(HomeFilter(emptySet(), setOf("溫泉")), vm.state.value.filter)
        assertEquals("2026-02", vm.state.value.upToMonth)
        assertEquals(listOf(emptySet<String>(), setOf("溫泉"), "2026-02", null, 10), repo.searchByFacetsCalls.last())
    }

    @Test
    fun clearFilter清掉篩選() = runTest(dispatcher) {
        val (vm, _) = newVm()
        vm.applySingle(tag("溫泉"))
        advanceUntilIdle()
        vm.clearFilter()
        advanceUntilIdle()
        assertTrue(vm.state.value.filter.isEmpty)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })
    }

    @Test
    fun 篩選中換時間範圍照樣生效_仍走searchByFacets() = runTest(dispatcher) {
        val (vm, repo) = newVm()
        vm.applySingle(place("礁溪"))
        advanceUntilIdle()
        vm.setFilter("2026-01")
        advanceUntilIdle()
        assertEquals("2026-01", vm.state.value.upToMonth)
        assertEquals(HomeFilter(setOf("礁溪"), emptySet()), vm.state.value.filter)
        assertEquals(listOf(setOf("礁溪"), emptySet<String>(), "2026-01", null, 10), repo.searchByFacetsCalls.last())
    }

    @Test
    fun 換篩選取消進行中的舊讀取_晚到的舊結果不蓋掉新狀態() = runTest(dispatcher) {
        val (vm, repo) = newVm {
            itemsByMonth = mapOf(null to listOf(row(1, "2026-03-05")))
            searchItems = listOf(row(9, "2026-03-09"))
        }
        val gate = CompletableDeferred<Unit>()
        repo.searchGate = gate
        vm.applySingle(place("礁溪")) // 第一次 searchByFacets 被卡住
        advanceUntilIdle()
        assertEquals(1, repo.searchByFacetsCalls.size)

        vm.clearFilter() // 使用者在舊讀取回來前清掉篩選，改走 homeFeed
        advanceUntilIdle()
        assertTrue(vm.state.value.filter.isEmpty)
        assertEquals(listOf(1L), vm.state.value.items.map { it.id })

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("舊篩選晚到的結果不能混進來", listOf(1L), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.loading)
    }
}
