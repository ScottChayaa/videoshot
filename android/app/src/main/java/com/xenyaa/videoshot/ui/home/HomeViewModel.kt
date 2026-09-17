package com.xenyaa.videoshot.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * 讀取（撈的是舊篩選）晚點才回來，會把它的游標、`endReached`、`total` 一起蓋回新篩選的
     * 狀態上，多出一批不該出現的舊資料（見階段 7 全盤覆查第 3 點）。取捲動觸發的
     * `loadMore()` 與取圖完成的 `reload()` 之間也有一樣的競態，所以每次 `loadMore()`
     * 開始前都取消上一次，不只是換篩選那個路徑。
     */
    private var loadJob: Job? = null

    init { reload() }

    /** 重新載入目前的篩選條件（完成取圖、刪除整支、還原備份之後都要叫）。 */
    fun reload() {
        _state.value = HomeStore.reset(_state.value, _state.value.upToMonth)
        launchGuarded { _state.value = _state.value.copy(months = repo.monthCounts()) }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (!HomeStore.canLoadMore(current)) return
        // 上一次讀取如果還在跑（例如剛換了篩選），它撈的是舊條件，不能讓它晚點回來還能寫狀態
        loadJob?.cancel()
        _state.value = HomeStore.startLoading(current)
        loadJob = launchGuarded {
            val page = repo.homeFeed(current.cursor, pageSize, current.upToMonth)
            val total = repo.shotCount(current.upToMonth)
            _state.value = HomeStore.appendPage(_state.value, page, total)
            loadFacets(page.items)
        }
    }

    /** @param month null＝清除篩選（手冊 §二：狀態列的 ✕） */
    fun setFilter(month: String?) {
        _state.value = HomeStore.reset(_state.value, month)
        loadMore()
    }

    fun onShotDeleted(id: Long) {
        _state.value = HomeStore.removeShot(_state.value, id)
        // 該月可能因此變成 0 張甚至整個消失，月份選擇器的選項要跟著更新，
        // 不然使用者還挑得到一個實際上已經沒有資料的月份（見階段 7 全盤覆查第 6 點）
        refreshMonths()
    }

    fun onShotChanged(row: ShotRow) {
        _state.value = HomeStore.replace(_state.value, row)
        // 編輯可能把日期改到別的月份，理由同上
        refreshMonths()
    }

    private fun refreshMonths() {
        launchGuarded { _state.value = _state.value.copy(months = repo.monthCounts()) }
    }

    /** 新出現的月份才去查標籤列 —— 每捲一頁就整份重查的話，同一個月會查很多次。 */
    private fun loadFacets(newRows: List<ShotRow>) {
        val wanted = newRows.map { monthOf(it.eventDate) }.distinct() - _state.value.facets.keys
        if (wanted.isEmpty()) return
        launchGuarded {
            val loaded = wanted.associateWith { repo.monthFacets(it) }
            _state.value = _state.value.copy(facets = _state.value.facets + loaded)
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
