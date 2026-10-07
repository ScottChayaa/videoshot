package com.xenyaa.videoshot.ui.search

import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.RESULT_COUNT_CAP
import com.xenyaa.videoshot.data.repo.model.SearchPage
import com.xenyaa.videoshot.data.repo.model.ShotRow

private const val FACET_LIMIT = 30

/** 查詢分頁的純狀態轉換。沒有任何 suspend、沒有 Android 相依——跟 `HomeStore` 同一個理由。 */
object SearchStore {

    fun canQuery(state: SearchState): Boolean = when (state.mode) {
        SearchMode.TAG -> state.selected.isNotEmpty()
        SearchMode.TEXT -> state.textQuery.isNotBlank()
    }

    fun disabledReason(state: SearchState): String? {
        if (canQuery(state)) return null
        return when (state.mode) {
            SearchMode.TAG -> "先選一個以上的標籤或地點"
            SearchMode.TEXT -> "請輸入想找的描述"
        }
    }

    fun queryButtonLabel(state: SearchState): String = when (state.mode) {
        SearchMode.TAG -> if (state.selected.isEmpty()) "查詢" else "查詢 ${state.selected.size} 個條件"
        SearchMode.TEXT -> "查詢"
    }

    fun setMode(state: SearchState, mode: SearchMode): SearchState = state.copy(mode = mode)

    fun setTextQuery(state: SearchState, text: String): SearchState = state.copy(textQuery = text)

    fun toggleFacet(state: SearchState, facet: MonthFacet): SearchState {
        val key = facetKey(facet)
        val next = if (key in state.selected) state.selected - key else state.selected + key
        return state.copy(selected = next)
    }

    /** 換時間範圍：清掉舊的 facets（等重新載入），選取先留著，等 [loadedFacets] 回來再修剪。 */
    fun setUpToMonth(state: SearchState, month: String?): SearchState =
        state.copy(upToMonth = month, facets = emptyList(), facetsHasMore = false, facetsLoading = true)

    fun startFacetsLoading(state: SearchState): SearchState = state.copy(facetsLoading = true)

    /**
     * facet 載入完成：只留前 [limit] 筆（呼叫端傳 `limit+1` 探測「顯示更多」），並修掉不在新池子裡
     * 的選取——換了時間範圍，舊選的 chip 可能已經不在候選清單裡（跟 mockup `renderTags()` 的
     * pruning 同一個理由）。
     *
     * @param limit 一般查詢用 [FACET_LIMIT]（top-30）；使用者按【顯示更多】時 `SearchViewModel`
     *        改傳一個大很多的值重新查一次——不是對 facet 清單做真正的 keyset 分頁
     *        （規格只要求「top-30 + 顯示更多」，facet 本身的候選數量在個人圖庫規模下不需要分頁）
     */
    fun loadedFacets(state: SearchState, facets: List<MonthFacet>, limit: Int = FACET_LIMIT): SearchState {
        val trimmed = facets.take(limit)
        val keys = trimmed.map { facetKey(it) }.toSet()
        return state.copy(
            facetsLoading = false,
            facets = trimmed,
            facetsHasMore = facets.size > limit,
            selected = state.selected.filter { it in keys }.toSet(),
        )
    }

    fun startTagSearch(state: SearchState): SearchState = state.copy(
        phase = SearchPhase.RESULTS, results = emptyList(), tagCursor = null,
        endReached = false, error = null, heard = null, resultsLoading = true,
    )

    fun startTextSearch(state: SearchState, heard: ResolvedSummary): SearchState = state.copy(
        phase = SearchPhase.RESULTS, results = emptyList(), textCursor = null,
        endReached = false, error = null, heard = heard, resultsLoading = true,
    )

    fun startResultsLoading(state: SearchState): SearchState = state.copy(resultsLoading = true)

    fun appendTagPage(state: SearchState, page: Page<ShotRow>, total: Int): SearchState = state.copy(
        results = state.results + page.items, tagCursor = page.next,
        endReached = page.next == null, resultsLoading = false, total = total, totalCapped = total > RESULT_COUNT_CAP, error = null,
    )

    fun appendTextPage(state: SearchState, page: SearchPage, total: Int): SearchState = state.copy(
        results = state.results + page.items, textCursor = page.next,
        endReached = page.next == null, resultsLoading = false, total = total, totalCapped = total > RESULT_COUNT_CAP, error = null,
    )

    /** 左上角返回鍵改條件（規格第六節）：回到條件畫面，選取與輸入都保留，不是重新開始。 */
    fun showConditions(state: SearchState): SearchState = state.copy(phase = SearchPhase.CONDITIONS)

    fun canLoadMore(state: SearchState): Boolean =
        state.phase == SearchPhase.RESULTS && !state.resultsLoading && !state.endReached

    fun failed(state: SearchState, message: String): SearchState =
        state.copy(resultsLoading = false, facetsLoading = false, error = message)

    /**
     * 從查詢結果就地拔掉一張（刪除同步，見 Task 12 覆查 Important 1）。
     * 跟 `HomeStore.removeShot` 同一個理由：總數要一起減，否則 Lightbox 的「共 M 張」
     * 停在刪除前的數字；清單也要一起拔掉，否則使用者滑回去還看得到那張已經不存在的圖。
     * 張數已達上限（`totalCapped`）時真正的總數不知道，`total` 不動，免得 1001 扣成「1000 張」騙人。
     */
    fun removeShot(state: SearchState, id: Long): SearchState {
        if (state.results.none { it.id == id }) return state
        return state.copy(
            results = state.results.filterNot { it.id == id },
            total = if (state.totalCapped) state.total else (state.total - 1).coerceAtLeast(0),
        )
    }

    /**
     * 刪掉整支收藏之後從查詢結果就地拔掉那支影片的所有列（最終審查 Important 4）。
     * 跟 [removeShot] 同一個理由，差別是一支影片可能同時有好幾張在查詢結果裡，
     * `total` 要照實際拔掉的張數扣，不能假設只有一張。
     */
    fun removeVideo(state: SearchState, videoId: String): SearchState {
        val removed = state.results.count { it.videoId == videoId }
        if (removed == 0) return state
        return state.copy(
            results = state.results.filterNot { it.videoId == videoId },
            total = if (state.totalCapped) state.total else (state.total - removed).coerceAtLeast(0),
        )
    }

    /**
     * 就地編輯之後換掉一張（編輯同步，見 Task 12 覆查 Important 1）。
     *
     * **只有 [SearchMode.TAG] 重新排序**——那個模式的順序是日期（跟 `HomeStore.replace`
     * 同一個理由：改了日期那張圖要換到正確的位置，不然使用者會覺得清單亂掉了）。
     * [SearchMode.TEXT] 的順序是查詢當下算出來的相關度，不是日期，這裡刻意不重新排序：
     * 編輯有可能改到影響相關度的欄位（例如說明、地點），理論上該重新排序甚至換名次，
     * 但那需要拿新內容重新問一次查詢引擎（甚至重新問 Gemini）才排得出新名次，
     * 這個修正只保證「資料本身是新的、畫面不會顯示編輯前的舊圖資」，不解決文字模式的
     * 相關度重新排序——這是已知的限制，留到真的需要時再處理。
     */
    fun replace(state: SearchState, row: ShotRow): SearchState {
        if (state.results.none { it.id == row.id }) return state
        val updated = state.results.map { if (it.id == row.id) row else it }
        val ordered = if (state.mode == SearchMode.TAG) {
            updated.sortedWith(compareByDescending<ShotRow> { it.eventDate }.thenByDescending { it.id })
        } else {
            updated
        }
        return state.copy(results = ordered)
    }
}
