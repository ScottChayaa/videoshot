package com.xenyaa.videoshot.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.wizard.Step3Cell
import com.xenyaa.videoshot.wizard.Step3Store
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 批次編輯的畫面狀態。**不另做一套編輯 UI**（規格第六節）——`Ready.store`／`shots` 直接餵給階段 6 的
 * `Step3DetailsScreen`；`cell` 是 [shots] 的序號（0-based），不是 shot id。
 */
sealed interface BatchEditState {
    data object Loading : BatchEditState
    data object Error : BatchEditState
    data class Ready(
        val store: Step3Store,
        val shots: List<ShotRow>,
        val places: List<String>,
        val tags: List<String>,
    ) : BatchEditState
}

/**
 * 【批次編輯圖資】：把這支影片所有 shot 載入精靈第三步的同一個介面（`Step3Store`／`Step3DetailsScreen`），
 * 改完按【完成】寫回 `library.db`、回詳情頁（規格第六節第 895 行）。
 *
 * 跟精靈本身的差異：精靈的 `WizardViewModel.finish()` 是**插入**新 shot（`commitPicks`），
 * 這裡是**修改**既有 shot（`patchShots`），所以完全是另一個 finish 實作，`Step3Store` 純粹是
 * 「勾選／抽屜／套用語意」那一層邏輯的複用，不牽涉精靈的草稿或第二步。
 */
class BatchEditViewModel(
    private val videoId: String,
    private val library: LibraryRepo,
) : ViewModel() {

    private val _state = MutableStateFlow<BatchEditState>(BatchEditState.Loading)
    val state: StateFlow<BatchEditState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>()
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    init { reload() }

    /**
     * 重新查詢這支影片的收藏，建一個全新的 [Step3Store]。**每次「真的進場」都要呼叫**，
     * 不能只靠 `init{}`：`AppRoot` 用 `videoId` 當 key 快取這個 VM（`viewModel(key = "batchedit-$videoId")`），
     * 同一支影片第二次進批次編輯拿到的是同一個實例，`init{}` 不會再跑一次。舊的 `Step3Store`
     * 還留著上一輪「套用過」的痕跡——不重建的話使用者會看到過期的綠點與圖資，按下【完成】
     * 甚至會把別處剛改好的圖資蓋回這個舊值（最終審查 Finding 1）。呼叫端是 `AppRoot.kt`
     * `Dest.BatchEdit` 分支裡的 `LaunchedEffect(Unit)`——那個 key 不能用 videoId，
     * 理由同一份 KDoc。
     *
     * 先把狀態撥回 [BatchEditState.Loading]，不然重查的這段空檔畫面會閃一下上一輪的舊資料。
     */
    fun reload() {
        _state.value = BatchEditState.Loading
        viewModelScope.launch {
            val shots = runCatching { library.shotsOfVideo(videoId) }.getOrDefault(emptyList())
            if (shots.isEmpty()) {
                _state.value = BatchEditState.Error
                return@launch
            }
            val details = shots.mapIndexed { index, shot ->
                index to ShotDetails(
                    eventDate = shot.eventDate,
                    place = shot.place,
                    description = shot.description,
                    tags = runCatching { library.tagsOfShot(shot.id) }.getOrDefault(emptyList()),
                )
            }.toMap()
            val cells = shots.mapIndexed { index, shot ->
                Step3Cell(cell = index, atSec = shot.atSec, manual = shot.source == "manual")
            }
            val store = Step3Store(cells, defaultEventDate = shots.first().eventDate)
            // restore() 只覆蓋現值,不動 applied——剛讀進來的每一格都還沒被「套用」過(同測試斷言)
            store.restore(details = details, selected = cells.map { it.cell }.toSet())
            _state.value = BatchEditState.Ready(
                store = store,
                shots = shots,
                places = runCatching { library.distinctPlaces() }.getOrDefault(emptyList()),
                tags = runCatching { library.allTagNames() }.getOrDefault(emptyList()),
            )
        }
    }

    private fun ready(): BatchEditState.Ready? = _state.value as? BatchEditState.Ready

    fun toggle(cell: Int) = ready()?.store?.toggle(cell) ?: Unit
    fun selectAll() = ready()?.store?.selectAll() ?: Unit
    fun selectNone() = ready()?.store?.selectNone() ?: Unit
    fun invert() = ready()?.store?.invert() ?: Unit
    fun selectUnapplied() = ready()?.store?.selectUnapplied() ?: Unit
    fun editEventDate(value: String) = ready()?.store?.editEventDate(value) ?: Unit
    fun editPlace(value: String) = ready()?.store?.editPlace(value) ?: Unit
    fun editDescription(value: String) = ready()?.store?.editDescription(value) ?: Unit
    fun editTags(value: List<String>) = ready()?.store?.editTags(value) ?: Unit
    fun apply() = ready()?.store?.applyPatch() ?: Unit

    /** 【完成】：只把套用過的那幾張寫回 `library.db`,回詳情頁。 */
    fun finish() {
        val ready = ready() ?: return
        val snapshot = ready.store.state.value
        viewModelScope.launch {
            for (cell in snapshot.cells) {
                val d = snapshot.details[cell.cell] ?: continue
                if (!d.applied) continue
                val shotId = ready.shots.getOrNull(cell.cell)?.id ?: continue
                runCatching {
                    library.patchShots(
                        listOf(shotId),
                        ShotPatch(
                            eventDate = d.eventDate,
                            place = d.place ?: "",
                            description = d.description ?: "",
                            tagIds = null,
                            tagNames = d.tags,
                        ),
                    )
                }
            }
            _finished.emit(Unit)
        }
    }
}
