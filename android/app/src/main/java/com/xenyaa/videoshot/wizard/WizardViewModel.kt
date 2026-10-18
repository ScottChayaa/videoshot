package com.xenyaa.videoshot.wizard

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.details.eventDateOf
import com.xenyaa.videoshot.core.draft.DraftCodec
import com.xenyaa.videoshot.core.draft.DraftManual
import com.xenyaa.videoshot.core.draft.DraftPayload
import com.xenyaa.videoshot.core.draft.toDraftDetails
import com.xenyaa.videoshot.core.draft.toDraftMeta
import com.xenyaa.videoshot.core.draft.toShotDetails
import com.xenyaa.videoshot.core.draft.toVideoMeta
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.url.parseVideoId
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.capture.Capture
import com.xenyaa.videoshot.capture.CaptureResult
import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.capture.encodeManualWebp
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.wizard.frames.FrameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 精靈的流程狀態。放在 ViewModel 是為了**轉螢幕不丟東西** ——
 * 已經抓好的 watch page 與已勾選的格子重來一次代價太大。
 */
class WizardViewModel(
    private val data: WizardData,
    private val frameSourceFactory: (LoadedVideo) -> FrameSource,
    private val strength: Flow<FilterStrength>,
    private val hintSeen: Flow<Boolean>,
    private val onHintSeen: suspend () -> Unit,
    /** 這支影片的手動圖存放處（`drafts/{videoId}/manual/`）。 */
    private val manualImages: (String) -> ManualImageStore,
    /**
     * 播放器接上之後要用哪個截圖器。正式環境是 `WebViewCapture`，測試傳假的。
     * 用 factory 而不是直接收 `Capture`：真實作需要**這一個** WebView 的 eval 入口，
     * 而 WebView 是畫面建的，ViewModel 建構時還不存在。
     */
    private val captureFor: (Player) -> Capture?,
    /** 注入而不是直接呼叫 `LocalDate.now()` —— 測試要能確定地驗「上傳日期缺漏時用今天」。 */
    private val today: () -> String = { java.time.LocalDate.now().toString() },
    /** 【完成】寫入成功後記下這一輪套用過的地點與標籤（`FacetUsage.markUsed`，規格第六節「首頁」）。盡力而為。 */
    private val markFacetsUsed: suspend (places: Set<String>, tags: Set<String>) -> Unit = { _, _ -> },
) : ViewModel() {

    private val _captureError = MutableStateFlow<CaptureError?>(null)
    val captureError: StateFlow<CaptureError?> = _captureError.asStateFlow()

    /** 第二步播放器目前播到第幾秒（播放器下方的進度條用）。由畫面定時呼叫 [pollPlayback] 更新。 */
    private val _playbackSec = MutableStateFlow(0.0)
    val playbackSec: StateFlow<Double> = _playbackSec.asStateFlow()

    private var capture: Capture? = null

    private val _step = MutableStateFlow(WizardStep.URL)
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    /** 有沒有值得留的草稿：一離開第一步就有。 */
    private var hasDraft = false

    fun goTo(target: WizardStep) {
        _step.value = target
        // 一離開第一步就有草稿可留 —— 使用者已經投入了選擇
        if (target != WizardStep.URL) hasDraft = true
        // 每次進第三步都重建 —— 使用者可能回第二步改了勾選，舊的格子清單已經不對了
        if (target == WizardStep.DETAILS) openStep3()
        // 存檔時機之一：步驟變了。openStep3() 已經跑過，_step3 才有值
        if (target != WizardStep.URL) saveDraft()
    }

    /**
     * 處理返回（頂欄返回鍵與 Android 返回）。
     * 離開第二、三步前先存一次草稿 —— 精靈沒有離開確認框，之後從第一步返回就直接走了，
     * 這裡不存的話，上次存檔之後的勾選與圖資會丟掉。存的是**離開的那一步**，續做時回到這裡。
     * @return true 代表精靈自己消化掉了；false 代表在第一步，交給系統（離開精靈）
     */
    fun back(): Boolean {
        val current = _step.value
        if (current == WizardStep.URL) return false
        // 續做還沒接上（骨架屏或「取圖需要網路」）：停掉、回第一步，草稿留著下次進場再接
        resuming?.let { payload ->
            resumeJob?.cancel()
            resumeJob = null
            resuming = null
            pendingDraft = payload
            _resumeError.value = null
            _step.value = WizardStep.URL
            return true
        }
        if (hasDraft) saveDraft()
        _step.value = WizardStep.entries.first { it.order == current.order - 1 }
        return true
    }

    /**
     * 精靈畫面正在顯示（使用者按了【取圖】）。草稿比進場晚讀到時（app 剛啟動），讀到的當下就續做。
     * 轉螢幕會重建畫面、再呼叫一次 [onEnter]，所以「離開時停在哪一步」只在真的離開時記（[onExitKeepingDraft]）。
     */
    private var entered = false

    /** 用返回鍵離開精靈時記下的那一步；下次進場直接回到這裡。 */
    private var resumeOnEnter: WizardStep? = null

    /**
     * 進入精靈（按【取圖】）。**有中斷的流程就直接接上，不再詢問**（2026-10-18）：
     * - app 重開過：磁碟上有草稿 → 續做（[resumeDraft]）。
     * - app 還活著：上次是一路返回、從第一步離開的 → 回到離開前的那一步，勾選與圖資都還在記憶體裡。
     * 要從頭開始就按右上角 X（[abandon]）。
     */
    fun onEnter() {
        entered = true
        val step = resumeOnEnter
        resumeOnEnter = null
        when {
            pendingDraft != null -> resumeDraft()
            step != null && _step.value == WizardStep.URL && _loaded.value != null -> goTo(step)
        }
    }

    fun onLeave() { entered = false }

    /**
     * 用返回鍵從第一步離開精靈。草稿留著，下次按【取圖】回到第二步 ——
     * 返回只能一步一步退，從第一步離開之前一定是在第二步（磁碟上的草稿存的也是這一步）。
     */
    fun onExitKeepingDraft() {
        if (hasDraft && _loaded.value != null) resumeOnEnter = WizardStep.PICK
    }

    /**
     * 右上角 X：放棄這次取圖。草稿（含手動圖目錄）一起刪，下次按【取圖】從第一步開始。
     * 還在續做的協程要先取消，否則它載完影片會把精靈又帶回第二步。
     */
    fun abandon() {
        val videoIds = setOfNotNull(_loaded.value?.videoId, pendingDraft?.videoId, resuming?.videoId)
        resumeJob?.cancel()
        resumeJob = null
        resuming = null
        _resumeError.value = null
        pendingDraft = null
        resumeOnEnter = null
        hasDraft = false
        _status.value = Step1Status.Idle
        resetToStart()
        viewModelScope.launch { videoIds.forEach { runCatching { data.clearDraft(it) } } }
    }

    // ---- 草稿 ----

    /** app 啟動時讀到、還沒接上的草稿。續做時要靠它把勾選與圖資還原回去。 */
    private var pendingDraft: DraftPayload? = null

    /** 正在續做的協程。按 X 放棄、或按返回時要取消。 */
    private var resumeJob: Job? = null

    /** 正在接上的草稿：從進場切到目標步驟（畫骨架屏）開始，到勾選與圖資還原完為止；失敗時也留著。 */
    private var resuming: DraftPayload? = null

    /** 續做接不上的原因（沒網路、影片抓不到）。第二步就地顯示，按【重試】再接一次。 */
    private val _resumeError = MutableStateFlow<String?>(null)
    val resumeError: StateFlow<String?> = _resumeError.asStateFlow()

    /** ViewModel 跟著 app 啟動一起建；這之後才建或才動過的草稿目錄，啟動時的清理不碰。 */
    private val startedAt = System.currentTimeMillis()

    private fun loadDraft() {
        viewModelScope.launch {
            val text = runCatching { data.currentDraft() }.getOrNull()
            // 解不出來就當作沒有草稿 —— 壞掉的草稿只該讓續做不發生，不該讓 app 開不起來
            val payload = text?.let { DraftCodec.decode(it) }
            if (payload != null) {
                pendingDraft = payload
                if (entered) resumeDraft()
            }
            // 順手清掉沒有草稿指著的影片目錄（換影片、當掉、儀器測試留下的）。
            // 目前的草稿與正在開的影片不刪；清不掉就算了，下次啟動再清
            runCatching {
                data.sweepDraftFiles(
                    keep = { setOfNotNull(payload?.videoId, pendingDraft?.videoId, _loaded.value?.videoId) },
                    olderThanMillis = startedAt,
                )
            }
        }
    }

    /**
     * 續做磁碟上的草稿（進場時自動呼叫，見 [onEnter]）。**一進來就切到草稿停的那一步**，
     * 資料還沒好之前那一步畫骨架屏（2026-10-18：原本先停在第一步約 1 秒才跳過去）。
     *
     * - 草稿裡有上次的 spec 與影片資訊 → **不等網路**，直接用本機的 sheet 建縮圖牆。
     *   簽章過期只影響本機沒有的 sheet，那幾張下載碰到 403 會自己重抓 spec（`StoryboardFrameSource`）。
     * - 舊版草稿沒有 → 先抓 watch page。抓不到（沒網路）就停在那一步顯示錯誤與【重試】，草稿留著。
     */
    private fun resumeDraft() {
        val payload = pendingDraft ?: return
        pendingDraft = null
        resuming = payload
        _resumeError.value = null
        hasDraft = true
        // 直接設、不走 goTo：狀態機還沒建，goTo 會去存草稿、建第三步
        _step.value = if (payload.step >= 3) WizardStep.DETAILS else WizardStep.PICK
        resumeJob = viewModelScope.launch {
            val page = payload.cachedPage() ?: when (val fetched = fetchPage(payload.videoId)) {
                is PageFetch.Failed -> {
                    pendingDraft = payload
                    _resumeError.value = fetched.message
                    return@launch
                }
                is PageFetch.Ok -> fetched.page
            }
            openVideo(payload.videoId, page)
            val store2 = _step2.value ?: return@launch
            val survivors = payload.manual.mapNotNull { m ->
                // 檔案不見了（使用者清了資料）就跳過這一張，其餘照常還原
                manualImages(payload.videoId).fileNamed(m.fileName)
                    ?.let { ManualCell(m.cell, m.atSec, it, m.fromGallery) }
            }
            val plan = store2.state.value.plan
            // 層級變了（續做要重抓 watch page，pickLevel 可能挑到別的層級）＝ **格號空間整個換了一套**：
            // storyboard 的格號在新層級裡指的是別的時間點，留著就是把錯的 frame_index 與 at_sec
            // 寫進 library.db —— 那是唯一推導不回來的東西，所以整批丟掉。
            // 手動圖是使用者親手截的（也不屬於任何層級），重新編號搬進新空間裡
            val levelChanged = payload.level != plan.level || payload.frameCount != plan.frameCount
            val restored = if (levelChanged) {
                survivors.mapIndexed { i, cell -> cell.copy(cellIndex = plan.frameCount + i) }
            } else {
                survivors
            }
            val renumbered = survivors.map { it.cellIndex }.zip(restored.map { it.cellIndex }).toMap()
            val selected = if (levelChanged) {
                // 層級換了只留手動圖，但**勾選狀態要跟著搬，不是一律勾上** ——
                // 使用者刻意取消勾選的截圖不該因為續做就自己回來，那會把他沒要的圖寫進 library.db。
                // renumbered 裡只有活著的手動格，所以 storyboard 的勾選自己就落空了，不必再過濾
                payload.selected.mapNotNull { renumbered[it] }.toSet()
            } else {
                payload.selected.toSet()
            }
            store2.restoreFromDraft(restored, selected)
            resuming = null
            if (payload.step < 3) {
                // 還原完才存（goTo 會存一次草稿）：存進去的是還原後的勾選，而且補上 spec 與影片資訊
                goTo(WizardStep.PICK)
            } else {
                goTo(WizardStep.DETAILS)
                _step3.value?.restore(
                    details = payload.details.mapNotNull { (key, value) ->
                        val cell = key.toIntOrNull() ?: return@mapNotNull null
                        // 搬過家的格子要**連圖資一起搬** —— 用舊格號當鍵會全部落空，
                        // 使用者打過的字就這樣靜靜不見了。查不到新號的（storyboard 格、
                        // 檔案不見的手動格）本來就已經不在牆上，丟掉是對的
                        val target = if (levelChanged) renumbered[cell] else cell
                        target?.let { it to value.toShotDetails() }
                    }.toMap(),
                    selected = selected,
                )
                // goTo 那一步存過一次草稿，但當時 restore 還沒跑，存進去的 details 是空的。
                // 這裡再存一次，讓磁碟上的草稿不依賴協程排程的巧合
                saveDraft()
            }
        }
    }

    /**
     * 把目前的狀態寫成草稿。
     *
     * **只在這幾個時機呼叫**：步驟變了（含返回上一步）、套用過一輪圖資。
     * 不是每次勾選都存 —— 一次取圖會勾上百次，而勾選丟掉的代價（重點幾下）
     * 遠小於圖資丟掉的代價（重打一輪字）。
     */
    private fun saveDraft() {
        val video = _loaded.value ?: return
        val state2 = _step2.value?.state?.value ?: return
        val state3 = _step3.value?.state?.value
        // 在協程外先取：back() 呼叫完馬上換步驟，協程裡再讀就變成上一步了
        val stepOrder = _step.value.order
        viewModelScope.launch {
            val payload = DraftPayload(
                videoId = video.videoId,
                step = stepOrder,
                level = state2.plan.level,
                frameCount = state2.plan.frameCount,
                // **存第二步的勾選，不是第三步的** —— 兩個 store 的 `selected` 意思不同：
                // 第二步的是「使用者挑了哪些」，第三步的是「抽屜正在編輯哪幾張」。
                // 存錯的話，在第三步只挑 3 張套用過圖資再離開，續做回來就只剩那 3 張
                selected = state2.selected.sorted(),
                manual = state2.manual.map {
                    DraftManual(it.cellIndex, it.atSec, it.file.name, it.fromGallery)
                },
                details = state3?.details.orEmpty()
                    .mapKeys { it.key.toString() }
                    .mapValues { it.value.toDraftDetails() },
                // 續做時不必等網路就畫得出縮圖牆（見 [resumeDraft]）
                sbSpec = video.page.storyboardSpec,
                meta = video.page.meta?.toDraftMeta(),
            )
            runCatching { data.saveDraft(DraftCodec.encode(payload)) }
        }
    }

    /** 第三步的【套用到 N 張】走這裡而不是直接呼叫 store —— 套完要順手存一次草稿。 */
    fun applyDetails() {
        _step3.value?.applyPatch()
        saveDraft()
    }

    // ---- 第一步 ----

    private val _status = MutableStateFlow<Step1Status>(Step1Status.Idle)
    val status: StateFlow<Step1Status> = _status.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentVideo>>(emptyList())
    val recent: StateFlow<List<RecentVideo>> = _recent.asStateFlow()

    /** 重試續做（第二步的「取圖需要網路」下面那顆【重試】）。 */
    fun retryResume() {
        if (resuming != null && resumeJob?.isActive != true) resumeDraft()
    }

    /** 草稿裡存的 spec 與影片資訊組回 watch page；舊版草稿沒有就是 null。 */
    private fun DraftPayload.cachedPage(): WatchPage? {
        val m = meta ?: return null
        return WatchPage(
            result = if (sbSpec != null) FetchResult.OK else FetchResult.NO_STORYBOARD,
            meta = m.toVideoMeta(videoId),
            storyboardSpec = sbSpec,
        )
    }

    /** 抓好的 watch page 與已收藏格號，交給第二步用。 */
    private val _loaded = MutableStateFlow<LoadedVideo?>(null)
    val loaded: StateFlow<LoadedVideo?> = _loaded.asStateFlow()

    // `_suggestions` 要宣告在 `init` 之前 —— Kotlin 依文字順序初始化屬性，
    // `init` 裡的協程一旦搶在建構子跑完前就同步執行（Robolectric 的 compose 測試環境會這樣），
    // 引用一個宣告在後面、還沒跑到初始值的屬性就是 NPE。
    private val _suggestions = MutableStateFlow(Suggestions())
    val suggestions: StateFlow<Suggestions> = _suggestions.asStateFlow()

    init {
        reloadRecent()
        viewModelScope.launch {
            _suggestions.value = Suggestions(
                places = runCatching { data.distinctPlaces() }.getOrDefault(emptyList()),
                tags = runCatching { data.tagSuggestions() }.getOrDefault(emptyList()),
            )
        }
        loadDraft()
    }

    fun submit(input: String) {
        val videoId = parseVideoId(input)
        if (videoId == null) {
            _status.value = Step1Status.Error(
                "看不懂這個網址。可以貼 youtu.be/…、watch?v=…、shorts/… 或直接貼 11 碼影片 ID。"
            )
            return
        }
        openRecent(videoId)
    }

    fun openRecent(videoId: String) {
        // 使用者自己開了一支影片：還沒接上的草稿（續做時沒網路而留下的）就不再自動接上，
        // 這支影片進第二步時存的草稿會蓋過它，所以它的目錄也一起清
        val stale = pendingDraft?.videoId
        pendingDraft = null
        resuming = null
        _resumeError.value = null
        viewModelScope.launch {
            openRecentAndAwait(videoId) ?: return@launch
            if (stale != null && stale != videoId) runCatching { data.deleteDraftFiles(stale) }
        }
    }

    /**
     * 第一步開一支影片：抓 watch page、建第二步、切過去。
     * @return 成功載入的影片；失敗回 null（錯誤已經寫進 [_status]，停在第一步）
     */
    private suspend fun openRecentAndAwait(videoId: String): LoadedVideo? {
        _status.value = Step1Status.Loading
        return when (val fetched = fetchPage(videoId)) {
            is PageFetch.Failed -> {
                _status.value = Step1Status.Error(fetched.message); null
            }
            is PageFetch.Ok -> {
                // 建狀態機要先問一次 DB（已收藏的格號），所以**轉圈圈留到這之後才收掉**
                val video = openVideo(videoId, fetched.page)
                _status.value = Step1Status.Idle
                goTo(WizardStep.PICK)
                video
            }
        }
    }

    private sealed interface PageFetch {
        data class Ok(val page: WatchPage) : PageFetch
        data class Failed(val message: String) : PageFetch
    }

    /**
     * 抓 watch page。no_storyboard 與 parse_failed **仍然算抓到** —— 縮圖牆空白，但可以截圖補上
     * （規格第五節第一步的表、第七節降級表）。
     */
    private suspend fun fetchPage(videoId: String): PageFetch {
        val page = data.watchPage(videoId)
        return when (page.result) {
            FetchResult.FETCH_FAILED, FetchResult.RATE_LIMITED -> PageFetch.Failed("取圖需要網路。")
            FetchResult.VIDEO_UNAVAILABLE ->
                PageFetch.Failed("這支影片抓不到，可能是私人影片、已被刪除或需要登入。")
            FetchResult.OK, FetchResult.NO_STORYBOARD, FetchResult.PARSE_FAILED -> PageFetch.Ok(page)
        }
    }

    /** 換上這支影片：建第二步的狀態機（不換步驟，由呼叫端決定）。 */
    private suspend fun openVideo(videoId: String, page: WatchPage): LoadedVideo {
        val previous = _loaded.value?.videoId
        val video = LoadedVideo(videoId = videoId, page = page)
        _loaded.value = video
        openStep2(video)
        // 圖庫存的標題跟 YouTube 不一樣（例如假資料配錯、影片改過名）就順手更新，歷史紀錄才不會標錯
        page.meta?.let { meta ->
            viewModelScope.launch {
                if (runCatching { data.syncVideoTitle(videoId, meta) }.getOrDefault(false)) reloadRecent()
            }
        }
        // 換了一支影片：上一支的草稿目錄（sheet 與手動圖）再也接不回來 —— 草稿只記最近一支，
        // 這一支進第二步就會蓋掉它。openStep2 已經關掉上一支的狀態機，不會有下載還在寫那裡
        if (previous != null && previous != videoId) {
            viewModelScope.launch { runCatching { data.deleteDraftFiles(previous) } }
        }
        return video
    }

    // ---- 第二步 ----

    private val _step2 = MutableStateFlow<Step2Store?>(null)
    val step2: StateFlow<Step2Store?> = _step2.asStateFlow()

    /** 播放器在 WebView 載完後才有；在那之前長按只會更新藍框，不跳播。 */
    private var player: Player? = null

    fun attachPlayer(p: Player) {
        player = p
        capture = captureFor(p)
    }

    /**
     * WebView 被釋放了就**不可以再握著它**。ViewModel 活得比畫面久（轉螢幕、離開第二步都還在），
     * 留著的話 [playFrame] 會對一個已經 destroy 的 WebView 呼叫 `evaluateJavascript`。
     */
    fun detachPlayer() {
        player = null
        capture = null
        _playbackSec.value = 0.0
    }

    /** 跟著 [_step2] 換掉的收集器。舊的 store close 了，訂閱它的收集器也要跟著取消 —— 否則每次
     *  「回第一步→再進第二步」都會多兩個永遠不結束的 `collect`，一路累積到離開精靈。 */
    private var step2Jobs: List<Job> = emptyList()

    /**
     * 進第二步時建立狀態機。**舊的要先 close** ——
     * 它握著磁碟上的 sheet 與 bitmap 快取，不放掉會一路累積到離開精靈。
     */
    private suspend fun openStep2(video: LoadedVideo) {
        _step2.value?.close()
        step2Jobs.forEach { it.cancel() }
        // 上一支影片的 WebView 已經隨著離開第二步被釋放了，這裡再保險一次 ——
        // 新影片絕不能對著舊播放器下 seek
        player = null
        // 截圖器握著**那一個** WebView 的 eval 入口，跟 player 是同一個生命週期。
        // 留著的話換了影片再按【截圖】，會對已經 destroy 的 WebView 下指令
        capture = null
        val source = frameSourceFactory(video)
        val store = Step2Store(
            source = source,
            // 鎖定格要**同一個層級才算數**：frameIndex 只在某個層級之內有意義
            // （規格第四節把縮圖鍵為 {videoId}/L{level}/{frameIndex}）。先前在 L2 取過圖、
            // 現在解析到 L3 的話，不過濾就會把不相干的格子畫成灰＋鎖，使用者選不了
            taken = data.takenFrameIndexes(video.videoId, source.plan.level),
            scope = viewModelScope,
            compute = Dispatchers.Default,
        )
        _step2.value = store
        step2Jobs = listOf(
            viewModelScope.launch { strength.collect { store.setStrength(it) } },
            viewModelScope.launch { hintSeen.collect { store.setHintSeen(it) } },
        )
    }

    /** 長按或按 ▶：播放器跳到該格的時間點並播放，該格顯示藍框（規格第五節互動表）。 */
    fun playFrame(frameIndex: Int) {
        val store = _step2.value ?: return
        val sec = store.state.value.plan.atSec.getOrNull(frameIndex) ?: return
        store.markPlaying(frameIndex)
        viewModelScope.launch {
            player?.seekTo(sec)
            player?.play()
        }
    }

    fun dismissCaptureError() { _captureError.value = null }

    /**
     * 讀一次播放器目前的秒數：更新進度條，並讓縮圖牆的外框跟著播放位置走。
     * **由畫面定時呼叫**（播放器還在畫面上才輪詢），ViewModel 自己不開無限迴圈。
     */
    suspend fun pollPlayback() {
        val p = player ?: return
        val sec = p.currentTime()
        if (sec < 0) return
        _playbackSec.value = sec
        _step2.value?.followPlayback(sec)
    }

    /**
     * 【截圖】：暫停 → 截圖＋讀秒（同一瞬間）→ 存檔 → 插進牆上 → 續播（規格第五節的流程圖）。
     *
     * **不論成功或失敗都要續播** —— 截不到不是把播放器停在那裡的理由，
     * 所以續播放在 `finally` 裡。
     */
    fun takeShot() {
        val store = _step2.value ?: return
        val shooter = capture ?: run { _captureError.value = CaptureError.NOT_READY; return }
        val videoId = _loaded.value?.videoId ?: return
        viewModelScope.launch {
            player?.pause()
            try {
                when (val result = shooter.capture()) {
                    is CaptureResult.Success -> {
                        val file = runCatching { manualImages(videoId).save(result.webp) }.getOrNull()
                        if (file == null) {
                            _captureError.value = CaptureError.SAVE_FAILED
                        } else {
                            store.addManual(result.atSec, file, fromGallery = false)
                            // 成功了就把上一次的失敗訊息收掉，否則畫面會留著一句過期的話
                            _captureError.value = null
                        }
                    }
                    CaptureResult.BlackFrame -> _captureError.value = CaptureError.BLACK_FRAME
                    CaptureResult.NotDecodable -> _captureError.value = CaptureError.NOT_DECODABLE
                    CaptureResult.AdPlaying -> _captureError.value = CaptureError.AD_PLAYING
                    CaptureResult.NotReady -> _captureError.value = CaptureError.NOT_READY
                }
            } finally {
                player?.play()
            }
        }
    }

    /**
     * 相簿選來的圖：秒數取播放器**當下**的位置。
     *
     * 那是近似值（圖跟播放器沒有關係），所以之後可以用 [nudgeManual] ±1 秒微調 ——
     * 這正是它與【截圖】不同的地方：截圖的秒數與圖是同一瞬間取的，調了就對不上。
     */
    fun addFromGallery(webp: ByteArray) {
        val store = _step2.value ?: return
        val videoId = _loaded.value?.videoId ?: return
        viewModelScope.launch {
            val atSec = (player?.currentTime() ?: 0.0).coerceAtLeast(0.0)
            val file = runCatching { manualImages(videoId).save(webp) }.getOrNull()
            if (file == null) {
                _captureError.value = CaptureError.SAVE_FAILED
            } else {
                store.addManual(atSec, file, fromGallery = true)
                _captureError.value = null
            }
        }
    }

    /**
     * 從 Photo Picker 的 uri 解圖、縮成 320×180 WebP，再交給 [addFromGallery]。
     *
     * **只有這一段碰得到 Android 的影像 API**；秒數與插入牆上的規則留在 [addFromGallery]，
     * 那一段才測得動（`Bitmap` 在純 JVM 測試裡是 not mocked）。
     * 解不出來、編不出來都當成一種失敗型態，不當機。
     */
    fun addFromGalleryUri(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val webp = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { d, _, _ ->
                        // 要能讀 pixel（縮圖用得到），所以不能是 HARDWARE bitmap
                        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    encodeManualWebp(bitmap)
                }.getOrNull()
            }
            if (webp == null) _captureError.value = CaptureError.NOT_DECODABLE else addFromGallery(webp)
        }
    }

    /**
     * ±1 秒微調。**只有相簿選來的圖該用它**（規格第五節、手冊第 93 行）——
     * 截圖的秒數與圖是同一瞬間取的，調了就對不上。這條規則由畫面把關：
     * 只對相簿來的格子顯示微調鈕。
     */
    fun nudgeManual(cell: Int, deltaSec: Double) {
        _step2.value?.nudgeManual(cell, deltaSec)
    }

    fun dismissHint() {
        _step2.value?.setHintSeen(true)
        viewModelScope.launch { onHintSeen() }
    }

    // ---- 第三步 ----

    private val _step3 = MutableStateFlow<Step3Store?>(null)
    val step3: StateFlow<Step3Store?> = _step3.asStateFlow()

    /** 非 null 代表「還有 N 張沒填資料，仍要完成嗎？」正在問。**提醒但不阻擋**（規格第五節）。 */
    private val _pendingFinish = MutableStateFlow<Int?>(null)
    val pendingFinish: StateFlow<Int?> = _pendingFinish.asStateFlow()

    /** 完成了。用 SharedFlow 而不是 StateFlow —— 這是一次性事件，重新訂閱不該再導一次首頁。 */
    private val _finished = MutableSharedFlow<Finished>()
    val finished: SharedFlow<Finished> = _finished.asSharedFlow()

    /**
     * 進第三步：把第二步勾選的格子接過來，並在背景把它們從本機 sheet 裁進 `thumbs/`。
     *
     * **裁圖不擋畫面** —— sheet 已經在本機，進度幾乎一閃而過（規格第五節），
     * 但磁碟慢的時候使用者仍該看得到格子與抽屜。
     */
    private fun openStep3() {
        val step2 = _step2.value ?: return
        val video = _loaded.value ?: return
        val state = step2.state.value
        val cells = state.selected.sortedBy { state.atSecOf(it) }.map {
            Step3Cell(cell = it, atSec = state.atSecOf(it), manual = state.isManual(it))
        }
        val store = Step3Store(
            cells = cells,
            defaultEventDate = eventDateOf(video.page.meta?.publishedAt ?: "", fallback = today()),
        )
        _step3.value = store
        // 上一批裁圖要先收掉 —— 它寫的是共用的 [cropped]，留著的話「回第二步再進來」會有兩批
        // 同時在跑，慢的那批後貼上；換了影片更糟：上一支的結果會被寫成這一支的 thumb_state
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            val storyboardCells = cells.filterNot { it.manual }.map { it.cell }
            val outcome = try {
                data.cropThumbs(video.videoId, video.page.storyboardSpec, storyboardCells) { done, total ->
                    store.setCropProgress(done, total)
                }
            } catch (e: CancellationException) {
                // **取消不是失敗**。用 runCatching 包 suspend 呼叫會把它一起吞掉，
                // 接著就對一個已經被換掉的 store 寫結果 —— 正是上面要避免的那件事
                throw e
            } catch (e: OutOfMemoryError) {
                // 裁圖是解 sheet，OOM 不是 Exception
                CropOutcome(emptyList(), storyboardCells)
            } catch (e: Exception) {
                CropOutcome(emptyList(), storyboardCells)
            }
            cropped = outcome
            store.finishCropping()
        }
    }

    /** 進第三步的裁圖工作。握在手上是為了取消得掉 —— 理由見 [openStep3]。 */
    private var cropJob: Job? = null

    /** 這一批裁圖的結果，完成時要拿來寫 `thumb_state`。 */
    private var cropped: CropOutcome = CropOutcome(emptyList(), emptyList())

    /**
     * 正在寫入圖庫。**擋掉連點兩下的【完成】** —— 第二次會拿同一批格號再寫一次，
     * 撞上 `shot(video_id, frame_index)` 的唯一索引而回滾，於是「存成功了」之後
     * 反而彈出「存不進圖庫」。資料沒壞，但使用者會以為要重做一次。
     */
    private var committing = false

    /**
     * 【完成】。還有沒套用過圖資的就**先問一次**，但問完仍然做得下去（規格第五節）。
     *
     * @param force 使用者在提醒框按了「仍要完成」
     */
    fun finish(force: Boolean = false) {
        if (committing) return
        val store = _step3.value ?: return
        val video = _loaded.value ?: return
        val meta = video.page.meta
        val state = store.state.value
        val unfilled = state.unappliedCells.size
        if (!force && unfilled > 0) {
            _pendingFinish.value = unfilled
            return
        }
        _pendingFinish.value = null

        val level = _step2.value?.state?.value?.plan?.level ?: 3
        committing = true
        viewModelScope.launch {
            try {
                val picks = state.cells.map { cell ->
                    val d = state.details[cell.cell] ?: ShotDetails(eventDate = today())
                    NewShot(
                        atSec = cell.atSec,
                        source = if (cell.manual) "manual" else "storyboard",
                        // 手動圖沒有格號，也不屬於任何 storyboard 層級 —— 兩欄都是 null（規格第四節）
                        frameIndex = if (cell.manual) null else cell.cell,
                        sbLevel = if (cell.manual) null else level,
                        eventDate = d.eventDate,
                        place = d.place,
                        description = d.description,
                        webp = if (cell.manual) manualWebpOf(cell.cell) else null,
                        tagNames = d.tags,
                    )
                }
                // 一張都沒有就沒有「完成」這件事：寫進去是一列沒有圖的影片，
                // 而且下面的 picks.first() 會在協程裡丟 NoSuchElementException 把 process 帶走。
                // committing 由 finally 收掉，使用者回頭挑了圖仍然按得動【完成】
                if (picks.isEmpty()) return@launch
                val videoRow = VideoEntity(
                    id = video.videoId,
                    title = meta?.title ?: video.videoId,
                    channelTitle = meta?.channelTitle ?: "",
                    publishedAt = meta?.publishedAt ?: "",
                    durationSec = meta?.durationSec ?: 0,
                    privacy = meta?.privacy ?: "unknown",
                    sbSpec = video.page.storyboardSpec,
                    addedAt = System.currentTimeMillis() / 1000,
                )
                // 第 1 步：唯一「失敗就是完成失敗」的一步
                val ok = runCatching { data.commit(videoRow, picks) }.isSuccess
                if (!ok) {
                    _commitFailed.value = true
                    return@launch
                }
                // 第 2、3 步：兩個 DB 無法共用交易（規格第五節）。這兩步沒做到也無妨 ——
                // thumb_state 可由「縮圖檔在不在」重新推導，草稿目錄下次取同一支影片會覆蓋
                runCatching { data.markThumbStates(video.videoId, level, cropped.written, cropped.missing) }
                runCatching { data.clearDraft(video.videoId) }
                // 跟上面兩步一樣是另一個 DB（cache.db），沒做到只影響篩選抽屜的順序
                runCatching { markFacetsUsed(state.usedPlaces, state.usedTags) }
                hasDraft = false
                // 第 4 步的「標記有變更」由 repo 的 onChanged 做掉了，這裡不必再呼叫
                _finished.emit(Finished(eventDate = picks.first().eventDate, count = picks.size))
                // 回到第一步。**不做這件事的話使用者會停在一個已經入庫的第三步上**，
                // 再按一次【完成】就是拿同樣的格號再寫一次 —— 撞 shot(video_id, frame_index) 的唯一索引。
                // 階段 7 接上首頁後這裡會換成導頁，但「第三步不能重按」這條不變
                resetToStart()
            } finally {
                committing = false
            }
        }
    }

    /** 完成或放棄之後把精靈清乾淨。第二步的狀態機握著磁碟快取，一定要 close。 */
    private fun resetToStart() {
        _step3.value = null
        _step2.value?.close()
        step2Jobs.forEach { it.cancel() }
        _step2.value = null
        _loaded.value = null
        player = null
        capture = null
        // 還在飛的裁圖不收掉的話，它會在下一支影片進場之後才把結果寫進 [cropped] ——
        // 於是下一支影片的 thumb_state 記的是上一支的格號
        cropJob?.cancel()
        cropJob = null
        cropped = CropOutcome(emptyList(), emptyList())
        _step.value = WizardStep.URL
        // 剛入庫的影片要出現在「最近取過的影片」清單最上面
        reloadRecent()
    }

    private fun reloadRecent() {
        viewModelScope.launch { _recent.value = runCatching { data.recentVideos(RECENT_LIMIT) }.getOrDefault(emptyList()) }
    }

    fun dismissPendingFinish() { _pendingFinish.value = null }

    private val _commitFailed = MutableStateFlow(false)
    val commitFailed: StateFlow<Boolean> = _commitFailed.asStateFlow()

    fun dismissCommitFailed() { _commitFailed.value = false }

    /** 手動圖的 webp。讀不到就當成沒有圖，不擋住整批入庫 —— 其餘 17 張沒有理由陪葬。 */
    private suspend fun manualWebpOf(cell: Int): ByteArray? {
        val file = _step2.value?.state?.value?.manual?.firstOrNull { it.cellIndex == cell }?.file
        return withContext(Dispatchers.IO) { runCatching { file?.readBytes() }.getOrNull() }
    }

    override fun onCleared() {
        _step2.value?.close()
        step2Jobs.forEach { it.cancel() }
        player = null
        super.onCleared()
    }
}

/** 第一步「歷史紀錄」最多列幾支影片。 */
private const val RECENT_LIMIT = 20
