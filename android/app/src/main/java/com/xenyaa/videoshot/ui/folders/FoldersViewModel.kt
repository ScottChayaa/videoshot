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
import kotlinx.coroutines.flow.update
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

    /** 封存／取消封存的結果與多本操作失敗的提示，`AppRoot` 收來顯示 snackbar（同一個 VM 服務兩個畫面）。 */
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
            _state.update { FoldersStore.loaded(it, cards) }
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
    fun dismissDelete() { _state.update { FoldersStore.closeDelete(it) } }

    // ---- 長按多選 ----

    fun toggleSelect(id: Long) { _state.update { FoldersStore.toggleSelect(it, id) } }
    fun clearSelection() { _state.update { FoldersStore.clearSelection(it) } }

    /** 底部【重新命名】：只有剛好選一本時有作用——開那本的改名框，並離開多選。 */
    fun startRenameSelected() {
        _state.update { state ->
            val card = FoldersStore.selectedCards(state).singleOrNull() ?: return@update state
            FoldersStore.clearSelection(FoldersStore.startRename(state, card))
        }
    }

    /** 底部【刪除】：先開確認框（一本沿用單本的，多本另一個），確認後才 [deleteSelected]。 */
    fun askDeleteSelected() { _state.update { FoldersStore.askDeleteSelected(it) } }

    fun create(name: String) = withNameRules { repo.createFolder(null, name) }

    fun rename(id: Long, name: String) = withNameRules { repo.renameFolder(id, name) }

    /**
     * 刪掉確認框裡的那幾本（逐本 `repo.deleteFolder`）。跟原本的單本刪除一樣不發成功提示；
     * 中途失敗時已刪的就是刪了，發失敗提示。無論成敗都關掉確認框、離開多選、重查。
     */
    fun deleteSelected() {
        val targets = FoldersStore.deleteTargets(_state.value).ifEmpty { FoldersStore.selectedCards(_state.value) }
        _state.update { FoldersStore.clearSelection(FoldersStore.closeDelete(it)) }
        forEachThenReload(targets, failure = "刪除失敗，請再試一次", success = { null }) { repo.deleteFolder(it.id) }
    }

    fun archiveSelected() = setSelectedArchived(archived = true)

    fun unarchiveSelected() = setSelectedArchived(archived = false)

    /**
     * 寫完重查：卡片會從目前的畫面消失、出現在另一個畫面（兩邊都讀同一份 `cards`）。
     * 失敗只發提示，不寫進 `error`——那是「清單讀不到」的錯誤列，這裡清單本身沒問題。
     */
    private fun setSelectedArchived(archived: Boolean) {
        val targets = FoldersStore.selectedCards(_state.value)
        if (targets.isEmpty()) return
        _state.update { FoldersStore.clearSelection(it) }
        val verb = if (archived) "已封存" else "已取消封存"
        forEachThenReload(
            targets,
            failure = if (archived) "封存失敗，請再試一次" else "取消封存失敗，請再試一次",
            success = { if (it.size == 1) "$verb「${it.single().name}」" else "$verb ${it.size} 本相簿" },
        ) { repo.setFolderArchived(it.id, archived) }
    }

    /**
     * 逐本做 [action]；中途失敗就停，已完成的保留，發 [failure]。成功時發 [success] 回傳的提示（null＝不發）。
     * 無論成敗都重查——就算失敗，前面幾本也可能已經改了。
     */
    private fun forEachThenReload(
        targets: List<FolderCard>,
        failure: String,
        success: (List<FolderCard>) -> String?,
        action: suspend (FolderCard) -> Unit,
    ) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val notice = try {
                targets.forEach { action(it) }
                success(targets)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failure
            }
            reload()
            notice?.let { _notices.emit(it) }
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
