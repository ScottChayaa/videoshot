package com.xenyaa.videoshot.ui.account

import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.TagUsage

/** 標籤編輯抽屜的草稿。`id` 是正在編輯的那個標籤，儲存時拿它跟既有標籤名比對決定要不要合併。 */
data class TagEditor(val id: Long, val name: String, val kind: TagKind, val aliasesRaw: String)

data class AccountState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: AccountStats = AccountStats(0, 0, 0),
    val filterStrength: FilterStrength = FilterStrength.MEDIUM,
    val aiRangeBeforeSec: Int = 10,
    val aiRangeAfterSec: Int = 20,
    val geminiKeySet: Boolean = false,
    val storageUsageBytes: Long = 0L,
    val tags: List<TagUsage> = emptyList(),
    val editor: TagEditor? = null,
    /** 儲存時發現會撞名——這裡放「撞到的那個名字」，畫面用它顯示確認文案。 */
    val pendingMerge: String? = null,
    val deleting: TagUsage? = null,
)

/** 標籤管理頁的純狀態轉換。沒有 suspend、沒有 Android 相依（比照 `FoldersStore`）。 */
object AccountStore {

    fun openEditor(state: AccountState, tag: TagUsage): AccountState =
        state.copy(editor = TagEditor(tag.id, tag.name, TagKind.byId(tag.kind), tag.aliases.joinToString(", ")))

    fun closeEditor(state: AccountState): AccountState = state.copy(editor = null, pendingMerge = null)

    fun editName(state: AccountState, name: String): AccountState =
        state.copy(editor = state.editor?.copy(name = name))

    fun editKind(state: AccountState, kind: TagKind): AccountState =
        state.copy(editor = state.editor?.copy(kind = kind))

    fun editAliases(state: AccountState, raw: String): AccountState =
        state.copy(editor = state.editor?.copy(aliasesRaw = raw))

    /** 儲存前的撞名檢查：找到一個名稱相同、id 不同的既有標籤，就是會合併。 */
    fun collidingTag(state: AccountState): TagUsage? {
        val editor = state.editor ?: return null
        val trimmed = editor.name.trim()
        return state.tags.firstOrNull { it.name == trimmed && it.id != editor.id }
    }
}
