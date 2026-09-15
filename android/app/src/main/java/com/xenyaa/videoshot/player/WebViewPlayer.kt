package com.xenyaa.videoshot.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 播放器要載哪一個網址（規格第二節第 5 點，POC 實測的結論）。
 *
 * - `playableInEmbed=true` → embed：畫面全乾淨、實測多為 720p
 * - `playableInEmbed=false` → `m.youtube.com`：embed 會顯示「影片擁有者已禁止…」，
 *   行動版頁面則播得出來（只有 360p，且頁面帶 YouTube 頂列）
 *
 * **事先判定，不靠「載入失敗再退回」** —— 那會讓使用者先看到一次錯誤畫面。
 */
fun playerUrl(videoId: String, playableInEmbed: Boolean): String =
    if (playableInEmbed) {
        "https://www.youtube.com/embed/$videoId?playsinline=1&controls=0&cc_load_policy=0&rel=0"
    } else {
        "https://m.youtube.com/watch?v=$videoId"
    }

/**
 * 用 WebView 驅動 YouTube 的 `<video>` 元素。
 *
 * **embed 不會自動播放，而且 `v.play()` 無效** —— 播放器停在 `unstarted`、`<video>` 還沒有
 * 媒體來源（規格第二節第 5 點）。[play] 因此先點封面覆蓋層的播放鍵，詳見 [PLAY_VIDEO_JS]。
 */
class WebViewPlayer(private val webView: WebView) : Player {

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady

    internal fun markReady() { _isReady.value = true }

    override suspend fun play() {
        eval(PLAY_VIDEO_JS)
    }

    override suspend fun pause() {
        eval(PAUSE_VIDEO_JS)
    }

    override suspend fun seekTo(sec: Double) {
        eval("(function(){var v=document.querySelector('video'); if(v) v.currentTime=$sec; return 'ok';})()")
    }

    override suspend fun currentTime(): Double {
        val raw = eval("(function(){var v=document.querySelector('video'); return v ? v.currentTime : -1;})()")
        return raw.trim().removeSurrounding("\"").toDoubleOrNull() ?: -1.0
    }

    /** evaluateJavascript 是回呼式的，包成 suspend 才能在流程裡照順序寫。 */
    private suspend fun eval(js: String): String = suspendCancellableCoroutine { cont ->
        webView.post { webView.evaluateJavascript(js) { cont.resume(it ?: "") } }
    }
}

/**
 * 啟動播放。
 *
 * **只呼叫 `v.play()` 是沒有用的。** 2026-09-15 實機實測：embed 載完時 YouTube 的播放器
 * 還停在 `unstarted`，`<video>` 雖然已經存在，但 `readyState=0`、`networkState=0`、
 * `src` 是空字串 —— 根本還沒有媒體來源。對這樣的 `<video>` 呼叫 `play()` 只會把 `paused`
 * 翻成 false，不報錯、畫面也不動（這就是「長按格子播放器沒反應」的真正原因）。
 * 必須點掉畫面上那層封面（cued overlay）的播放鍵，播放器才會去接媒體 ——
 * 點完之後 readyState 立刻變 4、`src` 拿到 blob URL、currentTime 開始前進。
 *
 * 選擇器涵蓋行動版與桌機版兩種 DOM：**app 的 WebView 拿到的 embed 是行動版**
 * （`ytmCuedOverlayPlayButton`），而 `.ytp-large-play-button` 是桌機版的類名、在這裡從來不存在 ——
 * 原本只寫桌機版的那一版等於一直在點空氣。`[class*=...]` 是類名被 YouTube 改掉時的緩衝。
 *
 * **不要把 `.ytp-play-button` 加回選擇器**：那是控制列上的播放／暫停鍵，
 * 正在播的時候點下去是暫停。
 *
 * 覆蓋層點掉之後就不再出現，所以之後每一次跳播都走 `v.play()` 那條路。
 *
 * 跳播的順序不必改：在 `readyState=0` 時設定 `currentTime` 會被當成
 * 「預設起播位置」保留下來，媒體接上後就從那裡開始（實測跳到 100 秒後確實從 100.06 秒起播）。
 */
