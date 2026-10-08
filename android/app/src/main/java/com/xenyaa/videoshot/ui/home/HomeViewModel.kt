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
 */
class HomeViewModel(
    private val repo: LibraryRepo,
    private val pageSize: Int = 50,
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
     * 同一個 update 先清掉上一次的候選，讀回來之前抽屜是空的，不會閃出過期的清單。
     */
    fun openFilter() {
        val current = _state.value
        optionsJob?.cancel()
        _state.update {
            it.copy(draft = FilterDraft(current.filter.places, current.filter.tags), filterOptions = emptyList())
        }
        optionsJob = launchGuarded {
            val options = repo.filterOptions(current.upToMonth)
            _state.update { it.copy(filterOptions = options) }
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

    fun expandDraft(isPlace: Boolean) {
        _state.update { state ->
            val draft = state.draft ?: return@update state
            state.copy(draft = draft.copy(expanded = draft.expanded + isPlace))
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
    }

    private fun Set<String>.toggled(name: String) = if (name in this) this - name else this + name

    fun onShotDeleted(id: Long) {
        _state.value = HomeStore.removeShot(_state.value, id)
        // 該月可能因此變成 0 張甚至整個消失，月份選擇器的選項要跟著更新，
        // 不然使用者還挑得到一個實際上已經沒有資料的月份（見階段 7 全盤覆查第 6 點）
        refreshMonths()
    }

    fun onShotChanged(row: ShotRow) {
        if (!_state.value.filter.isEmpty) {
            // 篩選中：ShotRow 不帶標籤，無法判斷編輯後這一張還符不符合篩選，就地替換可能把
            // 已經不符合的那張留在畫面上，一律重新載入
            reload()
            return
        }
        _state.value = HomeStore.replace(_state.value, row)
        // 編輯可能把日期改到別的月份，理由同上
        refreshMonths()
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
