package com.xenyaa.videoshot.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xenyaa.videoshot.capture.CAPTURE_JS
import com.xenyaa.videoshot.capture.capturedJpegOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Lightbox 影片定格的 WebView 實作（規格第六節「影片定格」）。網址、藏介面、點封面開播都沿用
 * [WebViewPlayer] 那一套（規格第二節第 5 點）；多的是靜音預載、停在那一格。
 */
class WebViewClipPlayer(private val webView: WebView) : ClipPlayer {

    private var pageLoaded = CompletableDeferred<Unit>()

    /** 這次 [load] 等的是哪支影片的頁面。 */
    private var loadingVideoId: String? = null

    /**
     * `onPageFinished` 呼叫。只認這次要載的那支影片：上一支的回呼晚到（快速換頁時 v1 還沒載完就換 v2）、
     * 或 `about:blank`，都不能讓這次的 [load] 提早回來——否則 `cue` 會打在舊頁面上，新頁面的封面沒人點。
     */
    internal fun onPageFinished(url: String?) {
        val id = loadingVideoId ?: return
        if (url != null && url.contains(id)) pageLoaded.complete(Unit)
    }

    override suspend fun load(videoId: String, playableInEmbed: Boolean) {
        val done = CompletableDeferred<Unit>()
        pageLoaded = done
        loadingVideoId = videoId
        val url = playerUrl(videoId, playableInEmbed)
        // embed 需要 referer，否則部分影片會拒絕播放（同 PlayerSurface）
        webView.post { webView.loadUrl(url, mapOf("Referer" to "https://${webView.context.packageName}")) }
        done.await()
    }

    override suspend fun cue(startSec: Double) { eval(clipCueJs(startSec)) }
    override suspend fun hold(startSec: Double) { eval(clipHoldJs(startSec)) }
    override suspend fun pause() { eval(PAUSE_VIDEO_JS) }
    override suspend fun status(): ClipStatus = parseClipStatus(eval(CLIP_STATUS_JS))
    override suspend fun frame(): ByteArray? {
        // 先切到這支影片的最高畫質再截；網路慢、等不到就截當下的畫質
        if (eval(MAX_QUALITY_JS).contains("switching")) {
            withTimeoutOrNull(MAX_QUALITY_WAIT_MS) {
                while (!eval(MAX_QUALITY_READY_JS).contains("1")) delay(100)
            }
        }
        return capturedJpegOf(eval(CAPTURE_JS))
    }

    private suspend fun eval(js: String): String = suspendCancellableCoroutine { cont ->
        webView.post { webView.evaluateJavascript(js) { if (cont.isActive) cont.resume(it ?: "") } }
    }
}

/** 封面播放鍵的選擇器，同 `PLAY_VIDEO_JS`（行動版 embed 與桌機版兩種 DOM；**不要加 `.ytp-play-button`**）。 */
private const val COVER_SELECTOR =
    "'.ytmCuedOverlayPlayButton, [class*=\"CuedOverlayPlayButton\"], .ytp-large-play-button'"

/**
 * 預載：靜音、跳到起點、開始播讓它緩衝。
 * 還沒開播（`readyState=0`）時 `v.play()` 沒有用，要點封面（規格第二節第 5 點）；
 * 那時設定的 `currentTime` 會成為起播位置。
 *
 * **只設一次 `muted` 不夠**（2026-10-15 實機）：剛載好的頁面點封面開播後，YouTube 會套用自己的音量狀態
 * 把 `muted` 改回 false，快速滑過的中間那幾張會出聲約 0.7 秒。所以預載期間（`__vsMute`）掛 capture 階段的
 * 事件——媒體事件不冒泡，但 document 的 capture 收得到，`<video>` 還沒出現或被換掉都接得住——
 * 一被取消靜音就當場蓋回去。`volume = 0` 不採用：YouTube 同樣會套用自己的音量。
 */
internal fun clipCueJs(startSec: Double): String =
    """
    (function(){
      window.__vsMute = true;
      if (!window.__vsMuteHook) {
        window.__vsMuteHook = function(e){
          var t = e.target;
          if (window.__vsMute && t && t.tagName === 'VIDEO' && !t.muted) t.muted = true;
        };
        ['volumechange', 'play', 'playing', 'loadeddata'].forEach(function(n){
          document.addEventListener(n, window.__vsMuteHook, true);
        });
      }
      var v = document.querySelector('video');
      if (v) { v.muted = true; v.currentTime = $startSec; }
      if (!v || v.readyState === 0) {
        var o = document.querySelector($COVER_SELECTOR);
        if (o) { o.click(); return 'overlay'; }
      }
      if (v) { v.play(); return 'play'; }
      return 'none';
    })()
    """.trimIndent()

/** 緩衝好了：停住、跳回起點，仍然靜音。 */
internal fun clipHoldJs(startSec: Double): String =
    "(function(){var v=document.querySelector('video'); if(!v) return 'none'; v.pause(); v.currentTime = $startSec; return 'ok';})()"

/** 【截圖】等最高畫質最多等多久（2026-11-16 實機：1080p 半秒內就換好）。 */
private const val MAX_QUALITY_WAIT_MS = 3_000L

/** YouTube 播放器物件（有 `getAvailableQualityLevels` 等方法的那個元素）。 */
private const val YT_PLAYER = "(document.getElementById('movie_player')||document.querySelector('.html5-video-player'))"

