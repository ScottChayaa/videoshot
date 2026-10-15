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
        reset()
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

    /** 抓來源 → 換頁面（不同影片才換）→ 靜音預載到起點 → 停住。拿不到來源回 null。 */
    private suspend fun prepare(target: ClipTarget): ClipRange? {
        val src = sources[target.videoId]
            ?: fetch(target.videoId)?.also { sources[target.videoId] = it }
            ?: return null
        val p = player.filterNotNull().first()
        if (loadedVideoId != target.videoId) {
            loadedVideoId = null
            p.load(target.videoId, src.playableInEmbed)
            loadedVideoId = target.videoId
        }
        val range = clipRange(target.atSec, src.durationSec.toDouble())
        p.cue(range.startSec)
        while (true) {
            val s = p.status()
            if (s.readyState >= 3 && s.currentSec >= range.startSec) break
            if (s.readyState >= 1 && s.currentSec < range.startSec - CUE_DRIFT_SEC) p.cue(range.startSec)
            delay(CLIP_POLL_MS)
        }
        p.hold(range.startSec)
        return range
    }

    private suspend fun fetch(videoId: String): ClipSource? = try {
        source(videoId)
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        null
    }
}
