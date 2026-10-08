package com.xenyaa.videoshot.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.query.ParsedQuery
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.query.QuerySource
import com.xenyaa.videoshot.query.QueryResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * 標籤模式「一次搜尋」全程固定用的條件——跟文字模式的 [TextCriteria] 同一個理由：
 * `loadMore()` 續頁不能重新從 `state.selected` 拆一次，不然中途一次 facet 重新載入
 * 把 `selected` 修剪掉,會讓第 2 頁跟第 1 頁用不同條件查、卻接在同一個游標之後。
 */
private data class TagCriteria(val places: Set<String>, val tags: Set<String>, val upToMonth: String?)

/**
 * 文字模式「一次搜尋」全程固定用的條件——跟 [TagCriteria] 同一個理由：`loadMore()` 續頁
 * 不能重新讀 `state.upToMonth`，不然使用者在還沒捲完的時候回條件畫面改了時間篩選，
 * 第 2 頁會跟第 1 頁用不同的時間範圍查、卻接在同一個游標之後（最終審查 Important 5，
 * 跟這個階段已經抓到兩次的 `tagCriteria` 是同一類錯，這裡不能再犯一次）。
 */
private data class TextCriteria(val parsed: ParsedQuery, val upToMonth: String?)

/**
 * 查詢分頁的資料接線（規格第六節「查詢」、第八節「檢索」）。
 * **階段 17 起沒有入口**（底部導覽拿掉了查詢分頁，改由首頁篩選），程式碼保留待日後接回。
 * @param pageSize 測試會調小，正式一律 50
 */
