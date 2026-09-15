package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.similarity.Fingerprint
import com.xenyaa.videoshot.core.similarity.converge
import com.xenyaa.videoshot.wizard.frames.FramePlan
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xenyaa.videoshot.wizard.frames.FrameSource
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一張手動補圖（【截圖】或【從相簿選】產生的）。
 *
 * **格號接在 storyboard 的格數之後往上編**（`plan.frameCount`、`+1`、`+2`…）。
 * 這樣 [Step2State] 的 `ready`／`kept`／`selected`／`taken` 全部維持 `Set<Int>`，
 * 階段 4b 驗收過的收斂與勾選規則一行都不用動。代價是「格號 ≥ frameCount 就是手動圖」
 * 這個約定是隱性的 —— 它只能出現在 [Step2State.isManual] 與 [Step2State.atSecOf] 裡，
 * 不要讓這個比較散落到畫面程式碼。
 *
 * @param atSec 截圖來的是「與圖同一瞬間」的秒數；相簿來的是播放器當下的近似值
 * @param fromGallery 從相簿選的。**只有它的時間可以 ±1 秒微調**（規格第五節、手冊第 93 行）——
 *        截圖的圖與秒數是同一瞬間取的，一調就對不上了
 */
data class ManualCell(
    val cellIndex: Int,
    val atSec: Double,
    val file: File,
    val fromGallery: Boolean,
)

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
    /** 載入中途整條 flow 掛掉（磁碟滿、OOM…）。**畫得出來**才叫降級，不然使用者只看到卡住的進度文案。 */
    val loadFailed: Boolean = false,
    val hintSeen: Boolean = true,
    /** 手動補圖，依加入順序。牆上的位置由 [atSecOf] 決定，不是這個順序。 */
    val manual: List<ManualCell> = emptyList(),
) {
    val selectedCount: Int get() = selected.size

    /**
     * 牆上可以挑的總格數 = 收斂後保留的 ＋ 手動補上的。
     *
     * **手動格一定要算進去** —— 它就在牆上、也選得到；只數 [kept] 的話，
     * 截一張圖之後底部會寫「127 張候選」但牆上其實有 128 格。
     */
    val candidateCount: Int get() = kept.size + manual.size

    /** 格號 ≥ storyboard 的格數就是手動補圖。 */
    fun isManual(cell: Int): Boolean = cell >= plan.frameCount

    /**
     * 這一格對應影片的第幾秒。手動格查表，storyboard 格直接取 [FramePlan.atSec]。
     * 查不到的手動格回 0.0 —— 排序不該為了一筆髒資料而爆掉。
     */
    fun atSecOf(cell: Int): Double =
        if (isManual(cell)) {
            manual.firstOrNull { it.cellIndex == cell }?.atSec ?: 0.0
        } else {
            plan.atSec.getOrElse(cell) { 0.0 }
        }

    /**
     * 目前牆上要畫哪些格。
     *
     * **收斂算完之前顯示全部已就緒的格子**（規格第五節）——
     * 若邊載邊收合，牆會在使用者眼前一直跳動。
     */
    val visible: List<Int>
        get() {
            val base = if (converging || showAll) ready.sorted() else kept.filter { it in ready }
            // 手動格永遠在牆上 —— 收斂不該把使用者自己剛補的圖藏掉
            val all = base + manual.map { it.cellIndex }
            val shown = if (onlySelected) all.filter { it in selected } else all
            // 依時間排序：手動圖要插在「對應時間的位置」（規格第五節）
            return shown.sortedBy { atSecOf(it) }
        }

    /** 頂部狀態列的文案。手冊 §四要求它講得出「收斂成幾張、藏了幾張」。 */
    val statusText: String
        get() = when {
            plan.frameCount == 0 -> "無法取得逐段縮圖，可以截圖補上"
            converging -> "正在過濾相似畫面…"
            loadFailed && ready.isEmpty() -> "縮圖載入失敗，可以截圖補上"
            loadFailed -> "只載入了 ${ready.size} 張縮圖就失敗了，其餘可以截圖補上"
            showAll -> "顯示全部 ${plan.frameCount} 張"
            hiddenCount > 0 -> "已收斂成 $candidateCount 張候選，隱藏了 $hiddenCount 張相似畫面"
            else -> "$candidateCount 張候選"
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

    /** 有指紋的格號。沒有指紋的格子是 [keptWith] 要補回來的那一批。 */
    private val fingerprinted = mutableSetOf<Int>()

    /**
     * 收斂是**時間順序**的前向掃描，所以送進 `converge` 之前一定要照格號排好。
     * sheet 是同時下載 4 張的，抵達順序不保證是時間順序 ——
     * 沒有這一行，先回來的後段 sheet 會把收斂的比較基準弄亂。
     *
     * 排序產生的是一份複本，而且**一定在 scope 的執行緒上取**（不是在 `compute` 裡面）——
     * 送進背景的必須是複本，否則重算收斂的期間 load 又 append 進來就是 `ConcurrentModificationException`。
     */
    private fun ordered(): List<Fingerprint> = fingerprints.sortedBy { it.frameIndex }

    /**
     * 收斂結果**加上沒有指紋的格子**。
     *
     * 降級成封面圖的批次（重抓 spec 後仍 403）發的是空指紋，`converge` 從來沒看過那些格號，
     * 不補回來的話它們會在 `converging` 翻成 false 的那一刻整批從牆上消失 ——
     * 使用者看著格子出現、然後不見；全部 sheet 都 403 時更是整面空牆，
     * 而【顯示全部】這個逃生口的顯示條件是 `hiddenCount > 0`，也一起被藏起來。
     * 規格第五節要求降級後「使用者仍能靠時間標籤與播放器挑格」，所以**沒有比較依據的一律保留**。
     */
    private fun keptWith(kept: List<Int>, ready: Set<Int>): List<Int> =
        (kept + (ready - fingerprinted)).sorted()

    private var strength: FilterStrength = FilterStrength.MEDIUM

    /**
     * 下載與收斂的工作。**握在手上是為了 [close] 取消得掉** ——
     * 它是 launch 進共用的 `viewModelScope` 的，不取消的話「回第一步 → 貼另一支網址」之後，
     * 前一支影片整套 sheet 下載會繼續跑：跟新的那一套一起就是 8 個並行請求（真實作限制同時 4 張），
     * 還會繼續寫進舊影片的 `drafts/`，並碰一個 `close()` 已經跑過的 [FrameSource]。
     */
    private val loadJob: Job = scope.launch {
        try {
            source.load().collect { batch ->
                fingerprints += batch.fingerprints
                batch.fingerprints.forEach { fingerprinted += it.frameIndex }
                val snapshot = ordered()
                val result = withContext(compute) { converge(snapshot, strength) }
                val ready = _state.value.ready + batch.frameIndexes
                _state.value = _state.value.copy(
                    ready = ready,
                    kept = keptWith(result.kept, ready),
                    hiddenCount = result.hiddenCount,
                    degradedToCover = _state.value.degradedToCover || batch.degradedToCover,
                )
            }
        } catch (e: CancellationException) {
            throw e      // close() 取消的，不是失敗
        } catch (e: Exception) {
            // 功能降級，絕不當機（規格第三節設計原則第 6 條）：終端失敗就停在已經拿到的那幾張，
            // 讓狀態列講得出來。這裡不接的話例外會從 viewModelScope 傳出去、把 process 殺掉
            _state.value = _state.value.copy(loadFailed = true)
        } catch (e: OutOfMemoryError) {
            // sheet 解圖是整條管線最可能 OOM 的地方，而 OOM 不是 Exception
            _state.value = _state.value.copy(loadFailed = true)
        }
        // 不論成功或失敗都要翻掉，否則狀態列永遠卡在「正在過濾相似畫面…」
        _state.value = _state.value.copy(converging = false)
    }

    /** 換強度的重算。只留最後一次 —— 兩次重算並行時誰後貼上誰贏，結果可能不是最新的強度。 */
    private var strengthJob: Job? = null

    fun setStrength(value: FilterStrength) {
        if (value == strength) return
        strength = value
        strengthJob?.cancel()
        strengthJob = scope.launch {
            val snapshot = ordered()
            val result = withContext(compute) { converge(snapshot, value) }
            val current = _state.value
            _state.value = current.copy(
                kept = keptWith(result.kept, current.ready),
                hiddenCount = result.hiddenCount,
            )
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

    /**
     * 補一張手動圖進牆上，**預設已勾選**（規格第五節：「以已勾選狀態插入縮圖牆」）。
     *
     * @return 新格子的格號
     */
    fun addManual(atSec: Double, file: File, fromGallery: Boolean = false): Int {
        val current = _state.value
        val cell = current.plan.frameCount + current.manual.size
        _state.value = current.copy(
            manual = current.manual + ManualCell(cell, atSec, file, fromGallery),
            selected = current.selected + cell,
            ready = current.ready + cell,
        )
        return cell
    }

    /**
     * ±1 秒微調。**只對相簿選來的圖有效**（規格第五節、手冊第 93 行）——
     * 截圖的秒數與圖是同一瞬間取的，調了就對不上。
     *
     * 畫面上本來就只對相簿來的格子顯示微調鈕，這裡**再擋一次**：
     * 規格明訂的規則不該只靠畫面把關。
     */
    fun nudgeManual(cell: Int, deltaSec: Double) {
        val current = _state.value
        _state.value = current.copy(
            manual = current.manual.map {
                if (it.cellIndex == cell && it.fromGallery) {
                    it.copy(atSec = (it.atSec + deltaSec).coerceAtLeast(0.0))
                } else {
                    it
                }
            },
        )
    }

    /**
     * 第 N 格的圖，**storyboard 與手動補圖都走這裡**。
     *
     * 解不出來一律回 null、不丟例外（與 [FrameSource] 同契約）：檔案被清掉、
     * 內容壞掉、解到一半 OOM，都只該讓這一格空著。
     */
    suspend fun bitmapOfCell(cell: Int): ImageBitmap? {
        val entry = _state.value.manual.firstOrNull { it.cellIndex == cell }
            ?: return source.bitmapOf(cell)
        return withContext(compute) {
            try {
                BitmapFactory.decodeFile(entry.file.path)?.asImageBitmap()
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    fun markPlaying(frameIndex: Int?) {
        _state.value = _state.value.copy(playingFrame = frameIndex)
    }

    /**
     * 離開這一步。**先取消工作再放快取** —— 還在跑的下載不收掉的話，
     * 它會繼續碰一個已經放掉快取的 [FrameSource]（見 [loadJob]）。
     */
    fun close() {
        loadJob.cancel()
        strengthJob?.cancel()
        source.close()
    }
}
