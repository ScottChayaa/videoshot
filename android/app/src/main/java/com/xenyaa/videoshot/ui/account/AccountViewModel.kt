package com.xenyaa.videoshot.ui.account

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.core.tags.parseAliases
import com.xenyaa.videoshot.data.repo.model.PlaceUsage
import com.xenyaa.videoshot.data.repo.model.TagUsage
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
import java.time.LocalDate

/**
 * 整個帳號分頁共用一份（比照 `FoldersViewModel`）——七個子畫面看到的是同一份 `stats`／
 * `tags`／設定值，不必各自重查。
 *
 * @param today 注入而不是直接呼叫 `LocalDate.now()`——理由同 `WizardViewModel.today`：
 *        測試要能確定地驗「本月新增」用的是哪個月
 */
class AccountViewModel(
    private val deps: AccountDeps,
    private val today: () -> String = { LocalDate.now().toString() },
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /**
     * 標籤或地點改名、合併、刪除真的送出之後發一次——查詢頁候選與首頁月份標籤、圖上的地點名稱
     * 都要重讀（`AppRoot` 收到時重查查詢頁候選並重新載入首頁）。用 `extraBufferCapacity = 1`：
     * `emit` 不必等收集端排到才返回，理由同 `WizardViewModel.finished` 的 KDoc。
     */
    private val _labelsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val labelsChanged: SharedFlow<Unit> = _labelsChanged.asSharedFlow()

    init {
        viewModelScope.launch { deps.filterStrength.collect { v -> _state.value = _state.value.copy(filterStrength = v) } }
        viewModelScope.launch { deps.aiRangeBeforeSec.collect { v -> _state.value = _state.value.copy(aiRangeBeforeSec = v) } }
        viewModelScope.launch { deps.aiRangeAfterSec.collect { v -> _state.value = _state.value.copy(aiRangeAfterSec = v) } }
        viewModelScope.launch { deps.thumbColumns.collect { v -> _state.update { it.copy(thumbColumns = v) } } }
        viewModelScope.launch { deps.geminiKeySet.collect { v -> _state.value = _state.value.copy(geminiKeySet = v) } }
        viewModelScope.launch { deps.linkedAccount.collect { v -> _state.value = _state.value.copy(linkedAccount = v) } }
        viewModelScope.launch { deps.lastBackupAtEpochSec.collect { v -> _state.value = _state.value.copy(lastBackupAtEpochSec = v) } }
        reload()
    }

    /** 開帳號頁、標籤管理存檔／刪除之後都要叫——統計與標籤清單可能都變了。 */
    fun reload() {
        _state.value = _state.value.copy(loading = true)
        launchGuarded {
            val stats = deps.stats(monthOf(today()))
            val tags = deps.tags()
            val places = deps.places()
            val usage = deps.storageUsageBytes()
            val backfill = deps.backfillProgress()
            _state.value = _state.value.copy(
                stats = stats, tags = tags, places = places, storageUsageBytes = usage,
                backfillProgress = backfill, loading = false, error = null,
            )
        }
    }

    fun setFilterStrength(value: FilterStrength) = launchGuarded { deps.setFilterStrength(value) }

    fun setThumbColumns(value: Int) = launchGuarded { deps.setThumbColumns(value) }

    fun setAiRange(beforeSec: Int, afterSec: Int) =
        launchGuarded { deps.setAiRange(beforeSec.coerceAtLeast(0), afterSec.coerceAtLeast(0)) }

    /** 空白輸入不送出——沒有「清空金鑰」這個操作，清除要按明確的【清除】按鈕（[clearGeminiKey]）。 */
    fun saveGeminiKey(plain: String) {
        val trimmed = plain.trim()
        if (trimmed.isEmpty()) return
        launchGuarded { deps.setGeminiKey(trimmed) }
    }

    fun clearGeminiKey() = launchGuarded { deps.clearGeminiKey() }

    /**
     * @param onNeedsConsent 需要使用者到系統畫面同意時呼叫——`AppRoot` 接住這個 callback，
     *        用 `rememberLauncherForActivityResult` 跳出畫面，回來後呼叫 [finishLink]。
     *        連結**成功**（不需要額外同意）時不會呼叫這個 callback，`linkedAccount` flow 自己會更新畫面。
     */
    fun beginLink(activity: Activity, onNeedsConsent: (IntentSender) -> Unit) =
        launchBackupGuarded("連結 Google 帳號失敗，請確認網路後再試一次") {
            when (val outcome = deps.beginLink(activity)) {
                is LinkOutcome.Linked -> Unit
                is LinkOutcome.NeedsConsent -> onNeedsConsent(outcome.intentSender)
            }
        }

    fun finishLink(data: Intent) =
        launchBackupGuarded("連結 Google 帳號失敗，請確認網路後再試一次") { deps.finishLink(data) }

    fun unlink() = launchBackupGuarded("中斷連結失敗，請確認網路後再試一次") { deps.unlink() }

    fun backupNow() {
        _state.value = _state.value.copy(backingUp = true, backupError = null)
        viewModelScope.launch {
            try {
                deps.backupNow()
                _state.value = _state.value.copy(backingUp = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = _state.value.copy(backingUp = false, backupError = "備份失敗，請確認網路後再試一次")
            }
        }
    }

    /** 「縮圖」子畫面開著時輕量輪詢用——不影響 `loading`／`error`,只更新這一塊。 */
    fun refreshBackfillProgress() {
        viewModelScope.launch {
            try {
                // 先等結果再以當下狀態更新——理由見 DetailViewModel.loadPlayerInfo
                val progress = deps.backfillProgress()
                _state.update { it.copy(backfillProgress = progress) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 輪詢失敗不用打擾使用者——下一輪再試就好
            }
        }
    }

    fun retryLostThumbs() = launchBackfillActionGuarded {
        deps.retryLostThumbs()
        refreshBackfillProgress()
    }

    fun deleteLostThumbs() = launchBackfillActionGuarded {
        deps.deleteLostThumbs()
        refreshBackfillProgress()
    }

    fun continueBackfillOnMobileData() = launchBackfillActionGuarded {
        deps.continueBackfillOnMobileData()
    }

    private fun launchBackfillActionGuarded(block: suspend () -> Unit): Job {
        _state.value = _state.value.copy(backfillActionError = null)
        return viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = _state.value.copy(backfillActionError = "操作失敗，請再試一次")
            }
        }
    }

    fun openTagEditor(tag: TagUsage) { _state.value = AccountStore.openEditor(_state.value, tag) }
    fun dismissTagEditor() { _state.value = AccountStore.closeEditor(_state.value) }
    fun editTagName(name: String) { _state.value = AccountStore.editName(_state.value, name) }
    fun editTagKind(kind: TagKind) { _state.value = AccountStore.editKind(_state.value, kind) }
    fun editTagAliases(raw: String) { _state.value = AccountStore.editAliases(_state.value, raw) }

    /**
     * 儲存前先查會不會撞名。撞到就停在確認提示（[AccountState.pendingMerge]），
     * 不撞就直接送出——這是規格第九節「改名成既有名稱＝合併」需要使用者確認的唯一入口。
     */
    fun requestSaveTag() {
        val editor = _state.value.editor ?: return
        val collision = AccountStore.collidingTag(_state.value)
        if (collision != null) {
            _state.value = _state.value.copy(pendingMerge = collision.name)
        } else {
            performSaveTag(editor.id, editor.name.trim(), editor.kind.id, parseAliases(editor.aliasesRaw))
        }
    }

    fun confirmMerge() {
        val editor = _state.value.editor ?: return
        performSaveTag(editor.id, editor.name.trim(), editor.kind.id, parseAliases(editor.aliasesRaw))
    }

    fun dismissMergeConfirm() { _state.value = _state.value.copy(pendingMerge = null) }

    private fun performSaveTag(id: Long, name: String, kind: String, aliases: List<String>) = launchGuarded {
        deps.renameTag(id, name, kind, aliases)
        _state.value = _state.value.copy(editor = null, pendingMerge = null)
        reload()
        _labelsChanged.emit(Unit)
    }

    /**
     * 也清掉 [AccountState.editor]——這顆刪除鈕是從編輯抽屜（[TagEditor]）裡按的
     * （`TagManagementScreen` 的 `TagEditSheet.onDelete`），不清的話確認對話框會疊在
     * 還開著的編輯抽屜上面；確認之後抽屜還留在畫面上顯示這個已經被刪掉的標籤的舊草稿，
     * 這時候按【儲存】會對一個不存在的 id 呼叫 `renameTag`，SQL 一列都不會命中、
     * 靜默沒反應（不是當機，但畫面對不上）（最終審查 Important 4）。
     */
    fun askDeleteTag(tag: TagUsage) { _state.value = _state.value.copy(deleting = tag, editor = null) }
    fun dismissDeleteTag() { _state.value = _state.value.copy(deleting = null) }

    fun confirmDeleteTag() {
        val tag = _state.value.deleting ?: return
        launchGuarded {
            deps.deleteTag(tag.id)
            _state.value = _state.value.copy(deleting = null, editor = null)
            reload()
            _labelsChanged.emit(Unit)
        }
    }

    fun openPlaceEditor(place: PlaceUsage) { _state.value = AccountStore.openPlaceEditor(_state.value, place) }
    fun dismissPlaceEditor() { _state.value = AccountStore.closePlaceEditor(_state.value) }
    fun editPlaceName(name: String) { _state.value = AccountStore.editPlaceName(_state.value, name) }
    fun editPlaceAliases(raw: String) { _state.value = AccountStore.editPlaceAliases(_state.value, raw) }

    /** 儲存前先查撞名：撞到停在確認提示（[AccountState.placePendingMerge]），不撞就直接送出。 */
    fun requestSavePlace() {
        val collision = AccountStore.collidingPlace(_state.value)
        if (collision != null) {
            _state.value = _state.value.copy(placePendingMerge = collision.name)
        } else {
            performSavePlace()
        }
    }

    fun confirmPlaceMerge() = performSavePlace()

    fun dismissPlaceMerge() { _state.value = _state.value.copy(placePendingMerge = null) }

    private fun performSavePlace() {
        val editor = _state.value.placeEditor ?: return
        val name = editor.name.trim()
        // 別名裡跟本名相同的沒有意義（合併時舊名會被 repo 自己留成別名），先拿掉
        val aliases = parseAliases(editor.aliasesRaw).filter { it != name }
        launchGuarded {
            deps.renamePlace(editor.id, name, aliases)
            _state.value = _state.value.copy(placeEditor = null, placePendingMerge = null)
            reload()
            _labelsChanged.emit(Unit)
        }
    }

    /** 合併（16C）：從地點編輯抽屜開始，抽屜關掉、改開目標挑選。 */
    fun startPlaceMerge() {
        val editor = _state.value.placeEditor ?: return
        val count = _state.value.places.firstOrNull { it.id == editor.id }?.shotCount ?: 0
        _state.update {
            it.copy(placeEditor = null, mergePicking = MergeRequest(MergeKind.PLACE, editor.id, editor.name, count))
        }
    }

    /** 同 [startPlaceMerge]，來源是標籤編輯抽屜。 */
    fun startTagMerge() {
        val editor = _state.value.editor ?: return
        val count = _state.value.tags.firstOrNull { it.id == editor.id }?.shotCount ?: 0
        _state.update {
            it.copy(editor = null, pendingMerge = null, mergePicking = MergeRequest(MergeKind.TAG, editor.id, editor.name, count))
        }
    }

    fun pickMergeTarget(toId: Long) {
        val request = _state.value.mergePicking ?: return
        val toName = when (request.kind) {
            MergeKind.PLACE -> _state.value.places.firstOrNull { it.id == toId }?.name
            MergeKind.TAG -> _state.value.tags.firstOrNull { it.id == toId }?.name
        } ?: return
        _state.update { it.copy(mergePicking = null, mergeConfirm = MergeConfirm(request, toId, toName)) }
    }

    fun dismissMergePicker() { _state.update { it.copy(mergePicking = null) } }

    /** 取消合併確認框（標籤既有的 [dismissMergeConfirm] 是改名撞名的確認框，兩者不同）。 */
    fun cancelMergeTarget() { _state.update { it.copy(mergeConfirm = null) } }

    fun confirmMergeTarget() {
        val confirm = _state.value.mergeConfirm ?: return
        _state.update { it.copy(mergeConfirm = null, merging = true) }
        viewModelScope.launch {
            try {
                when (confirm.request.kind) {
                    MergeKind.PLACE -> deps.mergePlace(confirm.request.fromId, confirm.toId)
                    MergeKind.TAG -> deps.mergeTag(confirm.request.fromId, confirm.toId)
                }
                _state.update { it.copy(merging = false) }
                reload()
                _labelsChanged.emit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(merging = false, error = "合併失敗，請再試一次") }
            }
        }
    }

    /** 同 [askDeleteTag]：從編輯抽屜按刪除，抽屜要同時關掉。 */
    fun askDeletePlace(place: PlaceUsage) { _state.value = _state.value.copy(placeDeleting = place, placeEditor = null) }
    fun dismissDeletePlace() { _state.value = _state.value.copy(placeDeleting = null) }

    fun confirmDeletePlace() {
        val place = _state.value.placeDeleting ?: return
        launchGuarded {
            deps.deletePlace(place.id)
            _state.value = _state.value.copy(placeDeleting = null, placeEditor = null)
            reload()
            _labelsChanged.emit(Unit)
        }
    }

    /**
     * 連結／同意流程／中斷連結這三個動作的失敗要走 [AccountState.backupError]，不是
     * [launchGuarded] 的泛用 `error`——`error` 目前只有 `TagManagementScreen` 會畫出來，
     * 備份子畫面（`BackupScreen`）與帳號頁首畫面都不顯示它。走 `error` 的後果是：中斷連結
     * 真的失敗時（`GisGoogleAuth.unlink` 的 `revokeAccess` 遇到網路錯誤）使用者當下什麼
     * 都看不到、以為斷掉了；然後那句泛用的「操作失敗，請再試一次」會殘留到**毫不相干的**
     * 標籤管理畫面上，等到使用者哪天點進去才莫名看到一次（最終審查 Important 4）。
     *
     * 一開始就先清掉 [AccountState.backupError]（同 [backupNow] 的做法）——上一次失敗的
     * 訊息不能留在畫面上跟這一次的成功並存。
     */
    private fun launchBackupGuarded(message: String, block: suspend () -> Unit): Job {
        _state.value = _state.value.copy(backupError = null)
        return viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = _state.value.copy(backupError = message)
            }
        }
    }

    /** 理由同 `FoldersViewModel.launchGuarded`：接住例外、`CancellationException` 要重丟。 */
    private fun launchGuarded(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = _state.value.copy(loading = false, error = "操作失敗，請再試一次")
        }
    }
}
