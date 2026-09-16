package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.details.applyToCells
import com.xenyaa.videoshot.core.details.commonOf
import com.xenyaa.videoshot.core.details.normalizeTags
import com.xenyaa.videoshot.core.time.formatClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 第三步要填圖資的一張圖。
 *
 * `cell` **沿用第二步的格號空間**（storyboard 是 frameIndex，手動圖接在 `plan.frameCount` 之後往上編，
 * 見階段 4c）。沿用而不重新編號，是為了讓「第二步勾了哪些」與「第三步在填哪些」不需要任何轉換表。
 */
data class Step3Cell(val cell: Int, val atSec: Double, val manual: Boolean)

/**
 * 第三步的畫面狀態。
 *
 * @param details 每一格目前的圖資。**鍵一定涵蓋 [cells] 的每一格** —— 少一格就是入庫時少一筆 event_date
 * @param patch 抽屜裡「動過但還沒套用」的部分。空的代表主按鈕是【完成】
 */
data class Step3State(
    val cells: List<Step3Cell>,
    val details: Map<Int, ShotDetails>,
    val selected: Set<Int>,
    val patch: DetailsPatch = DetailsPatch(),
    val cropping: Boolean = true,
    val cropDone: Int = 0,
    val cropTotal: Int = 0,
) {
    val appliedCount: Int get() = details.count { (cell, d) -> d.applied && cells.any { it.cell == cell } }

    val unappliedCells: Set<Int>
        get() = cells.map { it.cell }.filterNot { details[it]?.applied == true }.toSet()

    /** 頂部的「18 張 · 8 已完成」（規格第五節第三步的線框）。 */
    val headerText: String get() = "${cells.size} 張 · $appliedCount 已完成"

    /**
     * 抽屜標題。只勾一張時**帶上時間** —— 那就是規格所說的「單張編輯」，
     * 使用者要看得出現在編的是哪一張（規格第五節的勾選數表）。
     */
    val drawerTitle: String
        get() {
            val one = selected.singleOrNull()
            val clock = one?.let { c -> cells.firstOrNull { it.cell == c } }?.let { formatClock(it.atSec) }
            return if (clock != null) {
                "套用到已選的 1 張 · $clock"
            } else {
                "套用到已選的 ${selected.size} 張"
            }
        }

    /**
     * 主按鈕現在按下去是【完成】還是【套用】。**與 [mainButtonLabel] 同一個判斷**——
     * 分成兩處寫的話，日後改了其中一個，按鈕的字就會與它真正做的事對不上。
     */
    val mainActionIsFinish: Boolean get() = patch.isEmpty

    /**
     * 主按鈕。**沒動過欄位時是【完成】**；動了任一欄位就變成【套用到 N 張】
     * —— 流程終點永遠是最搶眼的那一顆（規格第五節、手冊 §四第三步）。
     */
    val mainButtonLabel: String
        get() = if (mainActionIsFinish) "完成" else "套用到 ${selected.size} 張"

    /** 「將更新：地點、標籤　其他欄位維持各張原值」。沒動過欄位時不顯示。 */
    val hintLine: String?
        get() = if (patch.isEmpty) null else "將更新：${patch.changedLabels.joinToString("、")}　其他欄位維持各張原值"

    /** 進場裁圖的進度。sheet 已在本機，這行幾乎一閃而過（規格第五節）。 */
    val progressText: String? get() = if (cropping && cropTotal > 0) "處理縮圖 $cropDone/$cropTotal" else null

    private fun <T> field(pick: (ShotDetails) -> T): Common<T> =
        commonOf(selected.sorted().mapNotNull { details[it] }.map(pick))

    val eventDateField: Common<String> get() = field { it.eventDate }
    val placeField: Common<String?> get() = field { it.place }
    val descriptionField: Common<String?> get() = field { it.description }
    val tagsField: Common<List<String>> get() = field { it.tags }
}

/**
 * 第三步的狀態機。**刻意不依賴 Android** —— 勾選、快捷列、抽屜共同值這些規則
 * 用普通 JUnit 就測得完，與 [Step2Store] 同一個取捨。
 *
 * @param defaultEventDate 每一格的時間預設值（YouTube 上傳日期，見 `eventDateOf`）
 */
class Step3Store(
    cells: List<Step3Cell>,
    private val defaultEventDate: String,
) {

    private val _state = MutableStateFlow(
        Step3State(
            cells = cells,
            details = cells.associate { it.cell to ShotDetails(eventDate = defaultEventDate) },
            // 進場預設全選：第二步剛勾完的那些，多數情況第一輪就是要一起填
            selected = cells.map { it.cell }.toSet(),
        )
    )
    val state: StateFlow<Step3State> = _state.asStateFlow()

    /**
     * 換勾選就把還沒套用的編輯清掉。
     *
     * 抽屜永遠在編輯「目前勾選的那些」（規格第五節）—— 留著上一組的輸入，
     * 按下【套用到 N 張】就會把上一組的意圖套到這一組身上。
     */
    private fun select(next: Set<Int>) {
        _state.value = _state.value.copy(selected = next, patch = DetailsPatch())
    }

    fun toggle(cell: Int) {
        val current = _state.value.selected
        select(if (cell in current) current - cell else current + cell)
    }

    fun selectAll() = select(_state.value.cells.map { it.cell }.toSet())

    fun selectNone() = select(emptySet())

    fun invert() {
        val current = _state.value
        select(current.cells.map { it.cell }.filterNot { it in current.selected }.toSet())
    }

    /** 【未填的】：一鍵勾選所有沒有綠點的，收尾時用（規格第五節快捷列）。 */
    fun selectUnapplied() = select(_state.value.unappliedCells)

    fun editEventDate(value: String) = edit { it.copy(eventDate = value) }

    fun editPlace(value: String) = edit { it.copy(place = value) }

    fun editDescription(value: String) = edit { it.copy(description = value) }

    fun editTags(value: List<String>) = edit { it.copy(tags = normalizeTags(value)) }

    private fun edit(block: (DetailsPatch) -> DetailsPatch) {
        _state.value = _state.value.copy(patch = block(_state.value.patch))
    }

    /** 【套用到 N 張】。套完就把抽屜的差異清掉 —— 主按鈕跟著變回【完成】。 */
    fun applyPatch() {
        val current = _state.value
        if (current.patch.isEmpty) return
        _state.value = current.copy(
            details = applyToCells(current.details, current.selected, current.patch),
            patch = DetailsPatch(),
        )
    }

    fun setCropProgress(done: Int, total: Int) {
        _state.value = _state.value.copy(cropDone = done, cropTotal = total)
    }

    fun finishCropping() {
        _state.value = _state.value.copy(cropping = false)
    }

    /**
     * 從草稿還原。**只蓋掉草稿記得的那幾格** ——
     * 草稿存的時候可能還沒有全部的格子（例如在第二步就離開了），
     * 沒記到的格子必須保有預設值，否則入庫時它們的 event_date 是空的。
     */
    fun restore(details: Map<Int, ShotDetails>, selected: Set<Int>) {
        val current = _state.value
        _state.value = current.copy(
            details = current.details + details.filterKeys { key -> current.cells.any { it.cell == key } },
            selected = selected.filter { key -> current.cells.any { it.cell == key } }.toSet(),
            patch = DetailsPatch(),
        )
    }
}
