package com.xenyaa.videoshot.ui.play

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 原片播放的資料接線（規格第六節「原片播放」）。
 *
 * @param startShotId 從 Lightbox【原片播放】帶進來的那一張——播放器從它的 `at_sec` 開始播，
 *        進場方向也先照它的縮圖比例猜
 * @param watchPage 只收這一個函式,不收整個 `WizardData`——播放器可不可用只需要 watch page 這一件事
 * @param landscapeOf 這張縮圖是不是橫的；讀不到回 null（當成橫的）。縮圖照影片本身的比例裁，直式影片的圖是直的
 */
class PlayViewModel(
    val videoId: String,
    private val startShotId: Long,
    private val library: LibraryRepo,
    private val watchPage: suspend (String) -> WatchPage,
    private val landscapeOf: suspend (ShotRow) -> Boolean? = { null },
    /** 拖曳預覽的來源；沒有 storyboard 回 null。測試預設不給 */
    private val previewFor: (WatchPage) -> (suspend (Double) -> ImageBitmap?)? = { null },
) : ViewModel() {

    data class State(
        val videoId: String = "",
        val title: String = "",
        /** 進場要轉成哪個方向；null＝還在猜，先不進全螢幕，免得轉過去又轉回來 */
        val landscape: Boolean? = null,
        val startSec: Double = 0.0,
        /** 【重新取圖】確認框要點名刪幾張、含幾張截圖 */
        val shotCount: Int = 0,
        val manualCount: Int = 0,
        val player: PlayerAvailability = PlayerAvailability.Loading,
    )

    sealed interface PlayerAvailability {
        data object Loading : PlayerAvailability
        data class Ready(
            val playableInEmbed: Boolean,
            /** watch page 的總長；`<video>.duration` 還沒有值時控制層用它 */
            val durationSec: Int = 0,
            val preview: (suspend (Double) -> ImageBitmap?)? = null,
        ) : PlayerAvailability
        data class Unavailable(val message: String, val retryable: Boolean) : PlayerAvailability
    }

    private val _state = MutableStateFlow(State(videoId = videoId))
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: Player? = null

    init {
        loadLibrary()
        loadPlayerInfo()
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            // 圖庫讀不到不擋播放：標題退回 videoId、從頭播、當成橫的
            val shots = try {
                library.shotsOfVideo(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val title = runCatching { library.videoById(videoId)?.title }.getOrNull() ?: videoId
            val start = shots.firstOrNull { it.id == startShotId }
            val landscape = start?.let { runCatching { landscapeOf(it) }.getOrNull() } ?: true
            _state.update {
                it.copy(
                    title = title,
                    landscape = landscape,
                    startSec = start?.atSec ?: 0.0,
                    shotCount = shots.size,
                    manualCount = shots.count { s -> s.source == "manual" },
                )
            }
            // 播放器比圖庫先好的話，那時還不知道從哪裡播，這裡補跳一次
            player?.let { p -> p.seekTo(_state.value.startSec); p.play() }
        }
    }

    fun loadPlayerInfo() {
        _state.update { it.copy(player = PlayerAvailability.Loading) }
        viewModelScope.launch {
            // 先等網路、再以「當下」的狀態更新——不能寫成 `_state.value = _state.value.copy(player = …watchPage…)`：
            // Kotlin 會先取那時的舊快照才去等網路，回來後整份寫回，把圖庫讀取早就寫好的值全蓋掉
            // （2026-10-01 實機「永遠正在載入」）
            val page = watchPage(videoId)
            val availability = availabilityOf(page).let {
                if (it is PlayerAvailability.Ready) it.copy(preview = previewFor(page)) else it
            }
            _state.update { it.copy(player = availability) }
        }
    }

    fun attachPlayer(p: Player) {
        player = p
        // 秒數在 coroutine 裡才讀：圖庫可能在這之前剛讀完，先讀的話會拿到預設的 0 秒、把圖庫那次的跳播蓋掉
        viewModelScope.launch { p.seekTo(_state.value.startSec); p.play() }
    }

    fun detachPlayer() { player = null }
}

/**
 * watch page 的抓取結果換成播放器可不可用。
 *
 * `meta` 有值就能播（`OK`／`NO_STORYBOARD` 都有 meta，單純播放不需要 storyboard）；
 * 沒有 meta 時依失敗種類給不同文案——`FETCH_FAILED` 是網路問題,值得給【重試】。
 */
internal fun availabilityOf(page: WatchPage): PlayViewModel.PlayerAvailability {
    page.meta?.let { return PlayViewModel.PlayerAvailability.Ready(it.playableInEmbed, durationSec = it.durationSec) }
    return when (page.result) {
        FetchResult.FETCH_FAILED, FetchResult.RATE_LIMITED ->
            PlayViewModel.PlayerAvailability.Unavailable("沒有網路，無法播放。", retryable = true)
        else ->
            PlayViewModel.PlayerAvailability.Unavailable(
                "這支影片抓不到，可能是私人影片、已被刪除或需要登入。", retryable = false,
            )
    }
}
