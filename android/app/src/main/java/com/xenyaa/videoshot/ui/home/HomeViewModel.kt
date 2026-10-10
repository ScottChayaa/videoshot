package com.xenyaa.videoshot.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首頁的資料接線。**一次 50 筆 keyset**（規格第六節），篩選下推到 SQL。
 *
 * @param pageSize 測試會調小，正式一律 50
 * @param filterOptionsSource 抽屜候選。正式環境傳 `FacetUsage::filterOptions`（依最近使用重排，規格第六節「首頁」）；預設直接讀 repo 的張數排序。
 * @param markFacetsUsed 套用篩選時記下用到的地點與標籤（`FacetUsage.markUsed`）。盡力而為：失敗不影響套用。
 */
class HomeViewModel(
    private val repo: LibraryRepo,
    private val pageSize: Int = 50,
    private val filterOptionsSource: suspend (String?) -> List<FilterOption> = repo::filterOptions,
    private val markFacetsUsed: suspend (places: Set<String>, tags: Set<String>) -> Unit = { _, _ -> },
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /**
     * `loadMore()` 目前在跑的那次讀取。**換篩選要先取消它**——否則換月份之前發出去的那次
     * 讀取（撈的是舊篩選）晚點才回來，會把它的游標、`endReached` 一起蓋回新篩選的
     * 狀態上，多出一批不該出現的舊資料（見階段 7 全盤覆查第 3 點）。取捲動觸發的
     * `loadMore()` 與取圖完成的 `reload()` 之間也有一樣的競態，所以每次 `loadMore()`
     * 開始前都取消上一次，不只是換篩選那個路徑。
     */
    private var loadJob: Job? = null

    /** [refreshMonths] 目前在跑的讀取；換篩選時要取消，理由同 [loadJob]。 */
    private var monthsJob: Job? = null

    /**
     * [openFilter] 讀候選的那次讀取。關抽屜或再開一次都要取消它——讀取很慢時，
     * 晚到的候選不能寫進已經關掉（或已重開）的抽屜。
     */
    private var optionsJob: Job? = null

    init { reload() }

    /** 重新載入目前的篩選條件（完成取圖、刪除整支、還原備份之後都要叫）。 */
    fun reload() {
        _state.value = HomeStore.reset(_state.value, _state.value.upToMonth)
        refreshMonths()
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (!HomeStore.canLoadMore(current)) return
        // 上一次讀取如果還在跑（例如剛換了篩選），它撈的是舊條件，不能讓它晚點回來還能寫狀態
        loadJob?.cancel()
        _state.value = HomeStore.startLoading(current)
        loadJob = launchGuarded {
            val filter = current.filter
            val page = if (filter.isEmpty) {
                repo.homeFeed(current.cursor, pageSize, current.upToMonth)
            } else {
                repo.searchByFacets(filter.places, filter.tags, current.upToMonth, current.cursor, pageSize)
            }
            _state.update { HomeStore.appendPage(it, page) }
            // 篩選中不畫月份小膠囊，也就不用查
            if (filter.isEmpty) loadFacets(page.items)
        }
    }

    /** @param month null＝清除篩選（手冊 §二：狀態列的 ✕） */
    fun setFilter(month: String?) {
        _state.value = HomeStore.reset(_state.value, month)
        loadMore()
    }

    /**
     * 開篩選抽屜：草稿從目前已套用的篩選起算，候選依目前的時間範圍非同步讀進來。
     * 同一個 update 先清掉上一次的候選、標成讀取中，讀回來之前抽屜不會閃出過期的清單，
     * 也不會誤說「這段時間沒有地點」。
     */
    fun openFilter() {
        val current = _state.value
        optionsJob?.cancel()
        _state.update {
            it.copy(
                draft = FilterDraft(current.filter.places, current.filter.tags),
                filterOptions = emptyList(),
                filterOptionsStatus = FilterOptionsStatus.LOADING,
            )
        }
        loadFilterOptions(current.upToMonth)
    }

    /** 抽屜裡的【重試】：只重讀候選，草稿（勾選、搜尋字、展開）不動。 */
    fun retryFilterOptions() {
        val current = _state.value
        if (current.draft == null) return
        optionsJob?.cancel()
        _state.update { it.copy(filterOptionsStatus = FilterOptionsStatus.LOADING) }
        loadFilterOptions(current.upToMonth)
    }

    /**
     * 讀候選。失敗只標在抽屜上（[FilterOptionsStatus.FAILED]，抽屜自己有【重試】），
     * **不走 [launchGuarded]**——那會把首頁的 `error`／`loading` 一起改掉，抽屜讀不到候選
     * 不代表首頁清單有問題。`CancellationException` 照樣往外丟（關抽屜、重開會取消它）。
     */
    private fun loadFilterOptions(upToMonth: String?) {
        optionsJob = viewModelScope.launch {
            val options = try {
                filterOptionsSource(upToMonth)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(filterOptionsStatus = FilterOptionsStatus.FAILED) }
                return@launch
            }
            _state.update { it.copy(filterOptions = options, filterOptionsStatus = FilterOptionsStatus.READY) }
        }
    }

    /** 勾選或取消勾選；只改草稿，按【套用】才會重新載入。 */
    fun toggleDraft(option: FilterOption) {
        _state.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(
                draft = if (option.isPlace) {
                    draft.copy(places = draft.places.toggled(option.name))
                } else {
                    draft.copy(tags = draft.tags.toggled(option.name))
                },
            )
        }
    }

    fun setDraftQuery(isPlace: Boolean, text: String) {
        _state.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = draft.copy(query = draft.query + (isPlace to text)))
        }
    }

    /** 清空草稿的勾選；已套用的篩選不動，要按【套用】才生效。 */
    fun clearDraft() {
        _state.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = draft.copy(places = emptySet(), tags = emptySet()))
        }
    }

    fun applyFilter() {
        val draft = _state.value.draft ?: return
        apply(HomeFilter(draft.places, draft.tags))
    }

    /** 關抽屜不套用：草稿丟掉。 */
    fun dismissFilter() {
        optionsJob?.cancel()
        _state.update { it.copy(draft = null) }
    }

    /** 月份小膠囊：直接只篩這一個（時間範圍不變）。 */
    fun applySingle(option: FilterOption) {
        apply(
            if (option.isPlace) HomeFilter(places = setOf(option.name)) else HomeFilter(tags = setOf(option.name)),
        )
    }

    /** 篩選後沒有結果的空狀態用。 */
    fun clearFilter() = apply(HomeFilter())

    private fun apply(filter: HomeFilter) {
        // 換條件要先作廢進行中的讀取，否則它晚點回來會把舊條件的資料接到新清單後面
        loadJob?.cancel()
        optionsJob?.cancel() // 套用會關掉抽屜，還沒回來的候選不用再寫
        _state.update { HomeStore.withFilter(it, filter).copy(draft = null) }
        refreshMonths()
        loadMore()

        // 規格第六節「首頁」：套用（含月份小膠囊）才算使用；空篩選（清除）不算
        if (!filter.isEmpty) {
            viewModelScope.launch { runCatching { markFacetsUsed(filter.places, filter.tags) } }
        }
    }

    private fun Set<String>.toggled(name: String) = if (name in this) this - name else this + name

    fun onShotDeleted(id: Long) {
        _state.value = HomeStore.removeShot(_state.value, id)
        // 該月可能因此變成 0 張甚至整個消失，月份選擇器的選項要跟著更新，
        // 不然使用者還挑得到一個實際上已經沒有資料的月份（見階段 7 全盤覆查第 6 點）
        refreshMonths()
    }

    /**
     * 就地編輯了一張（Lightbox 或資料夾的【編輯圖資】、詳情頁）。**一律不重讀**——重讀會先把清單
     * 清空，開著的 Lightbox 看到空清單會自己關掉、沒關也會被換成前 50 張而跳到別張，首頁捲動位置
     * 也丟了（階段 17 最終審查 Important 1，裁定 G 取代裁定 E）。
     *
     * 沒篩選時就地替換就好。篩選中先就地替換，再非同步判斷這張還符不符合：不符合就從清單拿掉
     * （跟刪除一樣，Lightbox 停在下一張）。判斷讀到的時候篩選已經換了，就不動清單——那是舊篩選的答案。
     */
    fun onShotChanged(row: ShotRow) {
        _state.update { HomeStore.replace(it, row) }
        val filter = _state.value.filter
        if (filter.isEmpty) {
            // 編輯可能把日期改到別的月份，理由同上
            refreshMonths()
            return
        }
        viewModelScope.launch {
            val stillMatches = try {
                matchesFilter(row, filter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 判斷不了就留在原地（已經就地替換過）；不當成首頁的讀取失敗
                true
            }
            if (_state.value.filter != filter) return@launch
            if (!stillMatches) _state.update { HomeStore.removeShot(it, row.id) }
            // 日期可能改到別的月份、拿掉的那張可能是某個月唯一的一張
            refreshMonths()
        }
    }

    /**
     * 篩選是任一符合。`row.place` 是正式名稱、篩選的名稱也來自候選清單（正式名稱），直接比對；
     * 地點符合就不必再讀標籤。
     */
    private suspend fun matchesFilter(row: ShotRow, filter: HomeFilter): Boolean {
        if (row.place != null && row.place in filter.places) return true
        if (filter.tags.isEmpty()) return false
        return repo.tagsOfShot(row.id).any { it in filter.tags }
    }

    /** 先等 repo 再以當下狀態更新——理由見 `DetailViewModel.loadPlayerInfo`：跟 [loadMore] 同時在跑，先取快照會把剛載入的列表蓋掉。 */
    private fun refreshMonths() {
        // 換篩選時舊條件的月份清單不能晚到蓋掉新的
        monthsJob?.cancel()
        monthsJob = launchGuarded {
            val filter = _state.value.filter
            val months = if (filter.isEmpty) repo.months() else repo.monthsMatching(filter.places, filter.tags)
            _state.update { it.copy(months = months) }
        }
    }

    /** 新出現的月份才去查標籤列 —— 每捲一頁就整份重查的話，同一個月會查很多次。 */
    private fun loadFacets(newRows: List<ShotRow>) {
        val wanted = newRows.map { monthOf(it.eventDate) }.distinct() - _state.value.facets.keys
        if (wanted.isEmpty()) return
        launchGuarded {
            val loaded = wanted.associateWith { repo.monthFacets(it) }
            _state.update { it.copy(facets = it.facets + loaded) }
        }
    }

    /**
     * `viewModelScope` 的 `SupervisorJob` 不會擋下沒接住的例外 —— repo 一丟例外
     * （磁碟滿、DB 被鎖、非正常關機後的損毀），預設的例外處理器會直接讓整個 process 死掉；
     * 就算沒有，`loading` 也會卡在 true，畫面上該重試的地方永遠等不到結果。
     * 這裡接住、把錯誤攤回狀態，畫面才有東西可以顯示、使用者才按得到重試。
     *
     * `CancellationException` 一定要重丟 —— `loadMore()` 換篩選時會主動取消上一個 job，
     * 那不是失敗，是結構化並發本來的機制，吞掉的話 job 取消就再也傳不出去。
     */
    private fun launchGuarded(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = _state.value.copy(loading = false, error = "載入失敗，請再試一次")
        }
    }
}
