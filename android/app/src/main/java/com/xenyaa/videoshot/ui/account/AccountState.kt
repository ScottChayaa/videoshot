package com.xenyaa.videoshot.ui.account

import com.xenyaa.videoshot.backfill.BackfillProgress
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.home.DEFAULT_THUMB_COLUMNS
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.PlaceUsage
import com.xenyaa.videoshot.data.repo.model.TagUsage

/** 標籤編輯抽屜的草稿。`id` 是正在編輯的那個標籤，儲存時拿它跟既有標籤名比對決定要不要合併。 */
data class TagEditor(val id: Long, val name: String, val kind: TagKind, val aliasesRaw: String)

/** 地點編輯抽屜的草稿。`id` 是正在編輯的地點，儲存時拿名稱跟既有地點比對決定要不要合併。 */
data class PlaceEditor(val id: Long, val name: String, val aliasesRaw: String)

enum class MergeKind { PLACE, TAG }

/** 合併：挑目標中（16C）。 */
data class MergeRequest(val kind: MergeKind, val fromId: Long, val fromName: String, val fromCount: Int)

/** 合併：確認中（16C）。 */
data class MergeConfirm(val request: MergeRequest, val toId: Long, val toName: String)

data class AccountState(
    val loading: Boolean = true,
    val error: String? = null,
    val stats: AccountStats = AccountStats(0, 0, 0),
    val filterStrength: FilterStrength = FilterStrength.MEDIUM,
    val aiRangeBeforeSec: Int = 10,
    val aiRangeAfterSec: Int = 20,
    val geminiKeySet: Boolean = false,
    val storageUsageBytes: Long = 0L,
    /** 縮圖牆手機寬度每列張數；首頁、查詢結果、資料夾內容都讀這一格（`AppRoot`）。 */
    val thumbColumns: Int = DEFAULT_THUMB_COLUMNS,
    val backfillProgress: BackfillProgress = BackfillProgress(0, 0, 0),
    val backfillActionError: String? = null,
    val tags: List<TagUsage> = emptyList(),
    val editor: TagEditor? = null,
    /** 儲存時發現會撞名——這裡放「撞到的那個名字」，畫面用它顯示確認文案。 */
    val pendingMerge: String? = null,
    val deleting: TagUsage? = null,
    val places: List<PlaceUsage> = emptyList(),
    val placeEditor: PlaceEditor? = null,
    /** 地點改名會撞名時，放「撞到的那個名字」。 */
    val placePendingMerge: String? = null,
    val placeDeleting: PlaceUsage? = null,
    val mergePicking: MergeRequest? = null,
    val mergeConfirm: MergeConfirm? = null,
    /** 合併進行中（圖很多時要幾秒），畫面顯示不可關閉的「合併中…」。 */
    val merging: Boolean = false,
    val linkedAccount: LinkedGoogleAccount? = null,
    val lastBackupAtEpochSec: Long = 0L,
    val backingUp: Boolean = false,
    val backupError: String? = null,
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

    fun openPlaceEditor(state: AccountState, place: PlaceUsage): AccountState =
        state.copy(placeEditor = PlaceEditor(place.id, place.name, place.aliases.joinToString(", ")))

    fun closePlaceEditor(state: AccountState): AccountState =
        state.copy(placeEditor = null, placePendingMerge = null)

    fun editPlaceName(state: AccountState, name: String): AccountState =
        state.copy(placeEditor = state.placeEditor?.copy(name = name))

    fun editPlaceAliases(state: AccountState, raw: String): AccountState =
        state.copy(placeEditor = state.placeEditor?.copy(aliasesRaw = raw))

    /** 儲存前的撞名檢查：名稱相同、id 不同的既有地點。 */
    fun collidingPlace(state: AccountState): PlaceUsage? {
        val editor = state.placeEditor ?: return null
        val trimmed = editor.name.trim()
        return state.places.firstOrNull { it.name == trimmed && it.id != editor.id }
    }
}
