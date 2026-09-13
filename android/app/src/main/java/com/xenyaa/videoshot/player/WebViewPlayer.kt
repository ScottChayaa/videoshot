package com.xenyaa.videoshot.player

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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
        eval("(function(){var v=document.querySelector('video'); if(v) v.pause(); return 'ok';})()")
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

/** 把播放器放進 Compose。`onPlayerReady` 在頁面載完時回呼，呼叫端拿到的是 [Player] 介面。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerSurface(
    videoId: String,
    playableInEmbed: Boolean,
    onPlayerReady: (Player) -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = remember(videoId, playableInEmbed) { playerUrl(videoId, playableInEmbed) }

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
                // embed 需要 referer，否則部分影片會拒絕播放
                loadUrl(url, mapOf("Referer" to "https://${context.packageName}"))
            }
        },
    )
}
