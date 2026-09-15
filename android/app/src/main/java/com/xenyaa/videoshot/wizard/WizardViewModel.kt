package com.xenyaa.videoshot.wizard

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.url.parseVideoId
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.capture.Capture
import com.xenyaa.videoshot.capture.CaptureResult
import com.xenyaa.videoshot.capture.ManualImageStore
import com.xenyaa.videoshot.capture.encodeManualWebp
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.wizard.frames.FrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    private val _captureError = MutableStateFlow<CaptureError?>(null)
    val captureError: StateFlow<CaptureError?> = _captureError.asStateFlow()

    private var capture: Capture? = null

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

    // ---- 第一步 ----

    private val _status = MutableStateFlow<Step1Status>(Step1Status.Idle)
    val status: StateFlow<Step1Status> = _status.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentVideo>>(emptyList())
    val recent: StateFlow<List<RecentVideo>> = _recent.asStateFlow()

    /** 抓好的 watch page 與已收藏格號，交給第二步用。 */
    private val _loaded = MutableStateFlow<LoadedVideo?>(null)
    val loaded: StateFlow<LoadedVideo?> = _loaded.asStateFlow()

    init {
        viewModelScope.launch { _recent.value = data.recentVideos(20) }
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
        _status.value = Step1Status.Loading
        viewModelScope.launch {
            val page = data.watchPage(videoId)
            when (page.result) {
                FetchResult.FETCH_FAILED ->
                    _status.value = Step1Status.Error("取圖需要網路。")

                FetchResult.VIDEO_UNAVAILABLE ->
                    _status.value = Step1Status.Error(
                        "這支影片抓不到，可能是私人影片、已被刪除或需要登入。"
                    )

                // no_storyboard 與 parse_failed **仍然進第二步** —— 縮圖牆空白，但可以截圖補上
                // （規格第五節第一步的表、第七節降級表）
                FetchResult.OK, FetchResult.NO_STORYBOARD, FetchResult.PARSE_FAILED -> {
                    val video = LoadedVideo(videoId = videoId, page = page)
                    _loaded.value = video
                    // 建狀態機要先問一次 DB（已收藏的格號），所以**轉圈圈留到這之後才收掉**
                    openStep2(video)
                    _status.value = Step1Status.Idle
                    goTo(WizardStep.PICK)
                }
            }
        }
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

    override fun onCleared() {
        _step2.value?.close()
        step2Jobs.forEach { it.cancel() }
        player = null
        super.onCleared()
    }
}
