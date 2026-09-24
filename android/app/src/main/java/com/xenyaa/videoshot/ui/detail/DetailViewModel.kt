package com.xenyaa.videoshot.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 詳情頁的資料接線（規格第六節「詳情 `/v/[videoId]`」）。
 *
 * @param initialFocusShotId 從 Lightbox【播放這一段】帶進來的那一張——播放器要從它的 `at_sec` 開始播
 * @param watchPage 只收這一個函式,不收整個 `WizardData`——播放器可不可用只需要 watch page 這一件事,
 *        窄依賴讓測試不必假一整套精靈用得到的介面
 */
class DetailViewModel(
    val videoId: String,
    initialFocusShotId: Long,
    private val library: LibraryRepo,
    private val watchPage: suspend (String) -> WatchPage,
) : ViewModel() {

    data class State(
        val videoId: String = "",
        val loading: Boolean = true,
        val title: String = "",
        val shots: List<ShotRow> = emptyList(),
        val focusedShotId: Long? = null,
        val focusedTags: List<String> = emptyList(),
        val player: PlayerAvailability = PlayerAvailability.Loading,
        val error: String? = null,
    ) {
        val focused: ShotRow? get() = shots.firstOrNull { it.id == focusedShotId }

        /** 刪除確認框要點名「含幾張截圖」（規格第六節）。 */
        val manualCount: Int get() = shots.count { it.source == "manual" }
    }

    sealed interface PlayerAvailability {
        data object Loading : PlayerAvailability
        data class Ready(val playableInEmbed: Boolean) : PlayerAvailability
        data class Unavailable(val message: String, val retryable: Boolean) : PlayerAvailability
    }

    private val _state = MutableStateFlow(State(videoId = videoId, focusedShotId = initialFocusShotId))
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: Player? = null
    private var tagsJob: Job? = null

    init {
        reload()
        loadPlayerInfo()
    }

    /** 圖資讀取失敗、刪除單張、編輯之後都要重查（同 `HomeViewModel.reload` 的角色）。 */
    fun reload(): Unit = run {
        launchGuarded {
            val shots = library.shotsOfVideo(videoId)
            val title = library.videoById(videoId)?.title ?: videoId
            // 這支影片還有那一張就留著聚焦,不然退回第一張——例如剛好聚焦的那張被別處刪掉了
            val keepFocus = _state.value.focusedShotId?.takeIf { id -> shots.any { it.id == id } }
            _state.value = _state.value.copy(
                loading = false,
                title = title,
                shots = shots,
                error = null,
                focusedShotId = keepFocus ?: shots.firstOrNull()?.id,
            )
            loadFocusedTags()
        }
    }

    fun loadPlayerInfo() {
        _state.value = _state.value.copy(player = PlayerAvailability.Loading)
        viewModelScope.launch {
            _state.value = _state.value.copy(player = availabilityOf(watchPage(videoId)))
        }
    }

    fun attachPlayer(p: Player) {
        player = p
        val sec = _state.value.focused?.atSec ?: return
        viewModelScope.launch { player?.seekTo(sec); player?.play() }
    }

    fun detachPlayer() { player = null }

    /** 縮圖網格點一下＝跳播（手冊 §七第二條）。 */
    fun focus(shot: ShotRow) {
        _state.value = _state.value.copy(focusedShotId = shot.id)
        loadFocusedTags()
        viewModelScope.launch { player?.seekTo(shot.atSec); player?.play() }
    }

    /** Lightbox／詳情頁共用的編輯 sheet 存檔後呼叫——只換掉那一張,不必整頁重查。 */
    fun onShotChanged(row: ShotRow) {
        _state.value = _state.value.copy(shots = _state.value.shots.map { if (it.id == row.id) row else it })
        if (row.id == _state.value.focusedShotId) loadFocusedTags()
    }

    private fun loadFocusedTags() {
        tagsJob?.cancel()
        val id = _state.value.focusedShotId
        if (id == null) {
            _state.value = _state.value.copy(focusedTags = emptyList())
            return
        }
        // 輔助性質的讀取(跟 AppRoot.EditingSheet 的 produceState 同一個取捨)：查不到就沒有建議,
        // 不該讓整頁跳錯誤
        tagsJob = viewModelScope.launch {
            _state.value = _state.value.copy(focusedTags = runCatching { library.tagsOfShot(id) }.getOrDefault(emptyList()))
        }
    }

    private fun launchGuarded(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = _state.value.copy(loading = false, error = "讀取失敗，請再試一次")
        }
    }
}

/**
 * watch page 的抓取結果換成播放器可不可用。
 *
 * 規格第六節：「影片被刪或轉私人時播放器失效,但圖資與縮圖仍在,頁面不得整頁失敗」——
 * 所以這裡只影響 [DetailViewModel.State.player],不影響 `shots`/`title` 那半邊。
 * `meta` 有值就能播（`OK`／`NO_STORYBOARD` 都有 meta,詳情頁不需要 storyboard,單純播放足夠）；
 * 沒有 meta 時依失敗種類給不同文案——`FETCH_FAILED` 是網路問題,值得給【重試】。
 */
internal fun availabilityOf(page: WatchPage): DetailViewModel.PlayerAvailability {
    page.meta?.let { return DetailViewModel.PlayerAvailability.Ready(it.playableInEmbed) }
    return when (page.result) {
        FetchResult.FETCH_FAILED ->
            DetailViewModel.PlayerAvailability.Unavailable("沒有網路，無法播放。", retryable = true)
        else ->
            DetailViewModel.PlayerAvailability.Unavailable(
                "這支影片抓不到，可能是私人影片、已被刪除或需要登入。", retryable = false,
            )
    }
}
