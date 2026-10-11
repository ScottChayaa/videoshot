package com.xenyaa.videoshot.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.settings.ShellSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 清單頁的資料接線。排序偏好存 DataStore，所以它是**流進來的**，不是這裡的欄位。
 */
class FoldersViewModel(
    private val repo: LibraryRepo,
    private val settings: ShellSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(FoldersState())
    val state: StateFlow<FoldersState> = _state.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** 封存／取消封存的結果提示，`AppRoot` 收來顯示 snackbar（同一個 VM 服務兩個畫面）。 */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    init {
        viewModelScope.launch {
            settings.folderSort.collect { sort -> _state.value = _state.value.copy(sort = sort) }
        }
        reload()
    }

    /** 建立／改名／刪除之後，以及從資料夾頁回來時都要叫 —— 張數與預覽可能都變了。 */
    fun reload() {
        _state.value = _state.value.copy(loading = true)
        launchGuarded {
            val cards = repo.folderCards(null)
            _state.value = _state.value.copy(cards = cards, loading = false, error = null)
        }
    }

    fun setQuery(query: String) { _state.value = _state.value.copy(query = query) }

    fun setSearching(searching: Boolean) { _state.value = FoldersStore.setSearching(_state.value, searching) }

    fun setSort(sort: FolderSort) {
        // 狀態不在這裡改：寫進 DataStore 之後由 init 的 collect 流回來，只有一個真相來源
        launchGuarded { settings.setFolderSort(sort) }
    }

    fun startCreate() { _state.value = FoldersStore.startCreate(_state.value) }
    fun startRename(card: FolderCard) { _state.value = FoldersStore.startRename(_state.value, card) }
    fun editName(name: String) { _state.value = FoldersStore.editName(_state.value, name) }
    fun dismissEditor() { _state.value = FoldersStore.closeEditor(_state.value) }
    fun askDelete(card: FolderCard) { _state.value = FoldersStore.askDelete(_state.value, card) }
    fun dismissDelete() { _state.value = FoldersStore.closeDelete(_state.value) }

    fun create(name: String) = withNameRules { repo.createFolder(null, name) }

    fun rename(id: Long, name: String) = withNameRules { repo.renameFolder(id, name) }

    fun delete(card: FolderCard) {
        launchGuarded {
            repo.deleteFolder(card.id)
            _state.value = FoldersStore.closeDelete(_state.value)
            reload()
        }
    }

    fun archive(card: FolderCard) = setArchived(card, archived = true)

    fun unarchive(card: FolderCard) = setArchived(card, archived = false)

    /**
     * 寫完重查：卡片會從目前的畫面消失、出現在另一個畫面（兩邊都讀同一份 `cards`）。
     * 失敗只發提示，不寫進 `error`——那是「清單讀不到」的錯誤列，這裡清單本身沒問題。
     */
    private fun setArchived(card: FolderCard, archived: Boolean) {
        viewModelScope.launch {
            try {
                repo.setFolderArchived(card.id, archived)
                reload()
                _notices.emit(if (archived) "已封存「${card.name}」" else "已取消封存「${card.name}」")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _notices.emit(if (archived) "封存失敗，請再試一次" else "取消封存失敗，請再試一次")
            }
        }
    }

    /**
     * 名稱規則（同層不重名、上限 50 字）由 repo 判斷並丟 `IllegalArgumentException`。
     * 這裡把訊息**留在對話框裡**，不跳 snackbar —— 使用者正在改的就是那個欄位。
     */
    private fun withNameRules(block: suspend () -> Unit) {
        launchGuarded {
            try {
                block()
                _state.value = FoldersStore.closeEditor(_state.value)
                reload()
            } catch (e: IllegalArgumentException) {
                _state.value = FoldersStore.editorFailed(_state.value, e.message ?: "名稱不能用")
            }
        }
    }

    /** 理由同 `HomeViewModel.launchGuarded`：接住例外、`CancellationException` 要重丟。 */
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
