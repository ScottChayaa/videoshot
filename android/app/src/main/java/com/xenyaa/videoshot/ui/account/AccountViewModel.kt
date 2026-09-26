package com.xenyaa.videoshot.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.home.monthOf
import com.xenyaa.videoshot.core.tags.parseAliases
import com.xenyaa.videoshot.data.repo.model.TagUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 整個帳號分頁共用一份（比照 `FoldersViewModel`）——六個子畫面看到的是同一份 `stats`／
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

    init {
        viewModelScope.launch { deps.filterStrength.collect { v -> _state.value = _state.value.copy(filterStrength = v) } }
        viewModelScope.launch { deps.aiRangeBeforeSec.collect { v -> _state.value = _state.value.copy(aiRangeBeforeSec = v) } }
        viewModelScope.launch { deps.aiRangeAfterSec.collect { v -> _state.value = _state.value.copy(aiRangeAfterSec = v) } }
        viewModelScope.launch { deps.geminiKeySet.collect { v -> _state.value = _state.value.copy(geminiKeySet = v) } }
        reload()
    }

    /** 開帳號頁、標籤管理存檔／刪除之後都要叫——統計與標籤清單可能都變了。 */
    fun reload() {
        _state.value = _state.value.copy(loading = true)
        launchGuarded {
            val stats = deps.stats(monthOf(today()))
            val tags = deps.tags()
            val usage = deps.storageUsageBytes()
            _state.value = _state.value.copy(stats = stats, tags = tags, storageUsageBytes = usage, loading = false, error = null)
        }
    }

    fun setFilterStrength(value: FilterStrength) = launchGuarded { deps.setFilterStrength(value) }

    fun setAiRange(beforeSec: Int, afterSec: Int) =
        launchGuarded { deps.setAiRange(beforeSec.coerceAtLeast(0), afterSec.coerceAtLeast(0)) }

    /** 空白輸入不送出——沒有「清空金鑰」這個操作，清除要按明確的【清除】按鈕（[clearGeminiKey]）。 */
    fun saveGeminiKey(plain: String) {
        val trimmed = plain.trim()
        if (trimmed.isEmpty()) return
        launchGuarded { deps.setGeminiKey(trimmed) }
    }

    fun clearGeminiKey() = launchGuarded { deps.clearGeminiKey() }

    fun openTagEditor(tag: TagUsage) { _state.value = AccountStore.openEditor(_state.value, tag) }
    fun dismissTagEditor() { _state.value = AccountStore.closeEditor(_state.value) }
    fun editTagName(name: String) { _state.value = AccountStore.editName(_state.value, name) }
    fun editTagKind(kind: com.xenyaa.videoshot.core.tags.TagKind) { _state.value = AccountStore.editKind(_state.value, kind) }
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
    }

    fun askDeleteTag(tag: TagUsage) { _state.value = _state.value.copy(deleting = tag) }
    fun dismissDeleteTag() { _state.value = _state.value.copy(deleting = null) }

    fun confirmDeleteTag() {
        val tag = _state.value.deleting ?: return
        launchGuarded {
            deps.deleteTag(tag.id)
            _state.value = _state.value.copy(deleting = null)
            reload()
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