/**
 * 【截圖】前把畫質固定在這支影片的最高一級（2026-11-16 scott）：YouTube 自動挑的畫質看播放器大小，
 * Lightbox 的直片只給到 720p，來源明明有 1080p。固定後跳回同一個時間點，讓那一格重新以高畫質解出來。
 * 回 `ready`（已經是最高）、`switching`（換畫質中，要等 [MAX_QUALITY_READY_JS]）或 `none`（拿不到播放器）。
 */
internal const val MAX_QUALITY_JS = """
(function(){
  var p = $YT_PLAYER, v = document.querySelector('video');
  if (!p || !v || !p.getAvailableQualityLevels || !p.setPlaybackQualityRange) return 'none';
  var q = p.getAvailableQualityLevels().filter(function(l){ return l !== 'auto'; })[0];
  if (!q) return 'none';
  if (p.getPlaybackQuality() === q) return 'ready';
  window.__vsMaxQ = {q: q, side: Math.min(v.videoWidth, v.videoHeight)};
  var t = v.currentTime;
  p.setPlaybackQualityRange(q, q);
  v.currentTime = t;
  return 'switching';
})()
"""

/** 換好了沒：畫質是目標、那一格解好了，而且畫面尺寸真的變了（只看畫質標籤會截到還沒換的舊畫面）。好了回 `1`。 */
internal const val MAX_QUALITY_READY_JS = """
(function(){
  var p = $YT_PLAYER, v = document.querySelector('video'), s = window.__vsMaxQ;
  if (!p || !v || !s) return '1';
  var ok = p.getPlaybackQuality() === s.q && v.readyState >= 2 && !v.seeking
    && Math.min(v.videoWidth, v.videoHeight) !== s.side;
  return ok ? '1' : '0';
})()
"""

/** currentTime, readyState，逗號隔開（同 `STATUS_JS` 的理由）。 */
internal const val CLIP_STATUS_JS =
    "(function(){var v=document.querySelector('video'); if(!v) return 'none'; " +
        "return [v.currentTime, v.readyState].join(',');})()"

/**
 * 頁面背景透明，影片外的留白才會露出 Lightbox 的底色。`BARE_PLAYER_JS` 把 html／body 設成 #000!important，
 * 這條同樣 !important、晚注入，所以蓋得過。
 */
internal val CLIP_TRANSPARENT_JS =
    """
    (function(){
      if (document.getElementById('vs-clip-bg')) return 'exists';
      var s = document.createElement('style');
      s.id = 'vs-clip-bg';
      s.textContent = 'html,body{background:transparent!important}';
      (document.head || document.documentElement).appendChild(s);
      return 'ok';
    })()
    """.trimIndent()

internal fun parseClipStatus(raw: String): ClipStatus {
    val parts = raw.trim().removeSurrounding("\"").split(',')
    if (parts.size != 2) return ClipStatus.Unknown
    val current = parts[0].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return ClipStatus.Unknown
    val ready = parts[1].toDoubleOrNull()?.toInt() ?: return ClipStatus.Unknown
    return ClipStatus(current, ready)
}

/**
 * Lightbox 的影片定格層（一個 Lightbox 只有一個）。WebView 不收觸控、不收焦點——上下滑與點擊都由
 * 疊在上面的 pager 處理（規格第六節「影片定格」）。
 *
 * @param alpha 0＝藏起來（預載中），1＝露出來；直接設在 View 上，淡入淡出由呼叫端算
 * @param onPlayer WebView 建好時給播放器、釋放時給 null
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ClipPlayerSurface(
    alpha: Float,
    onPlayer: (ClipPlayer?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPlayer = rememberUpdatedState(onPlayer)
    val holder = remember { arrayOfNulls<WebView>(1) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // 切到背景時預載中的影片停掉
                Lifecycle.Event.ON_PAUSE -> holder[0]?.let { it.evaluateJavascript(PAUSE_VIDEO_JS, null); it.onPause() }
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                applyPlayerViewSettings()
                setBackgroundColor(Color.TRANSPARENT)
                webChromeClient = WebChromeClient()
                val clip = WebViewClipPlayer(this)
                webViewClient = object : WebViewClient() {
                    // 頁面一畫出來就藏，不等 onPageFinished，減少 YouTube 按鈕閃一下（同 PlayerSurface）
                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        view?.evaluateJavascript(BARE_PLAYER_JS, null)
                        view?.evaluateJavascript(CLIP_TRANSPARENT_JS, null)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(BARE_PLAYER_JS, null)
                        view?.evaluateJavascript(CLIP_TRANSPARENT_JS, null)
                        clip.onPageFinished(url)
                    }
                }
                // 觸控整個不給 WebView：藏起來的 YouTube 按鈕仍然點得到
                setOnTouchListener { _, _ -> true }
                isFocusable = false
                isFocusableInTouchMode = false
                // 大圖那一頁已經有名稱，影片層對 TalkBack 不存在
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                holder[0] = this
                currentOnPlayer.value(clip)
            }
        },
        update = { it.alpha = alpha },
        onRelease = { webView ->
            holder[0] = null
            currentOnPlayer.value(null)
            // 先卸掉頁面再 destroy：about:blank 會把 <video> 一起帶走，聲音當場停（同 PlayerSurface）
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.destroy()
        },
    )
}
