package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.similarity.Fingerprint
import com.xenyaa.videoshot.core.similarity.converge
import com.xenyaa.videoshot.wizard.frames.FramePlan
import com.xenyaa.videoshot.wizard.frames.FrameSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 第二步的畫面狀態。
 *
 * @param ready 已經有圖可以畫的格號
 * @param kept 收斂後保留的格號（時間順序）
 * @param taken 先前已收藏過的格號 —— 灰＋鎖，點下去出提示而不是靜靜沒反應
 * @param playingFrame 播放器目前停在哪一格（藍框）
 */
data class Step2State(
    val plan: FramePlan,
    val ready: Set<Int> = emptySet(),
    val converging: Boolean = true,
    val kept: List<Int> = emptyList(),
    val hiddenCount: Int = 0,
    val showAll: Boolean = false,
    val onlySelected: Boolean = false,
    val selected: Set<Int> = emptySet(),
    val taken: Set<Int> = emptySet(),
    val playingFrame: Int? = null,
    val degradedToCover: Boolean = false,
    val hintSeen: Boolean = true,
) {
    val selectedCount: Int get() = selected.size

    /**
     * 目前牆上要畫哪些格。
     *
     * **收斂算完之前顯示全部已就緒的格子**（規格第五節）——
     * 若邊載邊收合，牆會在使用者眼前一直跳動。
     */
    val visible: List<Int>
        get() {
            val base = if (converging || showAll) ready.sorted() else kept.filter { it in ready }
            return if (onlySelected) base.filter { it in selected } else base
        }

    /** 頂部狀態列的文案。手冊 §四要求它講得出「收斂成幾張、藏了幾張」。 */
    val statusText: String
        get() = when {
            plan.frameCount == 0 -> "無法取得逐段縮圖，可以截圖補上"
            converging -> "正在過濾相似畫面…"
            showAll -> "顯示全部 ${plan.frameCount} 張"
            hiddenCount > 0 -> "已收斂成 ${kept.size} 張候選，隱藏了 $hiddenCount 張相似畫面"
            else -> "${kept.size} 張候選"
        }
}

/**
 * 第二步的狀態機。**刻意不依賴 Android** —— 收斂、勾選、篩選這些規則用普通 JUnit 就測得完，
 * 不必為了驗一條規則去啟動 Robolectric。
 *
 * @param compute 收斂是純計算，規格第三節設計原則第 5 條要求放 Default
 */
class Step2Store(
    /** 公開給畫面拿圖用 —— [Step2GridScreen] 要靠它 `bitmapOf` 每一格。 */
    val source: FrameSource,
    taken: Set<Int>,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow(Step2State(plan = source.plan, taken = taken))
    val state: StateFlow<Step2State> = _state.asStateFlow()

    /** 收斂的輸入。換強度時對它整個重算，不是在已過濾的結果上疊加（規格第五節）。 */
    private val fingerprints = mutableListOf<Fingerprint>()

    /**
     * 收斂是**時間順序**的前向掃描，所以送進 `converge` 之前一定要照格號排好。
     * sheet 是同時下載 4 張的，抵達順序不保證是時間順序 ——
     * 沒有這一行，先回來的後段 sheet 會把收斂的比較基準弄亂。
     */
    private fun ordered(): List<Fingerprint> = fingerprints.sortedBy { it.frameIndex }

    private var strength: FilterStrength = FilterStrength.MEDIUM

    init {
        scope.launch {
            source.load().collect { batch ->
                fingerprints += batch.fingerprints
                val result = withContext(compute) { converge(ordered(), strength) }
                _state.value = _state.value.copy(
                    ready = _state.value.ready + batch.frameIndexes,
                    kept = result.kept,
                    hiddenCount = result.hiddenCount,
                    degradedToCover = _state.value.degradedToCover || batch.degradedToCover,
                )
            }
            _state.value = _state.value.copy(converging = false)
        }
    }

    fun setStrength(value: FilterStrength) {
        if (value == strength) return
        strength = value
        scope.launch {
            val result = withContext(compute) { converge(ordered(), value) }
            _state.value = _state.value.copy(kept = result.kept, hiddenCount = result.hiddenCount)
        }
    }

    fun setHintSeen(seen: Boolean) {
        _state.value = _state.value.copy(hintSeen = seen)
    }

    fun setShowAll(value: Boolean) {
        _state.value = _state.value.copy(showAll = value)
    }

    fun setOnlySelected(value: Boolean) {
        _state.value = _state.value.copy(onlySelected = value)
    }

    /**
     * 切換勾選。
     * @return false 代表這一格先前已收藏過 —— 呼叫端要顯示「這一格已經收藏過了」
     */
    fun toggle(frameIndex: Int): Boolean {
        val current = _state.value
        if (frameIndex in current.taken) return false
        val next = if (frameIndex in current.selected) {
            current.selected - frameIndex
        } else {
            current.selected + frameIndex
        }
        _state.value = current.copy(selected = next)
        return true
    }

    /** 全選／全不選切換，**已收藏的格子不納入**，而且只作用在看得到的格子上（規格第五節工具列）。 */
    fun selectAll() {
        val current = _state.value
        val selectable = current.visible.filterNot { it in current.taken }.toSet()
        val next = if (selectable.isNotEmpty() && current.selected.containsAll(selectable)) {
            current.selected - selectable
        } else {
            current.selected + selectable
        }
        _state.value = current.copy(selected = next)
    }

    fun markPlaying(frameIndex: Int?) {
        _state.value = _state.value.copy(playingFrame = frameIndex)
    }

    fun close() = source.close()
}
