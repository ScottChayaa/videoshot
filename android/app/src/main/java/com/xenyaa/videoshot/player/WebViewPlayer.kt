package com.xenyaa.videoshot.player

import android.annotation.SuppressLint
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
 * **embed 不會自動播放，而且 `v.play()` 無效** —— POC 實測必須模擬點擊播放鍵
 * （規格第二節第 5 點）。所以 [play] 先試 `v.play()`，再點一次播放鍵。
 */
class WebViewPlayer(private val webView: WebView) : Player {

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady

    internal fun markReady() { _isReady.value = true }

    override suspend fun play() {
        eval(
            """
            (function(){
              var v = document.querySelector('video');
              if (v) { v.play(); }
              var btn = document.querySelector('.ytp-large-play-button, .ytp-play-button');
              if (btn) { btn.click(); }
              return 'ok';
            })()
            """.trimIndent()
        )
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
 * 讓 `<video>` 停下來。`WebView.onPause()` 對 HTML5 播放**不保證停得住**
 * （文件只承諾盡力停掉動畫之類的處理），所以背景化時除了 `onPause()` 還要補這一刀。
 */
internal const val PAUSE_VIDEO_JS =
    "(function(){var v=document.querySelector('video'); if(v) v.pause(); return 'ok';})()"

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
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
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
