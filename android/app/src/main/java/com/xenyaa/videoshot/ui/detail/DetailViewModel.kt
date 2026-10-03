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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
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
        /** 聚焦那張的標籤名 → kind（`tag.kind` 原始字串），圖資卡的小膠囊用來上色；對不到的當 other。 */
        val focusedTagKinds: Map<String, String> = emptyMap(),
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

    /**
     * 圖資讀取失敗、刪除單張、編輯之後都要重查（同 `HomeViewModel.reload` 的角色）。
     *
     * @param focusShotId 非 null 時代表「使用者剛剛明確要求聚焦這一張」——例如重新進入詳情頁
     * （`AppRoot.kt` 的 `Dest.Detail` 分支 `LaunchedEffect(Unit)` 帶進來目前導覽目的地的
     * `focusShotId`）。這個值優先於「盡量保留原本聚焦」的邏輯，因為 `AppRoot` 用 videoId
     * 當 key 快取這個 VM，同一支影片再進一次詳情頁拿到的是同一個實例，`initialFocusShotId`
     * 建構參數只在第一次建構時有意義，不這樣做的話「Lightbox 播另一張同一支影片的截圖」
     * 會誤聚焦到上一次那張、播放器也會跳去錯的秒數（最終審查 Finding 2）。
     *
     * 不帶這個參數（例如批次編輯完成、編輯 sheet 存檔後呼叫）——單純資料重查，
     * 維持原本聚焦不變，這是多數呼叫端要的行為。
     */
    fun reload(focusShotId: Long? = null): Unit = run {
        launchGuarded {
            val shots = library.shotsOfVideo(videoId)
            val title = library.videoById(videoId)?.title ?: videoId
            // 有明確指定的話用它，不然沿用目前聚焦；這支影片還有那一張就留著聚焦，
            // 不然退回第一張——例如指定的／原本聚焦的那張被別處刪掉了
            val wanted = focusShotId ?: _state.value.focusedShotId
            val keepFocus = wanted?.takeIf { id -> shots.any { it.id == id } }
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
            // 先等網路、再以「當下」的狀態更新——不能寫成 `_state.value = _state.value.copy(player = …watchPage…)`：
            // Kotlin 會先取那時的舊快照（loading 還是 true）才去等網路，回來後整份寫回，
            // 把圖資讀取早就寫好的 loading = false／shots／title 全蓋掉（2026-10-01 實機「永遠正在載入」）
            val availability = availabilityOf(watchPage(videoId))
            _state.update { it.copy(player = availability) }
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
            _state.update { it.copy(focusedTags = emptyList(), focusedTagKinds = emptyMap()) }
            return
        }
        // 輔助性質的讀取(跟 AppRoot.EditingSheet 的 produceState 同一個取捨)：查不到就沒有建議,
        // 不該讓整頁跳錯誤
        tagsJob = viewModelScope.launch {
            val tags = runCatching { library.tagsOfShot(id) }.getOrDefault(emptyList())
            // 種類同樣是輔助資訊：讀不到就空 map（小膠囊退回 other 色），標籤本身照樣顯示。
            // 先全部算好再一次 update，不把 suspend 呼叫寫進 copy(…)（2026-10-02 的慣例）
            val kinds = if (tags.isEmpty()) emptyMap() else {
                val kindByName = runCatching { library.allTagsWithUsage() }.getOrDefault(emptyList())
                    .associate { it.name to it.kind }
                tags.mapNotNull { name -> kindByName[name]?.let { name to it } }.toMap()
            }
            // runCatching 會把取消例外也吞掉；被取消的舊工作（連點兩張圖）不能再寫入過期的標籤
            ensureActive()
            _state.update { it.copy(focusedTags = tags, focusedTagKinds = kinds) }
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
        FetchResult.FETCH_FAILED, FetchResult.RATE_LIMITED ->
            DetailViewModel.PlayerAvailability.Unavailable("沒有網路，無法播放。", retryable = true)
        else ->
            DetailViewModel.PlayerAvailability.Unavailable(
                "這支影片抓不到，可能是私人影片、已被刪除或需要登入。", retryable = false,
            )
    }
}
