package com.xenyaa.videoshot.ui.clip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.mediaAccessOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 選素材畫面的狀態。[access] 為 null 代表還沒判定；[items] 為 null 代表讀取中（或沒權限、沒讀過）。
 * [selected] 依點選順序排列，序號就是它在清單裡的位置加一。
 */
data class ClipPickerState(
    val access: MediaAccess? = null,
    val filter: MediaFilter = MediaFilter.ALL,
    val items: List<MediaItem>? = null,
    val selected: List<Long> = emptyList(),
)

class ClipPickerViewModel(private val deps: ClipDeps) : ViewModel() {

    private val _state = MutableStateFlow(ClipPickerState())
    val state: StateFlow<ClipPickerState> = _state.asStateFlow()

    private var readJob: Job? = null

    /** 讀過的素材各是照片還是影片：只讀單一分頁時，靠它判斷「沒出現在這個分頁」是被刪了還是本來就屬於別的分頁。 */
    private val kinds = mutableMapOf<Long, MediaKind>()

    init {
        refreshAccess()
    }

    /**
     * 重新判定權限。有權限（完整或部分）就重讀目前分頁——涵蓋剛從設定改成允許、
     * 部分允許的範圍變了、素材被別的 app 刪了；沒權限就不讀、清單維持原樣。
     * 重讀後，已選但已不在清單裡的素材會從 [ClipPickerState.selected] 移除。
     */
    fun refreshAccess() {
        val access = mediaAccessOf(deps.sdkInt(), deps.grantedMediaPermissions())
        _state.update { it.copy(access = access) }
        if (access != MediaAccess.DENIED) load(prune = true)
    }

    fun setFilter(filter: MediaFilter) {
        _state.update { it.copy(filter = filter, items = null) }
        if (_state.value.access != MediaAccess.DENIED) load(prune = false)
    }

    fun toggle(id: Long) {
        _state.update { state ->
            val selected = if (id in state.selected) state.selected - id else state.selected + id
            state.copy(selected = selected)
        }
    }

    /** 1 起算的選取序號；沒選回 null。 */
    fun orderOf(id: Long): Int? = _state.value.selected.indexOf(id).takeIf { it >= 0 }?.plus(1)

    /** 從選方向重新進來時呼叫：清空選取、回到「全部」。 */
    fun reset() {
        _state.update { it.copy(filter = MediaFilter.ALL, items = null, selected = emptyList()) }
        if (_state.value.access != MediaAccess.DENIED) load(prune = false) else readJob?.cancel()
    }

    private fun load(prune: Boolean) {
        readJob?.cancel()
        val filter = _state.value.filter
        readJob = viewModelScope.launch {
            val list = deps.mediaLibrary.list(filter)
            list.forEach { kinds[it.id] = it.kind }
            val present = list.mapTo(HashSet()) { it.id }
            val filterKind = filter.kind()
            _state.update { state ->
                if (state.filter != filter) return@update state
                // 只有「這個分頁讀得到它卻沒讀到」才算被刪：全部分頁看得到所有素材；
                // 單一分頁只確定同種類的素材，種類沒讀過的先留著
                val selected = if (!prune) state.selected else state.selected.filter { id ->
                    id in present || !(filterKind == null || kinds[id] == filterKind)
                }
                state.copy(items = list, selected = selected)
            }
        }
    }

    private fun MediaFilter.kind(): MediaKind? = when (this) {
        MediaFilter.ALL -> null
        MediaFilter.VIDEO -> MediaKind.VIDEO
        MediaFilter.PHOTO -> MediaKind.PHOTO
    }
}
