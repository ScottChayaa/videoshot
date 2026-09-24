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
    val deleting: FolderCard? = null,
)

/** 清單頁的純狀態轉換。沒有 suspend、沒有 Android 相依。 */
object FoldersStore {

    /** 篩選 ＋ 排序都在這裡，畫面只負責畫。 */
    fun visible(state: FoldersState): List<FolderCard> {
        val q = state.query.trim()
        val filtered = if (q.isEmpty()) state.cards else state.cards.filter { it.name.contains(q, ignoreCase = true) }
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
        state.cards.isEmpty() -> FoldersEmpty.NO_FOLDERS
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

    fun askDelete(state: FoldersState, card: FolderCard): FoldersState = state.copy(deleting = card)

    fun closeDelete(state: FoldersState): FoldersState = state.copy(deleting = null)

    /** 收起搜尋列就把條件一起清掉 —— 收起來卻還在篩選是看不出來的狀態。 */
    fun setSearching(state: FoldersState, searching: Boolean): FoldersState =
        if (searching) state.copy(searching = true) else state.copy(searching = false, query = "")
}
