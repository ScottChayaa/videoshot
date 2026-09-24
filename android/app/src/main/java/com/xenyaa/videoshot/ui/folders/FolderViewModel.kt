package com.xenyaa.videoshot.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.folders.sort
import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FolderState(
    val node: FolderNode? = null,
    val children: List<FolderCard> = emptyList(),
    /** 本層已載入的圖。從這一頁開 Lightbox 時，左右滑動的範圍就是它 */
    val items: List<ShotRow> = emptyList(),
    val total: Int = 0,
    val cursor: FolderCursor? = null,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val editor: FolderEditor? = null,
    val deleting: FolderCard? = null,
)

/** 一個資料夾頁的資料接線。`folderId` 是建構參數 —— 換資料夾＝換一個 VM（`viewModel(key = ...)`）。 */
class FolderViewModel(
    private val repo: LibraryRepo,
    private val folderId: Long,
    private val pageSize: Int = 50,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderState())
    val state: StateFlow<FolderState> = _state.asStateFlow()

    /**
     * `reload()` 或 `loadMore()` 目前在跑的那次讀取。**兩者都要先取消它再開始自己的**——
     * 理由同 `HomeViewModel.loadJob`：Lightbox 捲到底時本來就會發 `loadMore()`，
     * 跟刪除／編輯觸發的 `reload()` 可能撞在一起；不取消的話，晚回來的那個會把先完成的
     * 那個剛設好的狀態蓋掉（見階段 8 全盤覆查已知 #12）。
     */
    private var loadJob: Job? = null

    init { reload() }

    /**
     * 一次載完節點、子資料夾、總張數與第一頁。**故意寫成同一個 [launchGuarded] 裡循序等待**，
     * 不是各自 launch 一個協程 —— 兩個協程各自寫 `_state` 的話，其中一個失敗、另一個成功時，
     * 後完成的那個會把先前設好的 `error` 蓋回 null，使用者看不到任何錯誤訊息也不知道要重試。
     */
    fun reload() {
        loadJob?.cancel()
        _state.value = _state.value.copy(
            items = emptyList(), cursor = null, endReached = false, loading = true, error = null,
        )
        loadJob = launchGuarded {
            val node = repo.folderNode(folderId)
            val children = loadChildren()
            val total = repo.folderShotCount(folderId)
            val page = repo.folderShots(folderId, null, pageSize)
            _state.value = _state.value.copy(
                node = node,
                children = children,
                total = total,
                items = page.items,
                cursor = page.next,
                endReached = page.next == null,
                loading = false,
                error = null,
            )
        }
    }

    /**
     * 續載下一頁。**要擋 `loading` 重入**（審查 Important 1）——
     * 前一次還在跑時再被呼叫（例如捲動觸發跟手動重試同時發生），沒有這個檢查的話兩個協程
     * 會用同一個 `cursor` 各打一次 `repo.folderShots`，各自把結果 `items + page.items`，
     * 同一頁被接兩次、清單裡出現重複縮圖。同層作法見 `HomeStore.canLoadMore`。
     */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.endReached) return
        loadJob?.cancel()
        _state.value = current.copy(loading = true)
        loadJob = launchGuarded {
            val page = repo.folderShots(folderId, current.cursor, pageSize)
            _state.value = _state.value.copy(
                items = _state.value.items + page.items,
                cursor = page.next,
                endReached = page.next == null,
                loading = false,
                error = null,
            )
        }
    }

    /**
     * Lightbox 在這個資料夾裡刪掉一張圖：**外科手術式地拔掉那一列**，不能整頁 `reload()`
     * ——資料夾超過一頁、使用者已經往下捲過時，`reload()` 會把 `items` 清空重撈第一頁，
     * 分頁跳回開頭（見階段 8 全盤覆查 N3／已知 #12）。`cursor`／`endReached` 保持原樣。
     * 那張圖可能同時也在某個子資料夾裡，`children` 的張數與預覽拼貼要另外重查。
     */
    fun onShotDeleted(id: Long) {
        val current = _state.value
        if (current.items.none { it.id == id }) return
        _state.value = current.copy(
            items = current.items.filterNot { it.id == id },
            total = (current.total - 1).coerceAtLeast(0),
        )
        launchGuarded { _state.value = _state.value.copy(children = loadChildren()) }
    }

    /** 子資料夾列固定名稱升冪（`FolderSort` 的 KDoc：清單頁與資料夾頁共用同一組排序）。 */
    private suspend fun loadChildren(): List<FolderCard> = FolderSort.NAME_ASC.sort(repo.folderCards(folderId))

    /** 新增子資料夾。`FoldersStore` 的那幾個轉換是給清單頁的 `FoldersState` 用的，型別不同，不要硬轉 */
    fun startCreateChild() { _state.value = _state.value.copy(editor = FolderEditor(target = null, name = "")) }

    /** 改的是**這一頁自己**（頂列的〔改名〕）。 */
    fun startRename() {
        val node = _state.value.node ?: return
        _state.value = _state.value.copy(editor = FolderEditor(node.id, node.name))
    }

    /**
     * 改的是**上半列出的某個子資料夾**，不是這一頁自己 —— 裁決 2：子卡片的〔改名〕要帶入
     * 子資料夾自己的名字與 id，接錯的話會把子資料夾改成跟父資料夾同名，是資料損壞級的錯。
     */
    fun startRenameChild(card: FolderCard) {
        _state.value = _state.value.copy(editor = FolderEditor(card.id, card.name))
    }

    fun editName(name: String) { _state.value = _state.value.copy(editor = _state.value.editor?.copy(name = name, error = null)) }
    fun dismissEditor() { _state.value = _state.value.copy(editor = null) }

    fun confirmEditor() {
        val editor = _state.value.editor ?: return
        val name = editor.name.trim()
        launchGuarded {
            try {
                if (editor.target == null) repo.createFolder(folderId, name) else repo.renameFolder(editor.target, name)
                _state.value = _state.value.copy(editor = null)
                reload()
            } catch (e: IllegalArgumentException) {
                _state.value = _state.value.copy(editor = editor.copy(error = e.message ?: "名稱不能用"))
            }
        }
    }

    /** 要刪的是這個資料夾自己。確認框只用到名稱，所以把 node 包成一張卡片重用同一個對話框 */
    fun askDeleteSelf() {
        val node = _state.value.node ?: return
        _state.value = _state.value.copy(deleting = FolderCard(node.id, node.name, 0, 0, emptyList()))
    }

    /**
     * 要刪的是**上半列出的某個子資料夾**，不是這一頁自己 —— 裁決 2：接錯的話會把整個父資料夾
     * （連同使用者正在看的這一頁）刪掉，是資料損壞級的錯。
     */
    fun askDeleteChild(card: FolderCard) {
        _state.value = _state.value.copy(deleting = card)
    }

    fun dismissDelete() { _state.value = _state.value.copy(deleting = null) }

    /**
     * 真的刪。依 `state.deleting.id` 是不是這一頁自己決定之後怎麼辦：
     * - 刪的是自己 → 呼叫 [onDeleted]，由呼叫端把這一頁從導覽堆疊移除
     * - 刪的是子資料夾 → 留在原地，[reload] 讓上半的清單反映出來
     */
    fun confirmDelete(onDeleted: () -> Unit) {
        val target = _state.value.deleting ?: return
        launchGuarded {
            repo.deleteFolder(target.id)
            _state.value = _state.value.copy(deleting = null)
            if (target.id == folderId) onDeleted() else reload()
        }
    }

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
