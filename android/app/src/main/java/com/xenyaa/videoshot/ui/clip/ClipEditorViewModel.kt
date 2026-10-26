package com.xenyaa.videoshot.ui.clip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.core.timeline.pruneMissing
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.mediaAccessOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 編輯畫面的狀態。[loading] 為 true 代表還在讀草稿；讀完 [draft] 仍為 null 代表沒有草稿（畫面退回剪輯頁）。
 * [access] 為 null 代表還沒判定。[selected] 是選取的段落索引，[playheadMs] 是播放頭在時間軸上的位置。
 */
data class ClipEditorState(
    val loading: Boolean = true,
    val access: MediaAccess? = null,
    val draft: ClipDraft? = null,
    val playheadMs: Long = 0,
    val selected: Int? = null,
    val notice: String? = null,
) {
    private val timeline: Timeline? get() = draft?.timeline

    /** 播放頭在某段之內、離段落頭尾都夠遠。 */
    val canSplit: Boolean get() = timeline?.canSplitAt(playheadMs) == true
    val canDelete: Boolean get() = timeline?.segments?.isNotEmpty() == true
    val canUndo: Boolean get() = draft?.history?.canUndo == true
    val canRedo: Boolean get() = draft?.history?.canRedo == true
    val isEmpty: Boolean get() = timeline?.segments?.isEmpty() == true
    val overLimit: Boolean get() = draft?.let { it.timeline.durationMs > it.orientation.maxDurationMs } == true
}

class ClipEditorViewModel(private val deps: ClipDeps) : ViewModel() {

    private val _state = MutableStateFlow(ClipEditorState())
    val state: StateFlow<ClipEditorState> = _state.asStateFlow()

    /** 上一個存檔工作：存檔排隊依序做，快速連按時舊的不會晚於新的寫入。 */
    private var saveJob: Job? = null

    /**
     * 讀目前的草稿。沒有權限（不是完整也不是部分）就只記下權限、草稿原樣，不修剪也不存檔——
     * 沒權限時讀不到任何素材，修剪會把整條時間軸清空。有權限就拿手機現有的素材 id 修剪，
     * 拿掉了素材就存回並提示。
     */
    fun load() {
        viewModelScope.launch {
            val draft = deps.cacheRepo.currentClipDraft()
            if (draft == null) {
                _state.update { it.copy(loading = false, draft = null) }
                return@launch
            }
            val access = mediaAccessOf(deps.sdkInt(), deps.grantedMediaPermissions())
            if (access == MediaAccess.DENIED) {
                _state.update { it.copy(loading = false, access = access, draft = draft, playheadMs = 0, selected = null) }
                return@launch
            }
            val existing = deps.mediaLibrary.list(MediaFilter.ALL).mapTo(HashSet()) { it.id }
            val pruned = draft.pruneMissing(existing)
            val notice = if (pruned.removedSources > 0) "有 ${pruned.removedSources} 個素材已不存在，已從時間軸移除" else null
            _state.update {
                it.copy(loading = false, access = access, draft = pruned.draft, playheadMs = 0, selected = null, notice = notice)
            }
            if (pruned.removedSources > 0) save(pruned.draft)
        }
    }

    /** 在播放頭所在處把段落切成兩段。不能分割時什麼都不做。 */
    fun split() = edit { history ->
        history.current.splitAt(_state.value.playheadMs)?.let { history.apply(it) }
    }

    /** 刪掉選取的段落；沒選取就刪播放頭所在的段（播放頭在最尾端時刪最後一段）。 */
    fun delete() = edit { history ->
        val timeline = history.current
        val state = _state.value
        val index = state.selected
            ?: timeline.locate(state.playheadMs)?.first
            ?: timeline.segments.lastIndex.takeIf { it >= 0 && state.playheadMs >= timeline.durationMs }
        // Timeline.delete 遇到範圍外的索引會原樣回傳，這裡先擋掉，免得記一筆什麼都沒變的復原紀錄
        if (index == null || index !in timeline.segments.indices) null else history.apply(timeline.delete(index))
    }

    fun undo() = edit { it.undo() }

    fun redo() = edit { it.redo() }

    /** 移動播放頭（夾在 0 到總長之間）。不進復原紀錄、不存檔。 */
    fun seekTo(ms: Long) {
        _state.update { it.copy(playheadMs = ms.coerceIn(0L, it.draft?.timeline?.durationMs ?: 0L)) }
    }

    /** 選取第 [index] 段；null 或超出範圍就取消選取。不進復原紀錄、不存檔。 */
    fun select(index: Int?) {
        _state.update { state ->
            val count = state.draft?.timeline?.segments?.size ?: 0
            state.copy(selected = index?.takeIf { it in 0 until count })
        }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    /** 改草稿的編輯紀錄：[change] 回 null 代表這個操作沒有作用。成功就立刻存草稿。 */
    private fun edit(change: (EditHistory) -> EditHistory?) {
        val draft = _state.value.draft ?: return
        val next = change(draft.history) ?: return
        val edited = draft.copy(history = next)
        _state.update {
            it.copy(
                draft = edited,
                selected = null,
                playheadMs = clampPlayhead(it.playheadMs, edited.timeline),
            )
        }
        save(edited)
    }

    /** 播放頭到了（或超出）新的總長就退到最後一段的開頭；時間軸空了回 0。 */
    private fun clampPlayhead(playheadMs: Long, timeline: Timeline): Long {
        val last = timeline.segments.lastIndex
        return when {
            last < 0 -> 0L
            playheadMs >= timeline.durationMs -> timeline.startOf(last)
            else -> playheadMs
        }
    }

    private fun save(draft: ClipDraft) {
        val previous = saveJob
        saveJob = viewModelScope.launch {
            // 離開畫面（ViewModel 清掉）時已經開始的存檔也要寫完，不然最後一步編輯會丟
            withContext(NonCancellable) {
                previous?.join()
                deps.cacheRepo.saveClipDraft(draft, deps.now())
            }
        }
    }
}