internal val PLAY_VIDEO_JS =
    """
    (function(){
      var overlay = document.querySelector(
        '.ytmCuedOverlayPlayButton, [class*="CuedOverlayPlayButton"], .ytp-large-play-button');
      if (overlay) { overlay.click(); return 'overlay'; }
      var v = document.querySelector('video');
      if (v) { v.play(); return 'video'; }
      return 'none';
    })()
    """.trimIndent()

/**
 * 讓 `<video>` 停下來。`WebView.onPause()` 對 HTML5 播放**不保證停得住**
 * （文件只承諾盡力停掉動畫之類的處理），所以背景化時除了 `onPause()` 還要補這一刀。
 */
internal const val PAUSE_VIDEO_JS =
    "(function(){var v=document.querySelector('video'); if(v) v.pause(); return 'ok';})()"

/**
 * 播放器 WebView 的初始設定。
 *
 * **`layoutParams` 一定要自己給 `MATCH_PARENT`。** `AndroidView` 預設塞給子 View 的是
 * `WRAP_CONTENT`；WebView 的高度一旦是「包住內容」，Chromium 就用不確定的高度排版，
 * 於是 YouTube embed 那條 `html` → `body` → `#player` → `<video>` 的 `height:100%`
 * **整條算成 0**。症狀非常難認：影片確實在播（有聲音、`readyState=4`、`currentTime` 前進），
 * Android 那一側的 `webView.height` 也是正確的 608px，但 `<video>` 的版面高度是 0，
 * 畫面上播放器整片空白（2026-09-15 實機實測）。
 *
 * 補 CSS 沒有用 —— 實測連 `html{height:100%!important}` 都還是算出 0px，
 * 因為問題出在 WebView 的排版高度不確定，不是頁面的樣式被誰蓋掉。重載也沒有用。
 *
 * `mediaPlaybackRequiresUserGesture = false` 不能拿掉：關掉的話連 cued 覆蓋層
 * 點下去都不會播（見 [PLAY_VIDEO_JS]）。
 */
internal fun WebView.applyPlayerViewSettings() {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
}

/**
 * 把播放器放進 Compose。`onPlayerReady` 在頁面載完時回呼，呼叫端拿到的是 [Player] 介面。
 *
 * **WebView 一定要釋放**：`AndroidView` 不會自己 destroy 它。沒有 `onRelease` 的話，
 * 預覽播到一半離開第二步（返回鍵、【✕】）之後，那個已經脫離畫面的 WebView 會繼續播 YouTube 的聲音，
 * 而且每重進一次第二步就多一個。切到背景不會離開組合，所以另外掛生命週期觀察者處理。
 *
 * @param onPlayerReleased WebView 沒了要講一聲 —— 呼叫端（`WizardViewModel`）握著的 [Player]
 *        指向的就是這個 WebView，不放掉的話轉螢幕之後還會對一個已經 destroy 的 WebView 下指令
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerSurface(
    videoId: String,
    playableInEmbed: Boolean,
    onPlayerReady: (Player) -> Unit,
    modifier: Modifier = Modifier,
    onPlayerReleased: () -> Unit = {},
) {
    val url = remember(videoId, playableInEmbed) { playerUrl(videoId, playableInEmbed) }

    // 生命週期觀察者要摸得到 WebView，而 WebView 是 factory 造的 —— 用一格陣列接住它。
    // 不用 mutableStateOf：這不是畫面狀態，寫進去只會多一次重組
    val holder = remember { arrayOfNulls<WebView>(1) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // 切到背景時聲音要停 —— 這條路徑不會離開組合，onRelease 接不到
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
                webChromeClient = WebChromeClient()
                val player = WebViewPlayer(this)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        player.markReady()
                        onPlayerReady(player)
                    }
                }
                holder[0] = this
                // embed 需要 referer，否則部分影片會拒絕播放
                loadUrl(url, mapOf("Referer" to "https://${context.packageName}"))
            }
        },
        onRelease = { webView ->
            holder[0] = null
            onPlayerReleased()
            // 先卸掉頁面再 destroy：about:blank 會把 <video> 一起帶走，聲音當場停
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.destroy()
        },
    )
}
