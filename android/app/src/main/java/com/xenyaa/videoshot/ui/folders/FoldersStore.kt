package com.xenyaa.videoshot.ui.folders

import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.folders.sort
import com.xenyaa.videoshot.data.repo.model.FolderCard

/**
 * 新增／改名共用的對話框狀態。
 *
 * @param target null＝新增；有值＝改這個 id 的名字
 * @param error repo 擋下來的理由（同層重名、超過 50 字）。**留在對話框裡**而不是跳 snackbar ——
 *        使用者正要改的就是這個欄位，訊息要出現在欄位旁邊
 */
data class FolderEditor(val target: Long?, val name: String, val error: String? = null)

/** 空狀態的兩種成因。畫面要給不同的出口：一個是【新增資料夾】，一個是【清除篩選】。 */
enum class FoldersEmpty { NO_FOLDERS, NO_MATCH }

data class FoldersState(
    val cards: List<FolderCard> = emptyList(),
    val sort: FolderSort = FolderSort.NAME_ASC,
    val query: String = "",
    val searching: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val editor: FolderEditor? = null,
    /** 只選一本時按【刪除】：沿用單本的確認框（`DeleteFolderDialog`）。 */
    val deleting: FolderCard? = null,
    /** 選兩本以上按【刪除】：多本的確認框（「刪除 N 本相簿？」）。 */
    val deletingMany: List<FolderCard>? = null,
    /**
     * 長按多選中已選的相簿 id；空＝不在多選。相簿畫面與封存相簿畫面共用（同一個 VM），
     * 兩邊的卡片集合互斥，所以進出封存相簿畫面時要先清掉（`AppRoot` 負責）。
     */
    val selection: Set<Long> = emptySet(),
)

/** 清單頁的純狀態轉換。沒有 suspend、沒有 Android 相依。 */
object FoldersStore {

    /** 相簿畫面看得到的（沒封存的）。不排序、不篩選——「N 個相簿」用它的張數。 */
    fun active(state: FoldersState): List<FolderCard> = state.cards.filter { it.archivedAt == null }

    /** 封存相簿畫面的清單：封存時間新到舊（規格第六節），同時間時 id 大的在前，順序才穩定。 */
    fun archived(state: FoldersState): List<FolderCard> =
        state.cards.filter { it.archivedAt != null }
            .sortedWith(compareByDescending<FolderCard> { it.archivedAt }.thenByDescending { it.id })

    /** 封存相簿畫面的空狀態。理由同 [emptyKind]：讀取中與讀取失敗都不算「真的沒有」。 */
    fun archivedEmpty(state: FoldersState): Boolean =
        !state.loading && state.error == null && archived(state).isEmpty()

    /** 篩選 ＋ 排序都在這裡（只看沒封存的），畫面只負責畫。 */
    fun visible(state: FoldersState): List<FolderCard> {
        val q = state.query.trim()
        val base = active(state)
        val filtered = if (q.isEmpty()) base else base.filter { it.name.contains(q, ignoreCase = true) }
        return state.sort.sort(filtered)
    }

    /**
     * null＝有東西可以畫。讀取中一律不算空狀態，否則每次進頁面都會閃一下空畫面。
     * 讀取失敗也不算——`cards` 這時候一定是空的，但那是「讀不到」不是「真的沒有」，
     * 不接住的話會被誤判成 `NO_FOLDERS`，對使用者主動說錯話（N2 的回歸測試）。
     */
    fun emptyKind(state: FoldersState): FoldersEmpty? = when {
        state.loading -> null
        state.error != null -> null
        active(state).isEmpty() -> FoldersEmpty.NO_FOLDERS
        visible(state).isEmpty() -> FoldersEmpty.NO_MATCH
        else -> null
    }

    fun startCreate(state: FoldersState): FoldersState = state.copy(editor = FolderEditor(null, ""))

    fun startRename(state: FoldersState, card: FolderCard): FoldersState =
        state.copy(editor = FolderEditor(card.id, card.name))

    fun editName(state: FoldersState, name: String): FoldersState =
        state.copy(editor = state.editor?.copy(name = name, error = null))

    fun editorFailed(state: FoldersState, message: String): FoldersState =
        state.copy(editor = state.editor?.copy(error = message))

    fun closeEditor(state: FoldersState): FoldersState = state.copy(editor = null)

    /** 只選一本走單本的確認框，多本走多本的確認框；沒選就不動。 */
    fun askDeleteSelected(state: FoldersState): FoldersState {
        val cards = selectedCards(state)
        return when (cards.size) {
            0 -> state
            1 -> state.copy(deleting = cards.single(), deletingMany = null)
            else -> state.copy(deleting = null, deletingMany = cards)
        }
    }

    /** 確認框裡要刪的那幾本（以確認框打開時的清單為準）。 */
    fun deleteTargets(state: FoldersState): List<FolderCard> =
        state.deletingMany ?: listOfNotNull(state.deleting)

    fun closeDelete(state: FoldersState): FoldersState = state.copy(deleting = null, deletingMany = null)

    fun toggleSelect(state: FoldersState, id: Long): FoldersState =
        state.copy(selection = if (id in state.selection) state.selection - id else state.selection + id)

    fun clearSelection(state: FoldersState): FoldersState = state.copy(selection = emptySet())

    /**
     * 已選的卡片，依畫面上的順序：相簿畫面照 [visible]（目前排序），封存相簿畫面照 [archived]
     * （兩邊集合互斥，接起來就是各自畫面的順序）。萬一有選到但不在任何一邊的（理論上不會），接在最後。
     */
    fun selectedCards(state: FoldersState): List<FolderCard> {
        if (state.selection.isEmpty()) return emptyList()
        val ordered = visible(state) + archived(state)
        val orderedIds = ordered.mapTo(HashSet()) { it.id }
        return (ordered + state.cards.filter { it.id !in orderedIds }).filter { it.id in state.selection }
    }

    /** 讀取完成：換上新的卡片，已經不在的相簿從選取拿掉（不然「已選 N 本」會算錯）。 */
    fun loaded(state: FoldersState, cards: List<FolderCard>): FoldersState {
        val ids = cards.mapTo(HashSet()) { it.id }
        return state.copy(
            cards = cards,
            loading = false,
            error = null,
            selection = state.selection.filterTo(LinkedHashSet()) { it in ids },
        )
    }

    /** 收起搜尋列就把條件一起清掉 —— 收起來卻還在篩選是看不出來的狀態。 */
    fun setSearching(state: FoldersState, searching: Boolean): FoldersState =
        if (searching) state.copy(searching = true) else state.copy(searching = false, query = "")
}
