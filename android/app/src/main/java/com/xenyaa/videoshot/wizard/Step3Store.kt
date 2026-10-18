package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.details.applyToCells
import com.xenyaa.videoshot.core.details.commonOf
import com.xenyaa.videoshot.core.details.normalizeTags
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

/** 第三步底部的四個欄位按鈕，順序同畫面（時間、地點、標籤、描述）。 */
enum class DetailField { DATE, PLACE, TAGS, DESCRIPTION }

/** 這一張的這個欄位有沒有值。縮圖左下的圖示亮不亮、欄位按鈕打不打勾都看這個。 */
fun ShotDetails.has(field: DetailField): Boolean = when (field) {
    DetailField.DATE -> eventDate.isNotBlank()
    DetailField.PLACE -> !place.isNullOrBlank()
    DetailField.TAGS -> tags.isNotEmpty()
    DetailField.DESCRIPTION -> !description.isNullOrBlank()
}

/**
 * 第三步的畫面狀態。
 *
 * @param details 每一格目前的圖資。**鍵一定涵蓋 [cells] 的每一格** —— 少一格就是入庫時少一筆 event_date
 * @param patch 抽屜裡「動過但還沒套用」的部分
 */
data class Step3State(
    val cells: List<Step3Cell>,
    val details: Map<Int, ShotDetails>,
    val selected: Set<Int>,
    val patch: DetailsPatch = DetailsPatch(),
    val cropping: Boolean = true,
    val cropDone: Int = 0,
    val cropTotal: Int = 0,
    /**
     * 剛剛那一下【套用】套到幾張；`null`＝沒有提示。**是「剛剛那一下」的回饋**：
     * 使用者一動欄位或勾選就清回 `null`（設計文件決定 6），所以只有 [Step3Store.applyPatch] 會設它。
     */
    val lastAppliedCount: Int? = null,
    /**
     * 這一輪各次【套用】時抽屜裡填的地點與標籤（沒動過的欄位不算；清空地點不算）。
     * 「新加」以套用前的圖資判斷：標籤要不在勾選格子套用前各自標籤的聯集裡，地點要至少一張勾選格子原本不是它。
     * 【完成】寫入成功後記成最近使用（規格第六節「首頁」）。**不進草稿**：從草稿接續時離開前那幾輪不記。
     */
    val usedPlaces: Set<String> = emptySet(),
    val usedTags: Set<String> = emptySet(),
) {
    val appliedCount: Int get() = details.count { (cell, d) -> d.applied && cells.any { it.cell == cell } }

    val unappliedCells: Set<Int>
        get() = cells.map { it.cell }.filterNot { details[it]?.applied == true }.toSet()

    /** 底部 dock 的狀態文字「18 張 · 8 已完成」（規格第五節第三步的線框；沒有 [appliedNotice] 時顯示）。 */
    val headerText: String get() = "${cells.size} 張 · $appliedCount 已完成"

    /** 套用後的短暫提示「已套用到 N 張」。沒有剛套用過就是 `null`。 */
    val appliedNotice: String? get() = lastAppliedCount?.let { "已套用到 $it 張" }

    /**
     * 每一張都有這個欄位的值（規格第五節第三步的欄位表「打勾條件」）。
     * 只設了其中幾張不算 —— 按鈕的打勾是「這一欄做完了」，不是「動過了」。
     */
    fun isComplete(field: DetailField): Boolean =
        cells.isNotEmpty() && cells.all { details[it.cell]?.has(field) == true }

    /** 取圖精靈的【完成】按得下去嗎：每一張都有時間與地點（2026-10-18 scott）。批次編輯不看這個。 */
    val canFinish: Boolean get() = isComplete(DetailField.DATE) && isComplete(DetailField.PLACE)

    /**
     * 【完成】按得下去嗎：「未填 N」歸 0，也就是每一張都套用過（2026-10-17 scott）。
     * 取圖精靈才有這條規則；批次編輯只寫回套用過的那幾張，不需要全填。
     */
    val allFilled: Boolean get() = unappliedCells.isEmpty()

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
        _state.value = _state.value.copy(selected = next, patch = DetailsPatch(), lastAppliedCount = null)
    }

    fun toggle(cell: Int) {
        val current = _state.value.selected
        select(if (cell in current) current - cell else current + cell)
    }

    fun selectAll() = select(_state.value.cells.map { it.cell }.toSet())

    /** 全部取消勾選。畫面上沒有【全不選】了（2026-10-17 拿掉），一張張點掉是同一件事；留給測試與狀態還原用。 */
    fun selectNone() = select(emptySet())

    fun invert() {
        val current = _state.value
        select(current.cells.map { it.cell }.filterNot { it in current.selected }.toSet())
    }

    /** 【未填的】：一鍵勾選所有還沒套用（沒有打勾標記）的，收尾時用（規格第五節快捷列）。 */
    fun selectUnapplied() = select(_state.value.unappliedCells)

    fun editEventDate(value: String) = edit { it.copy(eventDate = value) }

    fun editPlace(value: String) = edit { it.copy(place = value) }

    fun editDescription(value: String) = edit { it.copy(description = value) }

    fun editTags(value: List<String>) = edit { it.copy(tags = normalizeTags(value)) }

    private fun edit(block: (DetailsPatch) -> DetailsPatch) {
        _state.value = _state.value.copy(patch = block(_state.value.patch), lastAppliedCount = null)
    }

    /**
     * 選擇器按【確定】：把 [patch] 寫進目前勾選的每一張（規格第五節「寫入語意」）。
     * 「沒動過就不改」由選擇器自己判斷，沒動過就不會呼叫到這裡；這裡只負責沒動過的**欄位**原封不動。
     * 勾選不變 —— 使用者常常接著替同一批設下一個欄位。
     */
    fun applyToSelected(patch: DetailsPatch) {
        val current = _state.value
        if (current.selected.isEmpty() || patch.isEmpty) return
        // 「新加」要用套用前的圖資判斷：標籤選擇器送出的是整份清單（起始值是勾選格子的共同標籤）
        val before = current.selected.mapNotNull { current.details[it] }
        val beforeTags = before.flatMapTo(mutableSetOf()) { it.tags }
        val newPlace = patch.place?.trim()?.takeIf { it.isNotEmpty() && before.any { d -> d.place != it } }
        _state.value = current.copy(
            details = applyToCells(current.details, current.selected, patch),
            usedPlaces = current.usedPlaces + listOfNotNull(newPlace),
            usedTags = current.usedTags + (normalizeTags(patch.tags.orEmpty()) - beforeTags),
        )
    }

    /**
     * 抽屜標題列的【套用】。套完就把抽屜的差異清掉。
     *
     * **沒改任何欄位也能套用**：等於「這幾張照現在的值確認過了」，一樣算已完成（右上綠勾）——
     * 取圖精靈要每一張都套用過才能【完成】，只想沿用預設時間的那幾張不能卡在這裡。
     */
    fun applyPatch() {
        val current = _state.value
        if (current.selected.isEmpty()) return
        if (current.patch.isEmpty) {
            _state.value = current.copy(
                details = current.details.mapValues { (cell, d) -> if (cell in current.selected) d.copy(applied = true) else d },
                lastAppliedCount = current.selected.size,
            )
            return
        }
        // 「新加」要用套用前的圖資判斷：抽屜的標籤欄位送出的是整份清單（起始值是勾選格子的共同標籤）
        val before = current.selected.mapNotNull { current.details[it] }
        val beforeTags = before.flatMapTo(mutableSetOf()) { it.tags }
        val newPlace = current.patch.place?.trim()?.takeIf { it.isNotEmpty() && before.any { d -> d.place != it } }
        _state.value = current.copy(
            details = applyToCells(current.details, current.selected, current.patch),
            patch = DetailsPatch(),
            lastAppliedCount = current.selected.size,
            usedPlaces = current.usedPlaces + listOfNotNull(newPlace),
            usedTags = current.usedTags + (normalizeTags(current.patch.tags.orEmpty()) - beforeTags),
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
