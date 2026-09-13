package com.xenyaa.videoshot.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.url.parseVideoId
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 精靈的流程狀態。放在 ViewModel 是為了**轉螢幕不丟東西** ——
 * 已經抓好的 watch page 與已勾選的格子重來一次代價太大。
 */
class WizardViewModel(
    private val data: WizardData,
) : ViewModel() {

    private val _step = MutableStateFlow(WizardStep.URL)
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    /** 走過的最遠步驟；進度列只讓使用者點回「已完成」的步驟。 */
    private val _furthest = MutableStateFlow(WizardStep.URL)
    val furthest: StateFlow<WizardStep> = _furthest.asStateFlow()

    private val _hasDraft = MutableStateFlow(false)
    val hasDraft: StateFlow<Boolean> = _hasDraft.asStateFlow()

    fun goTo(target: WizardStep) {
        _step.value = target
        if (target.order > _furthest.value.order) _furthest.value = target
        // 一離開第一步就有草稿可留 —— 使用者已經投入了選擇
        if (target != WizardStep.URL) _hasDraft.value = true
    }

    /** 點進度列上的某一段。只有已完成的步驟能點回去。 */
    fun jumpTo(target: WizardStep) {
        if (target.order <= _furthest.value.order) _step.value = target
    }

    /**
     * 處理 Android 返回鍵。
     * @return true 代表精靈自己消化掉了；false 代表在第一步，交給系統（離開精靈）
     */
    fun back(): Boolean {
        val current = _step.value
        if (current == WizardStep.URL) return false
        _step.value = WizardStep.entries.first { it.order == current.order - 1 }
        return true
    }

    fun keepDraft() { _hasDraft.value = true }

    fun discardDraft() { _hasDraft.value = false }

    // ---- 第一步 ----

    private val _status = MutableStateFlow<Step1Status>(Step1Status.Idle)
    val status: StateFlow<Step1Status> = _status.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentVideo>>(emptyList())
    val recent: StateFlow<List<RecentVideo>> = _recent.asStateFlow()

    /** 抓好的 watch page 與已收藏格號，交給第二步用。 */
    private val _loaded = MutableStateFlow<LoadedVideo?>(null)
    val loaded: StateFlow<LoadedVideo?> = _loaded.asStateFlow()

    init {
        viewModelScope.launch { _recent.value = data.recentVideos(20) }
    }

    fun submit(input: String) {
        val videoId = parseVideoId(input)
        if (videoId == null) {
            _status.value = Step1Status.Error(
                "看不懂這個網址。可以貼 youtu.be/…、watch?v=…、shorts/… 或直接貼 11 碼影片 ID。"
            )
            return
        }
        openRecent(videoId)
    }

    fun openRecent(videoId: String) {
        _status.value = Step1Status.Loading
        viewModelScope.launch {
            val page = data.watchPage(videoId)
            when (page.result) {
                FetchResult.FETCH_FAILED ->
                    _status.value = Step1Status.Error("取圖需要網路。")

                FetchResult.VIDEO_UNAVAILABLE ->
                    _status.value = Step1Status.Error(
                        "這支影片抓不到，可能是私人影片、已被刪除或需要登入。"
                    )

                // no_storyboard 與 parse_failed **仍然進第二步** —— 縮圖牆空白，但可以截圖補上
                // （規格第五節第一步的表、第七節降級表）
                FetchResult.OK, FetchResult.NO_STORYBOARD, FetchResult.PARSE_FAILED -> {
                    _loaded.value = LoadedVideo(
                        videoId = videoId,
                        page = page,
                        takenFrameIndexes = data.takenFrameIndexes(videoId),
                    )
                    _status.value = Step1Status.Idle
                    goTo(WizardStep.PICK)
                }
            }
        }
    }
}