class SearchViewModel(
    private val repo: LibraryRepo,
    private val queryResolver: QueryResolver,
    private val pageSize: Int = 50,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** 文字模式往下捲要延續同一次解析結果與時間篩選，不能每捲一頁就重新問一次 Gemini
     * 或重新讀當下的 `upToMonth`（見 [TextCriteria] 的 KDoc）。 */
    private var textCriteria: TextCriteria? = null

    /** 標籤模式往下捲要延續同一次的地點／標籤／時間條件——跟 [textCriteria] 同一個理由（見 [TagCriteria]）。 */
    private var tagCriteria: TagCriteria? = null

    /** 換條件前一次還在跑的讀取要先取消——理由同 `HomeViewModel.loadJob`。 */
    private var loadJob: Job? = null

    /** facet 候選清單的讀取——跟 [loadJob] 分開追蹤，換月份時前一次還在跑的查詢要先取消，
     * 不然回應晚到的那個會覆蓋掉新查詢的結果（`setUpToMonth` 連續呼叫兩次就會踩到）。 */
    private var facetsJob: Job? = null

    init {
        loadFacets()
        // 日期選擇器的選項，跟首頁看的是同一份「有收藏的月份」清單，只查一次不必跟著時間篩選重查
        // 先等 repo 再以當下狀態更新——跟 loadFacets() 同時在跑，理由見 DetailViewModel.loadPlayerInfo
        launchGuarded {
            val months = repo.monthCounts()
            _state.update { it.copy(months = months) }
        }
    }

    fun setMode(mode: SearchMode) { _state.value = SearchStore.setMode(_state.value, mode) }

    fun setTextQuery(text: String) { _state.value = SearchStore.setTextQuery(_state.value, text) }

    fun toggleFacet(facet: MonthFacet) { _state.value = SearchStore.toggleFacet(_state.value, facet) }

    fun setUpToMonth(month: String?) {
        _state.value = SearchStore.setUpToMonth(_state.value, month)
        loadFacets()
    }

    fun loadFacets() {
        facetsJob?.cancel()
        facetsJob = launchGuarded {
            val facets = repo.searchFacets(_state.value.upToMonth, limit = FACET_PAGE_LIMIT + 1)
            _state.value = SearchStore.loadedFacets(_state.value, facets, limit = FACET_PAGE_LIMIT)
        }
    }

    /** 手冊 §五：top-30 之外的【顯示更多】——重查一次更大的上限，不是對 facet 做分頁（見 `SearchStore.loadedFacets` 的 KDoc）。 */
    fun showMoreFacets() {
        facetsJob?.cancel()
        facetsJob = launchGuarded {
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
                val (places, tags) = placesAndTagsOf(current.selected)
                tagCriteria = TagCriteria(places, tags, current.upToMonth)
                _state.value = SearchStore.startTagSearch(current)
                loadJob = launchGuarded { loadTagPage(places, tags, current.upToMonth) }
            }
            SearchMode.TEXT -> {
                _state.value = current.copy(resultsLoading = true)
                loadJob = launchGuarded {
                    val resolved = queryResolver.resolve(current.textQuery)
                    textCriteria = TextCriteria(resolved.parsed, current.upToMonth)
                    _state.value = SearchStore.startTextSearch(
                        _state.value,
                        ResolvedSummary(heardTextOf(resolved.parsed), resolved.source == QuerySource.Rule),
                    )
                    loadTextPage(resolved.parsed, current.upToMonth)
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!SearchStore.canLoadMore(current)) return
        loadJob?.cancel()
        _state.value = SearchStore.startResultsLoading(current)
        when (current.mode) {
            SearchMode.TAG -> {
                loadJob = launchGuarded {
                    tagCriteria?.let { (places, tags, upToMonth) -> loadTagPage(places, tags, upToMonth) }
                }
            }
            SearchMode.TEXT -> {
                loadJob = launchGuarded {
                    textCriteria?.let { (parsed, upToMonth) -> loadTextPage(parsed, upToMonth) }
                }
            }
        }
    }

    /** 左上角返回鍵改條件（規格第六節）。結果與游標留著——回頭再查一次不必從頭讀。 */
    fun showConditions() { _state.value = SearchStore.showConditions(_state.value) }

    /** 首頁月份標籤點進來：條件與時間自動帶入並直接顯示結果（規格第六節）。 */
    fun seedFromHome(month: String, facet: MonthFacet) {
        loadJob?.cancel()
        _state.value = SearchState(
            mode = SearchMode.TAG,
            upToMonth = month,
            selected = setOf(facetKey(facet)),
            months = _state.value.months,
        )
        runSearch()
        loadFacets()
    }

    /**
     * 從 Lightbox 刪掉一張，查詢結果要同步（見 Task 12 覆查 Important 1）——不然使用者在
     * 查詢分頁開的 Lightbox 刪除之後，那張圖還留在清單裡、`total` 也還沒少一張，
     * 刪到最後一張時 Lightbox 也不會自動關掉（跟 `HomeViewModel.onShotDeleted` 同一個理由）。
     */
    fun onShotDeleted(id: Long) {
        _state.value = SearchStore.removeShot(_state.value, id)
    }

    /**
     * 從詳情頁【刪除整支收藏】之後同步查詢結果，理由同 [onShotDeleted]——差別是這裡一次
     * 可能要拔掉好幾張（同一支影片的好幾個 shot 都在查詢結果裡），見 `SearchStore.removeVideo`
     * 的 KDoc（最終審查 Important 4）。
     */
    fun onVideoDeleted(videoId: String) {
        _state.value = SearchStore.removeVideo(_state.value, videoId)
    }

    /**
     * 就地編輯之後同步查詢結果，理由同 [onShotDeleted]。標籤模式會依日期重新排序，
     * 文字模式的相關度排序限制見 `SearchStore.replace` 的 KDoc。
     */
    fun onShotChanged(row: ShotRow) {
        _state.value = SearchStore.replace(_state.value, row)
    }

    private suspend fun loadTagPage(places: Set<String>, tags: Set<String>, upToMonth: String?) {
        val page = repo.searchByFacets(places, tags, upToMonth, _state.value.tagCursor, pageSize)
        val total = repo.searchByFacetsCount(places, tags, upToMonth)
        _state.value = SearchStore.appendTagPage(_state.value, page, total)
    }

    private suspend fun loadTextPage(parsed: ParsedQuery, upToMonth: String?) {
        val page = repo.searchByQuery(parsed, upToMonth, _state.value.textCursor, pageSize)
        val total = repo.searchByQueryCount(parsed, upToMonth)
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
