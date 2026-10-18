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
 */
data class Step3State(
    val cells: List<Step3Cell>,
    val details: Map<Int, ShotDetails>,
    val selected: Set<Int>,
    val cropping: Boolean = true,
    val cropDone: Int = 0,
    val cropTotal: Int = 0,
    /**
     * 這一輪各次在選擇器按【確定】時填的地點與標籤（沒動過的欄位不算；清空地點不算）。
     * 「新加」以寫入前的圖資判斷：標籤要不在勾選格子寫入前各自標籤的聯集裡，地點要至少一張勾選格子原本不是它。
     * 【完成】寫入成功後記成最近使用（規格第六節「首頁」）。**不進草稿**：從草稿接續時離開前那幾輪不記。
     */
    val usedPlaces: Set<String> = emptySet(),
    val usedTags: Set<String> = emptySet(),
) {
    /**
     * 每一張都有這個欄位的值（規格第五節第三步的欄位表「打勾條件」）。
     * 只設了其中幾張不算 —— 按鈕的打勾是「這一欄做完了」，不是「動過了」。
     */
    fun isComplete(field: DetailField): Boolean =
        cells.isNotEmpty() && cells.all { details[it.cell]?.has(field) == true }

    /** 取圖精靈的【完成】按得下去嗎：每一張都有時間與地點（2026-10-18 scott）。批次編輯不看這個。 */
    val canFinish: Boolean get() = isComplete(DetailField.DATE) && isComplete(DetailField.PLACE)

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
 * 第三步的狀態機。**刻意不依賴 Android** —— 勾選、選擇器的起始值這些規則
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

    /** 換勾選。選擇器每次打開都從勾選那幾張的現值開始，沒有要清的東西。 */
    private fun select(next: Set<Int>) {
        _state.value = _state.value.copy(selected = next)
    }

    fun toggle(cell: Int) {
        val current = _state.value.selected
        select(if (cell in current) current - cell else current + cell)
    }

    /**
     * 【全選】：還沒全選就勾選每一張；已經全選時再按一次＝全部取消勾選（同第二步 `Step2Store.selectAll()`）。
     * 批次編輯共用這個 store，行為一起生效。
     */
    fun selectAll() {
        val all = _state.value.cells.map { it.cell }.toSet()
        select(if (all.isNotEmpty() && _state.value.selected.containsAll(all)) emptySet() else all)
    }

    /** 全部取消勾選。畫面上沒有【全不選】了（2026-10-17 拿掉），一張張點掉是同一件事；留給測試與狀態還原用。 */
    fun selectNone() = select(emptySet())

    /**
     * 選擇器按【確定】：把 [patch] 寫進目前勾選的每一張（規格第五節「寫入語意」）。
     * 「沒動過就不改」由選擇器自己判斷，沒動過就不會呼叫到這裡；這裡只負責沒動過的**欄位**原封不動。
     * 勾選不變 —— 使用者常常接著替同一批設下一個欄位。
     */
    fun applyToSelected(patch: DetailsPatch) {
        val current = _state.value
        if (current.selected.isEmpty() || patch.isEmpty) return
        // 「新加」要用寫入前的圖資判斷：標籤選擇器送出的是整份清單（起始值是勾選格子的共同標籤）
        val before = current.selected.mapNotNull { current.details[it] }
        val beforeTags = before.flatMapTo(mutableSetOf()) { it.tags }
        val newPlace = patch.place?.trim()?.takeIf { it.isNotEmpty() && before.any { d -> d.place != it } }
        _state.value = current.copy(
            details = applyToCells(current.details, current.selected, patch),
            usedPlaces = current.usedPlaces + listOfNotNull(newPlace),
            usedTags = current.usedTags + (normalizeTags(patch.tags.orEmpty()) - beforeTags),
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
        )
    }
}
