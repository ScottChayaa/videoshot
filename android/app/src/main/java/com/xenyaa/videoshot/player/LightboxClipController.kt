package com.xenyaa.videoshot.player

import com.xenyaa.videoshot.core.player.CLIP_DELAY_MS
import com.xenyaa.videoshot.core.player.ClipRange
import com.xenyaa.videoshot.core.player.clipRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 預載與等畫面出來時讀 [ClipPlayer.status] 的間隔。 */
const val CLIP_POLL_MS: Long = 100L

/** 秒數超過起點這麼多才算「畫面真的在動」，才把縮圖淡掉。 */
private const val ADVANCE_EPS_SEC = 0.05

/** 預載時秒數落在起點前面超過這麼多，當成 YouTube 自己跳回片頭，再跳一次。 */
private const val CUE_DRIFT_SEC = 1.0

/** 預載後 `readyState` 一直是 0（封面沒點到）這麼久，就再 cue 一次。 */
private const val CUE_RETRY_MS = 1_500L

/** 預載這麼久都沒準備好，當成頁面壞了（錯誤頁、載入失敗）：重載頁面，繼續等。 */
private const val PREPARE_RELOAD_MS = 15_000L

/**
 * Lightbox 自動播放片段的流程（規格第六節「自動播放片段」）。不碰 Compose，畫面只讀 [showing]／[userPaused]、
 * 把事件（停在某一張、開始拖、點一下、被蓋住）丟進來。
 *
 * 一次只有一個工作（[job]）：停在某一張就開一個，開始拖或換一張就取消。工作裡「倒數」與「預載」同時跑，
 * 兩個都完成才開始循環，等秒數真的在前進才把 [showing] 設成那張——畫面在那之前一直是縮圖。
 *
 * @param scope 畫面的 scope；Lightbox 離開組合時一起取消
 * @param source 抓 watch page 換成 [ClipSource]；拿不到回 null。**成功的結果記在這個物件裡**（這次 Lightbox），失敗不記
 */
class LightboxClipController(
    private val scope: CoroutineScope,
    private val source: suspend (videoId: String) -> ClipSource?,
    private val delayMs: Long = CLIP_DELAY_MS,
) {
    private val player = MutableStateFlow<ClipPlayer?>(null)
    private val covered = MutableStateFlow(false)
    private val sources = mutableMapOf<String, ClipSource>()

    /** 頁面上目前載的是哪支影片；load 完成才寫，中途被取消就保持 null，下次一定重載。 */
    private var loadedVideoId: String? = null
    private var job: Job? = null

    /** 已經下過 startLoop：之後被蓋住、點一下才需要對播放器下暫停／繼續。 */
    private var loopStarted = false

    private val _showing = MutableStateFlow<Long?>(null)

    /** 正在顯示影片的那一張的 shotId；null＝顯示縮圖。 */
    val showing: StateFlow<Long?> = _showing

    private val _userPaused = MutableStateFlow(false)
    val userPaused: StateFlow<Boolean> = _userPaused

    /** WebView 建好給播放器；被釋放時給 null——之後不能再對它下任何指令。 */
    fun attach(p: ClipPlayer?) {
        if (p == null) reset()
        loadedVideoId = null
        player.value = p
    }

    /** 停在某一張（含剛開啟、滑回同一張、目前這張被刪掉換成下一張）：重新倒數。 */
    fun onSettled(target: ClipTarget) {
        // 不經拖曳換成別張（刪掉目前這張、篩選中改完被拿掉）時，上一段可能還在播：先停掉。
        // 不停的話下一張若是別支影片又抓不到，舊片段會在縮圖後面一直出聲（最終審查 I-1）
        val wasActive = job != null || loopStarted
        reset()
        player.value?.let { p -> if (wasActive) scope.launch { p.pause() } }
        job = scope.launch { run(target) }
    }

    /** 手指開始拖：倒數作廢、影片立刻暫停、換回縮圖。 */
    fun onDragStart() {
        reset()
        val p = player.value ?: return
        scope.launch { p.pause() }
    }

    /** 點一下影片：暫停／繼續。還是縮圖時沒反應。 */
    fun onTap() {
        if (_showing.value == null) return
        _userPaused.value = !_userPaused.value
        applyPlayback()
    }

    /** ⋯ 選單、確認框、抽屜、切到背景。 */
    fun setCovered(value: Boolean) {
        if (covered.value == value) return
        covered.value = value
        if (loopStarted) applyPlayback()
    }

    private fun reset() {
        job?.cancel()
        job = null
        loopStarted = false
        _showing.value = null
        _userPaused.value = false
    }

    private fun applyPlayback() {
        val p = player.value ?: return
        val play = !_userPaused.value && !covered.value
        scope.launch { if (play) p.resume() else p.pause() }
    }

    private suspend fun run(target: ClipTarget) = coroutineScope {
        val prepared = async { prepare(target) }
        delay(delayMs)
        val range = prepared.await() ?: return@coroutineScope
        covered.first { !it }
        val p = player.value ?: return@coroutineScope
        loopStarted = true
        p.startLoop(range.startSec, range.endSec)
        while (true) {
            val s = p.status()
            if (!s.paused && s.currentSec > range.startSec + ADVANCE_EPS_SEC) break
            delay(CLIP_POLL_MS)
        }
        _showing.value = target.shotId
    }

    /**
     * 抓來源 → 換頁面（不同影片才換）→ 靜音預載到起點 → 停住。拿不到來源回 null。
     * 預載太久沒好就重載頁面再等（規格：還沒載好就繼續等，好了才換）——頁面載入失敗不能被記住。
     */
    private suspend fun prepare(target: ClipTarget): ClipRange? {
        val src = sources[target.videoId]
            ?: fetch(target.videoId)?.also { sources[target.videoId] = it }
            ?: return null
        val p = player.filterNotNull().first()
        val range = clipRange(target.atSec, src.durationSec.toDouble())
        while (true) {
            if (loadedVideoId != target.videoId) {
                loadedVideoId = null
                p.load(target.videoId, src.playableInEmbed)
                loadedVideoId = target.videoId
            }
            if (cueUntilReady(p, range.startSec)) break
            loadedVideoId = null
        }
        p.hold(range.startSec)
        return range
    }

    /** 預載到緩衝好回 true；[PREPARE_RELOAD_MS] 內都沒好回 false。 */
    private suspend fun cueUntilReady(p: ClipPlayer, startSec: Double): Boolean {
        p.cue(startSec)
        var waited = 0L
        var sinceCue = 0L
        while (waited < PREPARE_RELOAD_MS) {
            val s = p.status()
            if (s.readyState >= 3 && s.currentSec >= startSec) return true
            val drifted = s.readyState >= 1 && s.currentSec < startSec - CUE_DRIFT_SEC
            // 頁面剛載完封面還沒出現時點不到，cue 只能回 'play'／'none'，readyState 會一直是 0
            val stuck = s.readyState == 0 && sinceCue >= CUE_RETRY_MS
            if (drifted || stuck) {
                p.cue(startSec)
                sinceCue = 0
            }
            delay(CLIP_POLL_MS)
            waited += CLIP_POLL_MS
            sinceCue += CLIP_POLL_MS
        }
        return false
    }

    private suspend fun fetch(videoId: String): ClipSource? = try {
        source(videoId)
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        null
    }
}
