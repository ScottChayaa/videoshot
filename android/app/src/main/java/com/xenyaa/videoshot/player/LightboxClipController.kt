package com.xenyaa.videoshot.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 預載與等畫面出來時讀 [ClipPlayer.status] 的間隔。 */
const val CLIP_POLL_MS: Long = 100L

/** 預載時秒數落在起點前面超過這麼多，當成 YouTube 自己跳回片頭，再跳一次。 */
private const val CUE_DRIFT_SEC = 1.0

/** 預載後 `readyState` 一直是 0（封面沒點到）這麼久，就再 cue 一次。 */
private const val CUE_RETRY_MS = 1_500L

/** 預載這麼久都沒準備好，當成頁面壞了（錯誤頁、載入失敗）：重載頁面，繼續等。 */
private const val PREPARE_RELOAD_MS = 15_000L

/**
 * Lightbox 大圖換成影片畫面的流程（規格第六節「影片定格」）。不碰 Compose，畫面只讀 [showing]、
 * 把事件（停在某一張、開始拖）丟進來。
 *
 * 一次只有一個工作（[job]）：停在某一張就開一個，開始拖或換一張就取消。工作靜音預載到截圖那一秒、
 * 停住，等畫面出來才把 [showing] 設成那張——畫面在那之前一直是縮圖。之後影片一直停在那一格，不播放、不出聲。
 *
 * @param scope 畫面的 scope；Lightbox 離開組合時一起取消
 * @param source 抓 watch page 換成 [ClipSource]；拿不到回 null。**成功的結果記在這個物件裡**（這次 Lightbox），失敗不記
 */
class LightboxClipController(
    private val scope: CoroutineScope,
    private val source: suspend (videoId: String) -> ClipSource?,
) {
    private val player = MutableStateFlow<ClipPlayer?>(null)
    private val sources = mutableMapOf<String, ClipSource>()

    /** 頁面上目前載的是哪支影片；load 完成才寫，中途被取消就保持 null，下次一定重載。 */
    private var loadedVideoId: String? = null
    private var job: Job? = null

    private val _showing = MutableStateFlow<Long?>(null)

    /** 正在顯示影片畫面的那一張的 shotId；null＝顯示縮圖。 */
    val showing: StateFlow<Long?> = _showing

    /** WebView 建好給播放器；被釋放時給 null——之後不能再對它下任何指令。 */
    fun attach(p: ClipPlayer?) {
        if (p == null) reset()
        loadedVideoId = null
        player.value = p
    }

    /** 停在某一張（含剛開啟、滑回同一張、目前這張被刪掉換成下一張）：重新預載。 */
    fun onSettled(target: ClipTarget) {
        // 上一張可能還在靜音預載（播著讓它緩衝）：先停掉，下一張抓不到來源時才不會在背後一直播
        val wasActive = job != null
        reset()
        player.value?.let { p -> if (wasActive) scope.launch { p.pause() } }
        job = scope.launch { run(target) }
    }

    /** 手指開始拖：預載作廢、換回縮圖；預載中的影片（靜音在播、讓它緩衝）停掉。 */
    fun onDragStart() {
        reset()
        val p = player.value ?: return
        scope.launch { p.pause() }
    }

    private fun reset() {
        job?.cancel()
        job = null
        _showing.value = null
    }

    private suspend fun run(target: ClipTarget) {
        val startSec = prepare(target) ?: return
        val p = player.value ?: return
        // hold 跳回起點要重新解碼那一格：有畫面（readyState ≥ 2）才露出來
        while (p.status().readyState < 2) delay(CLIP_POLL_MS)
        _showing.value = target.shotId
    }

    /**
     * 抓來源 → 換頁面（不同影片才換）→ 靜音預載到截圖那一秒 → 停住，回傳停在哪一秒。拿不到來源回 null。
     * 預載太久沒好就重載頁面再等（規格：還沒載好就繼續等，好了才換）——頁面載入失敗不能被記住。
     */
    private suspend fun prepare(target: ClipTarget): Double? {
        val src = sources[target.videoId]
            ?: fetch(target.videoId)?.also { sources[target.videoId] = it }
            ?: return null
        val p = player.filterNotNull().first()
        // 截圖秒數比 watch page 的整數總長還大時，停在片尾
        val startSec = target.atSec.coerceAtLeast(0.0)
            .let { if (src.durationSec > 0) it.coerceAtMost(src.durationSec.toDouble()) else it }
        while (true) {
            if (loadedVideoId != target.videoId) {
                loadedVideoId = null
                p.load(target.videoId, src.playableInEmbed)
                loadedVideoId = target.videoId
            }
            if (cueUntilReady(p, startSec)) break
            loadedVideoId = null
        }
        p.hold(startSec)
        return startSec
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
