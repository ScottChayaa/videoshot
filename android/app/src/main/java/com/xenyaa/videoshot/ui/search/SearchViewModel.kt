package com.xenyaa.videoshot.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.query.ParsedQuery
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.query.QuerySource
import com.xenyaa.videoshot.query.QueryResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val FACET_PAGE_LIMIT = 30
private const val FACET_EXPANDED_LIMIT = 500

/** chip 的 `facetKey` 拆回「地點集合」與「標籤名集合」（key 格式 `"place:X"`／`"tag:Y"`，見 [facetKey]）。 */
private fun placesAndTagsOf(selected: Set<String>): Pair<Set<String>, Set<String>> {
    val places = mutableSetOf<String>()
    val tags = mutableSetOf<String>()
    for (key in selected) {
        val sep = key.indexOf(':')
        if (sep < 0) continue
        if (key.substring(0, sep) == "place") places += key.substring(sep + 1) else tags += key.substring(sep + 1)
    }
    return places to tags
}

private fun heardTextOf(parsed: ParsedQuery): String {
    val parts = parsed.places + parsed.tags + parsed.keywords
    return if (parts.isEmpty()) "（沒有解析出條件）" else parts.joinToString("・")
}

/**
 * 查詢分頁的資料接線（規格第六節「查詢」、第八節「檢索」）。
 * @param pageSize 測試會調小，正式一律 50
 */
class SearchViewModel(
    private val repo: LibraryRepo,
    private val queryResolver: QueryResolver,
    private val pageSize: Int = 50,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** 文字模式往下捲要延續同一次解析結果，不能每捲一頁就重新問一次 Gemini（見本 Task 的 KDoc）。 */
    private var resolvedQuery: ParsedQuery? = null

    /** 換條件前一次還在跑的讀取要先取消——理由同 `HomeViewModel.loadJob`。 */
    private var loadJob: Job? = null

    init {
        loadFacets()
        // 日期選擇器的選項，跟首頁看的是同一份「有收藏的月份」清單，只查一次不必跟著時間篩選重查
        launchGuarded { _state.value = _state.value.copy(months = repo.monthCounts()) }
    }

    fun setMode(mode: SearchMode) { _state.value = SearchStore.setMode(_state.value, mode) }

    fun setTextQuery(text: String) { _state.value = SearchStore.setTextQuery(_state.value, text) }

    fun toggleFacet(facet: MonthFacet) { _state.value = SearchStore.toggleFacet(_state.value, facet) }

    fun setUpToMonth(month: String?) {
        _state.value = SearchStore.setUpToMonth(_state.value, month)
        loadFacets()
    }

    fun loadFacets() {
        launchGuarded {
            val facets = repo.searchFacets(_state.value.upToMonth, limit = FACET_PAGE_LIMIT + 1)
            _state.value = SearchStore.loadedFacets(_state.value, facets, limit = FACET_PAGE_LIMIT)
        }
    }

    /** 手冊 §五：top-30 之外的【顯示更多】——重查一次更大的上限，不是對 facet 做分頁（見 `SearchStore.loadedFacets` 的 KDoc）。 */
    fun showMoreFacets() {
        launchGuarded {
            val facets = repo.searchFacets(_state.value.upToMonth, limit = FACET_EXPANDED_LIMIT)
            _state.value = SearchStore.loadedFacets(_state.value, facets, limit = FACET_EXPANDED_LIMIT)
        }
    }

    fun runSearch() {
        val current = _state.value
        if (!SearchStore.canQuery(current)) return
        loadJob?.cancel()
        when (current.mode) {
            SearchMode.TAG -> {
                _state.value = SearchStore.startTagSearch(current)
                loadJob = launchGuarded { loadTagPage() }
            }
            SearchMode.TEXT -> {
                _state.value = current.copy(resultsLoading = true)
                loadJob = launchGuarded {
                    val resolved = queryResolver.resolve(current.textQuery)
                    resolvedQuery = resolved.parsed
                    _state.value = SearchStore.startTextSearch(
                        _state.value,
                        ResolvedSummary(heardTextOf(resolved.parsed), resolved.source == QuerySource.Rule),
                    )
                    loadTextPage(resolved.parsed)
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!SearchStore.canLoadMore(current)) return
        loadJob?.cancel()
        _state.value = SearchStore.startResultsLoading(current)
        loadJob = launchGuarded {
            when (current.mode) {
                SearchMode.TAG -> loadTagPage()
                SearchMode.TEXT -> resolvedQuery?.let { loadTextPage(it) }
            }
        }
    }

    /** 左上角返回鍵改條件（規格第六節）。結果與游標留著——回頭再查一次不必從頭讀。 */
    fun showConditions() { _state.value = SearchStore.showConditions(_state.value) }

    /** 首頁月份標籤點進來：條件與時間自動帶入並直接顯示結果（規格第六節）。 */
    fun seedFromHome(month: String, facet: MonthFacet) {
        loadJob?.cancel()
        _state.value = SearchState(mode = SearchMode.TAG, upToMonth = month, selected = setOf(facetKey(facet)))
        runSearch()
        loadFacets()
    }

    private suspend fun loadTagPage() {
        val current = _state.value
        val (places, tags) = placesAndTagsOf(current.selected)
        val page = repo.searchByFacets(places, tags, current.upToMonth, current.tagCursor, pageSize)
        val total = repo.searchByFacetsCount(places, tags, current.upToMonth)
        _state.value = SearchStore.appendTagPage(_state.value, page, total)
    }

    private suspend fun loadTextPage(parsed: ParsedQuery) {
        val page = repo.searchByQuery(parsed, _state.value.textCursor, pageSize)
        val total = repo.searchByQueryCount(parsed)
        _state.value = SearchStore.appendTextPage(_state.value, page, total)
    }

    /** 例外處理同 `HomeViewModel.launchGuarded`：接住例外攤回狀態，`CancellationException` 一定要重丟。 */
    private fun launchGuarded(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = SearchStore.failed(_state.value, "查詢失敗，請再試一次")
        }
    }
}
