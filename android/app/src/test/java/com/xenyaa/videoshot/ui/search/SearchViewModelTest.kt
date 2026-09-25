package com.xenyaa.videoshot.ui.search

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.query.ParsedQuery
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.SearchPage
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.query.FakeGeminiClient
import com.xenyaa.videoshot.query.QueryResolver
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun row(id: Long) = ShotRow(
        id = id, videoId = "v1", atSec = id.toDouble(), source = "storyboard", frameIndex = id.toInt(),
        sbLevel = 3, eventDate = "2026-03-01", place = null, description = null,
    )

    private class Repo : FakeLibraryRepo() {
        var facets: List<MonthFacet> = emptyList()
        var monthCountsValue: List<com.xenyaa.videoshot.data.repo.model.MonthCount> = emptyList()
        var facetPage: Page<ShotRow> = Page(emptyList(), null)
        var facetCount = 0
        var queryPage: SearchPage = SearchPage(emptyList(), null)
        var queryCount = 0
        var lastFacetPlaces: Set<String>? = null
        var lastFacetTags: Set<String>? = null
        var lastFacetUpToMonth: String? = null
        var lastQuery: ParsedQuery? = null

        override suspend fun monthCounts() = monthCountsValue
        override suspend fun searchFacets(upToMonth: String?, limit: Int) = facets
        override suspend fun searchByFacets(places: Set<String>, tagNames: Set<String>, upToMonth: String?, after: ShotCursor?, limit: Int): Page<ShotRow> {
            lastFacetPlaces = places; lastFacetTags = tagNames; lastFacetUpToMonth = upToMonth
            return facetPage
        }
        override suspend fun searchByFacetsCount(places: Set<String>, tagNames: Set<String>, upToMonth: String?) = facetCount
        override suspend fun searchByQuery(query: ParsedQuery, after: com.xenyaa.videoshot.core.paging.SearchCursor?, limit: Int): SearchPage {
            lastQuery = query
            return queryPage
        }
        override suspend fun searchByQueryCount(query: ParsedQuery): Int {
            lastQuery = query
            return queryCount
        }
    }

    private fun resolverOf(gemini: FakeGeminiClient = FakeGeminiClient(null), key: String? = null) =
        QueryResolver(gemini = gemini, geminiKey = { key }, vocabulary = { QueryVocabulary(emptyList(), emptyList()) })

    @Test
    fun 初始化就載入facet清單與月份選項() = runTest(dispatcher) {
        val repo = Repo().apply {
            facets = listOf(MonthFacet("宜蘭", "place", 3))
            monthCountsValue = listOf(com.xenyaa.videoshot.data.repo.model.MonthCount("2026-03", 3))
        }
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        assertEquals(listOf(MonthFacet("宜蘭", "place", 3)), vm.state.value.facets)
        assertEquals(listOf(com.xenyaa.videoshot.data.repo.model.MonthCount("2026-03", 3)), vm.state.value.months)
    }

    @Test
    fun 換時間範圍重新載入facet() = runTest(dispatcher) {
        val repo = Repo()
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        repo.facets = listOf(MonthFacet("台北", "place", 1))
        vm.setUpToMonth("2026-01")
        advanceUntilIdle()
        assertEquals("2026-01", vm.state.value.upToMonth)
        assertEquals(listOf(MonthFacet("台北", "place", 1)), vm.state.value.facets)
    }

    @Test
    fun 標籤模式查詢會把chip鍵拆成地點與標籤名兩組送給repo() = runTest(dispatcher) {
        val repo = Repo().apply {
            facetPage = Page(listOf(row(1)), null)
            facetCount = 1
        }
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        vm.toggleFacet(MonthFacet("宜蘭", "place", 1))
        vm.toggleFacet(MonthFacet("露營", "tag", 1))
        vm.runSearch()
        advanceUntilIdle()

        assertEquals(setOf("宜蘭"), repo.lastFacetPlaces)
        assertEquals(setOf("露營"), repo.lastFacetTags)
        assertEquals(SearchPhase.RESULTS, vm.state.value.phase)
        assertEquals(1, vm.state.value.results.size)
        assertEquals(1, vm.state.value.total)
    }

    @Test
    fun 文字模式查詢完成後帶聽懂了摘要並標記本機解析() = runTest(dispatcher) {
        val repo = Repo().apply { queryPage = SearchPage(listOf(row(1)), null); queryCount = 1 }
        val vm = SearchViewModel(repo, resolverOf(key = null))
        advanceUntilIdle()
        vm.setMode(SearchMode.TEXT)
        vm.setTextQuery("大蝦")
        vm.runSearch()
        advanceUntilIdle()

        assertEquals(SearchPhase.RESULTS, vm.state.value.phase)
        assertTrue(vm.state.value.heard!!.local)
        assertEquals(listOf("大蝦"), repo.lastQuery?.keywords)
        assertEquals(1, vm.state.value.results.size)
    }

    @Test
    fun 文字模式用Gemini時聽懂了摘要不標本機解析() = runTest(dispatcher) {
        val repo = Repo()
        val gemini = FakeGeminiClient(ParsedQuery(places = listOf("加勒比海")))
        val vm = SearchViewModel(repo, resolverOf(gemini = gemini, key = "k"))
        advanceUntilIdle()
        vm.setMode(SearchMode.TEXT)
        vm.setTextQuery("加勒比海")
        vm.runSearch()
        advanceUntilIdle()

        assertFalse(vm.state.value.heard!!.local)
    }

    @Test
    fun 沒有選任何條件時查詢不會呼叫repo() = runTest(dispatcher) {
        val repo = Repo()
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        vm.runSearch()
        advanceUntilIdle()
        assertEquals(SearchPhase.CONDITIONS, vm.state.value.phase)
    }

    @Test
    fun loadMore延續文字模式的解析結果不會重新呼叫Gemini() = runTest(dispatcher) {
        val repo = Repo().apply {
            queryPage = SearchPage(listOf(row(1)), com.xenyaa.videoshot.core.paging.SearchCursor(0, "2026-03-01", 1))
        }
        val gemini = FakeGeminiClient(ParsedQuery(keywords = listOf("大蝦")))
        val vm = SearchViewModel(repo, resolverOf(gemini = gemini, key = "k"))
        advanceUntilIdle()
        vm.setMode(SearchMode.TEXT)
        vm.setTextQuery("大蝦")
        vm.runSearch()
        advanceUntilIdle()
        repo.queryPage = SearchPage(listOf(row(2)), null)
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.results.size)
        assertEquals(listOf("大蝦"), repo.lastQuery?.keywords)
    }

    @Test
    fun loadMore延續標籤模式查詢時捕捉的條件不受中途facet重新載入修剪影響() = runTest(dispatcher) {
        val repo = Repo().apply {
            facetPage = Page(listOf(row(1)), ShotCursor("2026-03-01", 1))
            facetCount = 1
        }
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        vm.toggleFacet(MonthFacet("宜蘭", "place", 1))
        vm.runSearch()
        advanceUntilIdle()

        // 模擬使用者查詢中途換了時間範圍——這會觸發一次 facet 重新載入,新的候選池
        // (這裡設成空清單)會把剛剛查詢用的 "宜蘭" 從 selected 修剪掉。
        repo.facets = emptyList()
        vm.setUpToMonth("2026-01")
        advanceUntilIdle()
        assertTrue(vm.state.value.selected.isEmpty())

        repo.facetPage = Page(listOf(row(2)), null)
        vm.loadMore()
        advanceUntilIdle()

        // loadMore() 應該沿用 runSearch() 當初捕捉的條件("宜蘭"、upToMonth=null)，
        // 不是重新從已經被修剪成空、且 upToMonth 已經變成 "2026-01" 的 state 讀。
        assertEquals(setOf("宜蘭"), repo.lastFacetPlaces)
        assertEquals(null, repo.lastFacetUpToMonth)
        assertEquals(2, vm.state.value.results.size)
    }

    @Test
    fun showConditions回到條件畫面() = runTest(dispatcher) {
        val repo = Repo().apply { facetPage = Page(listOf(row(1)), null); facetCount = 1 }
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        vm.toggleFacet(MonthFacet("宜蘭", "place", 1))
        vm.runSearch()
        advanceUntilIdle()
        vm.showConditions()
        assertEquals(SearchPhase.CONDITIONS, vm.state.value.phase)
        assertEquals(setOf("place:宜蘭"), vm.state.value.selected)
    }

    @Test
    fun seedFromHome帶入條件並直接查詢() = runTest(dispatcher) {
        val repo = Repo().apply {
            facetPage = Page(listOf(row(1)), null)
            facetCount = 1
            facets = listOf(MonthFacet("宜蘭", "place", 4))
            monthCountsValue = listOf(com.xenyaa.videoshot.data.repo.model.MonthCount("2026-03", 4))
        }
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        vm.seedFromHome("2026-03", MonthFacet("宜蘭", "place", 4))
        advanceUntilIdle()

        assertEquals(SearchPhase.RESULTS, vm.state.value.phase)
        assertEquals("2026-03", vm.state.value.upToMonth)
        assertEquals(setOf("宜蘭"), repo.lastFacetPlaces)
        assertEquals(setOf("place:宜蘭"), vm.state.value.selected)
        assertEquals(listOf(com.xenyaa.videoshot.data.repo.model.MonthCount("2026-03", 4)), vm.state.value.months)
    }

    /**
     * 從查詢分頁開的 Lightbox 刪除／編輯要同步回查詢結果，不然清單、「共 M 張」、Lightbox
     * 都會停在刪除／編輯前的狀態（Task 12 覆查 Important 1，跟 `HomeViewModel` 的
     * `onShotDeleted`／`onShotChanged` 是同一種修法）。
     */
    @Test
    fun 刪除會同步拿掉查詢結果裡的那一張並減少總數() = runTest(dispatcher) {
        val repo = Repo().apply { facetPage = Page(listOf(row(1), row(2)), null); facetCount = 2 }
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        vm.toggleFacet(MonthFacet("宜蘭", "place", 1))
        vm.runSearch()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.total)

        vm.onShotDeleted(1L)

        assertEquals(listOf(2L), vm.state.value.results.map { it.id })
        assertEquals(1, vm.state.value.total)
    }

    @Test
    fun 編輯會同步換掉查詢結果裡對應的那一張() = runTest(dispatcher) {
        val repo = Repo().apply { facetPage = Page(listOf(row(1)), null); facetCount = 1 }
        val vm = SearchViewModel(repo, resolverOf())
        advanceUntilIdle()
        vm.toggleFacet(MonthFacet("宜蘭", "place", 1))
        vm.runSearch()
        advanceUntilIdle()

        val edited = row(1).copy(place = "台北")
        vm.onShotChanged(edited)

        assertEquals("台北", vm.state.value.results.single { it.id == 1L }.place)
    }
}
