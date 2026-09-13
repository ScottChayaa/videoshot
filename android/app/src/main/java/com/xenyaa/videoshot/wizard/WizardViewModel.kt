package com.xenyaa.videoshot.wizard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 精靈的流程狀態。放在 ViewModel 是為了**轉螢幕不丟東西** ——
 * 已經抓好的 watch page 與已勾選的格子重來一次代價太大。
 */
class WizardViewModel(
    private val data: WizardData,
) : ViewModel() {

    private val _step = MutableStateFlow(WizardStep.URL)
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    /** 走過的最遠步驟；進度列只讓使用者點回「已完成」的步驟。 */
    private val _furthest = MutableStateFlow(WizardStep.URL)
    val furthest: StateFlow<WizardStep> = _furthest.asStateFlow()

    private val _hasDraft = MutableStateFlow(false)
    val hasDraft: StateFlow<Boolean> = _hasDraft.asStateFlow()

    fun goTo(target: WizardStep) {
        _step.value = target
        if (target.order > _furthest.value.order) _furthest.value = target
        // 一離開第一步就有草稿可留 —— 使用者已經投入了選擇
        if (target != WizardStep.URL) _hasDraft.value = true
    }

    /** 點進度列上的某一段。只有已完成的步驟能點回去。 */
    fun jumpTo(target: WizardStep) {
        if (target.order <= _furthest.value.order) _step.value = target
    }

    /**
     * 處理 Android 返回鍵。
     * @return true 代表精靈自己消化掉了；false 代表在第一步，交給系統（離開精靈）
     */
    fun back(): Boolean {
        val current = _step.value
        if (current == WizardStep.URL) return false
        _step.value = WizardStep.entries.first { it.order == current.order - 1 }
        return true
    }

    fun keepDraft() { _hasDraft.value = true }

    fun discardDraft() { _hasDraft.value = false }
}
