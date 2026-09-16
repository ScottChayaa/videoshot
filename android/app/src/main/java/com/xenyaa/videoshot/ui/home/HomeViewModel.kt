package com.xenyaa.videoshot.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.ShotRow
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

    init { reload() }

    /** 重新載入目前的篩選條件（完成取圖、刪除整支、還原備份之後都要叫）。 */
    fun reload() {
        _state.value = HomeStore.reset(_state.value, _state.value.upToMonth)
        viewModelScope.launch {
            _state.value = _state.value.copy(months = repo.monthCounts())
        }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (!HomeStore.canLoadMore(current)) return
        _state.value = HomeStore.startLoading(current)
        viewModelScope.launch {
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
    }

    fun onShotChanged(row: ShotRow) {
        _state.value = HomeStore.replace(_state.value, row)
    }

    /** 新出現的月份才去查標籤列 —— 每捲一頁就整份重查的話，同一個月會查很多次。 */
    private fun loadFacets(newRows: List<ShotRow>) {
        val wanted = newRows.map { monthOf(it.eventDate) }.distinct() - _state.value.facets.keys
        if (wanted.isEmpty()) return
        viewModelScope.launch {
            val loaded = wanted.associateWith { repo.monthFacets(it) }
            _state.value = _state.value.copy(facets = _state.value.facets + loaded)
        }
    }
}
