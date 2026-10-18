package com.xenyaa.videoshot.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xenyaa.videoshot.core.player.isLandscapeVideo
import com.xenyaa.videoshot.core.player.playerMaxHeightDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    override suspend fun togglePlay() {
        eval(TOGGLE_PLAY_JS)
    }

    override suspend fun status(): PlaybackStatus = parseStatus(eval(STATUS_JS))

    /** 【聲音】（取圖第二步與詳情頁，規格第六節「播放器」）。頁面載好時與切換時各下一次。 */
    suspend fun setMuted(muted: Boolean) {
        eval(playerMuteJs(muted))
    }

    /**
     * 給 `capture` 用的執行入口。
     *
     * 交出去的是「在這個播放器上跑一段 JS」這個能力，**不是 WebView 本身** ——
     * 截圖實作要藏在 `capture` 介面後面（規格第三節邊界 4），
     * 把 WebView 交出去等於讓別的模組可以對播放器做任何事。
     */
    internal suspend fun evaluate(js: String): String = eval(js)

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
 * 點播放器畫面：播放中就暫停，停著就播。還沒開播（`<video>` 還沒有媒體來源）時跟
 * [PLAY_VIDEO_JS] 一樣點封面覆蓋層（被 [BARE_PLAYER_JS] 藏起來了，但 `click()` 照樣有效）。
 *
 * **不能用「覆蓋層還在不在」判斷有沒有開播**：2026-10-12 實機查到開播之後覆蓋層的按鈕
 * 仍在 DOM 裡（`display:block`、72×72），那樣寫的話第二次點畫面又去點覆蓋層、永遠停不下來。
 */
internal val TOGGLE_PLAY_JS =
    """
    (function(){
      var v = document.querySelector('video');
      if (!v || v.readyState === 0) {
        var overlay = document.querySelector(
          '.ytmCuedOverlayPlayButton, [class*="CuedOverlayPlayButton"], .ytp-large-play-button');
        if (overlay) { overlay.click(); return 'overlay'; }
        if (v) { v.play(); return 'play'; }
        return 'none';
      }
      if (v.paused) { v.play(); return 'play'; }
      v.pause(); return 'pause';
    })()
    """.trimIndent()

/**
 * 把 YouTube 自己的介面全部藏掉，只留影片畫面（取圖第二步，2026-10-12 scott 要求：
 * 跳播只靠下方的候選格子，點畫面只管播放／暫停）。
 *
 * 用 `visibility` 而不是 `display:none`：子元素可以把 `visibility` 改回來，所以
 * 「全部藏起來、只放 `<video>` 出來」一條規則就涵蓋標題、頻道頭像、分享、
 * 「觀看平台：YouTube」、暫停時的推薦影片，YouTube 改類名也不怕。
 * embed 開播前 `<video>` 沒有畫面，所以封面縮圖（`.ytmVideoCoverThumbnail`）也放出來。
 * `<video>` 固定鋪滿視窗是給 `m.youtube.com`（不能嵌入的影片）用的：那個頁面的影片在頂列下面，
 * 不鋪滿的話會偏一截；embed 本來就鋪滿，套上去沒有差別（2026-10-12 兩種頁面都在實機上試過）。
 * 有 id，重複注入只會留一份。
 */
internal val BARE_PLAYER_JS =
    """
    (function(){
      if (document.getElementById('vs-bare')) return 'exists';
      var s = document.createElement('style');
      s.id = 'vs-bare';
      s.textContent =
        'html,body{background:#000!important;overflow:hidden!important}' +
        'body *{visibility:hidden!important}' +
        '.ytmVideoCoverThumbnail{visibility:visible!important}' +
        'video{visibility:visible!important;position:fixed!important;top:0!important;left:0!important;' +
        'width:100vw!important;height:100vh!important;object-fit:contain!important;z-index:2147483647!important}';
      (document.head || document.documentElement).appendChild(s);
      return 'ok';
    })()
    """.trimIndent()

/**
 * 切換【聲音】。**只設一次 `muted` 不夠**：點封面開播後 YouTube 會套用自己的音量狀態把它改回 false
 * （2026-10-15 在 Lightbox 片段實測過，見 `WebViewClipPlayer.kt` 的 `clipCueJs`）。所以靜音期間在 document 的
 * capture 階段掛媒體事件（媒體事件不冒泡，capture 收得到；`<video>` 晚出現或被換掉也接得住），一被取消靜音就蓋回去。
 * 旗標名跟片段的 `__vsMute` 分開，兩種頁面不會互相影響。
 */
internal fun playerMuteJs(muted: Boolean): String =
    """
    (function(){
      window.__vsPlayerMute = $muted;
      if (!window.__vsPlayerMuteHook) {
        window.__vsPlayerMuteHook = function(e){
          var t = e.target;
          if (window.__vsPlayerMute && t && t.tagName === 'VIDEO' && !t.muted) t.muted = true;
        };
        ['volumechange', 'play', 'playing', 'loadeddata'].forEach(function(n){
          document.addEventListener(n, window.__vsPlayerMuteHook, true);
        });
      }
      var v = document.querySelector('video');
      if (v) v.muted = $muted;
      return 'ok';
    })()
    """.trimIndent()

/**
 * 一次讀完控制層要的六個值，逗號隔開（不用 JSON.stringify：回來還要再剝一層引號跟跳脫，逗號字串最好解）。
 * 順序：currentTime, duration, paused, readyState>0, videoWidth, videoHeight。
 */
internal val STATUS_JS =
    """
    (function(){
      var v = document.querySelector('video');
      if (!v) return 'none';
      return [v.currentTime, v.duration, v.paused ? 1 : 0, v.readyState > 0 ? 1 : 0, v.videoWidth, v.videoHeight].join(',');
    })()
    """.trimIndent()

/** [STATUS_JS] 的回傳值。格式不對一律當未知，不丟例外——控制層讀不到只是不顯示，不該當機。 */
internal fun parseStatus(raw: String): PlaybackStatus {
    val parts = raw.trim().removeSurrounding("\"").split(',')
    if (parts.size != 6) return PlaybackStatus.Unknown
    val nums = parts.map { it.toDoubleOrNull() }
    val current = nums[0]?.takeIf { it.isFinite() } ?: return PlaybackStatus.Unknown
    val duration = nums[1]?.takeIf { !it.isNaN() && !it.isInfinite() } ?: 0.0
    return PlaybackStatus(
        currentSec = current,
        durationSec = duration,
        paused = nums[2] != 0.0,
        started = nums[3] == 1.0,
        videoWidth = nums[4]?.toInt() ?: 0,
        videoHeight = nums[5]?.toInt() ?: 0,
    )
}

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
 * YouTube 介面一律藏掉（[BARE_PLAYER_JS]），WebView 不收觸控，觸控由 [PlayerControls] 處理
 * （點一下叫出控制層、點兩下播放／暫停、水平拖曳跳播）。
 * @param durationSec 影片總長，WebView 讀不到時控制層拿它當進度條的總長
 * @param preview 拖曳跳播時要顯示的預覽圖（依目標秒數）；沒有就不顯示泡泡
 * @param onPlayerReleased WebView 沒了要講一聲 —— 呼叫端（`WizardViewModel`）握著的 [Player]
 *        指向的就是這個 WebView，不放掉的話轉螢幕之後還會對一個已經 destroy 的 WebView 下指令
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun PlayerSurface(
    videoId: String,
    playableInEmbed: Boolean,
    durationSec: Int,
    preview: suspend (Double) -> ImageBitmap?,
    onPlayerReady: (Player) -> Unit,
    modifier: Modifier = Modifier,
    onPlayerReleased: () -> Unit = {},
) {
    val url = remember(videoId, playableInEmbed) { playerUrl(videoId, playableInEmbed) }

    // 生命週期觀察者要摸得到 WebView，而 WebView 是 factory 造的 —— 用一格陣列接住它。
    // 不用 mutableStateOf：這不是畫面狀態，寫進去只會多一次重組
    val holder = remember { arrayOfNulls<WebView>(1) }

    val fullscreen = LocalPlayerFullscreen.current
    val sound = LocalPlayerSound.current
    // onPageFinished 之後才有；控制層靠它讀狀態與下指令
    var player by remember { mutableStateOf<WebViewPlayer?>(null) }
    var status by remember { mutableStateOf(PlaybackStatus.Unknown) }
    val scope = rememberCoroutineScope()

    // 播放器在畫面上才輪詢（同第二步既有的 0.25 秒）
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            status = p.status()
            delay(STATUS_POLL_MS)
        }
    }

    // 頁面載好時套一次設定值，之後每次切換再套
    LaunchedEffect(player, sound.muted) { player?.setMuted(sound.muted) }

    // 全螢幕時系統返回只負責離開全螢幕。這個 BackHandler 比畫面自己的晚註冊，所以優先
    BackHandler(enabled = fullscreen.active) { fullscreen.exit() }
    // 播放器離開畫面（返回、換頁、被系統收掉）一律回直立，不讓別的畫面卡在橫向
    DisposableEffect(Unit) { onDispose { fullscreen.exit() } }

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

    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                WebView(context).apply {
                    applyPlayerViewSettings()
                    // 透明，透出外框的黑底。WebView 預設白底，頁面畫出來之前那一幀會整片白
                    // （實機看到的範圍還不只播放器框，連頂欄都蓋過去），進第二步就白一下
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webChromeClient = WebChromeClient()
                    val webPlayer = WebViewPlayer(this)
                    webViewClient = object : WebViewClient() {
                        // 頁面一畫出來就藏，不等 onPageFinished，減少 YouTube 按鈕閃一下
                        override fun onPageCommitVisible(view: WebView?, loadedUrl: String?) {
                            view?.evaluateJavascript(BARE_PLAYER_JS, null)
                        }

                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            view?.evaluateJavascript(BARE_PLAYER_JS, null)
                            webPlayer.markReady()
                            player = webPlayer
                            onPlayerReady(webPlayer)
                        }
                    }
                    // 藏起來的按鈕仍然點得到，所以觸控整個不給 WebView，改由蓋在上面的控制層處理
                    setOnTouchListener { _, _ -> true }
                    // 實體鍵盤的 Tab 不能停在看不到焦點框的 WebView 上，焦點只給上面的控制層
                    isFocusable = false
                    isFocusableInTouchMode = false
                    holder[0] = this
                    // embed 需要 referer，否則部分影片會拒絕播放
                    loadUrl(url, mapOf("Referer" to "https://${context.packageName}"))
                }
            },
            onRelease = { webView ->
                holder[0] = null
                player = null
                onPlayerReleased()
                // 先卸掉頁面再 destroy：about:blank 會把 <video> 一起帶走，聲音當場停
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.destroy()
            },
        )
        PlayerControls(
            status = status,
            fallbackDurationSec = durationSec.toDouble(),
            enabled = player != null,
            fullscreen = fullscreen.active,
            preview = preview,
            onTogglePlay = { scope.launch { player?.togglePlay() } },
            onSeek = { sec -> scope.launch { player?.seekTo(sec) } },
            onToggleFullscreen = {
                if (fullscreen.active) fullscreen.exit()
                else fullscreen.enter(isLandscapeVideo(status.videoWidth, status.videoHeight))
            },
            modifier = Modifier.matchParentSize(),
            muted = sound.muted,
            onToggleMute = { sound.setMuted(!sound.muted) },
        )
    }
}

/** 控制層讀播放狀態的間隔，同第二步縮圖牆外框的輪詢。 */
private const val STATUS_POLL_MS = 250L

/**
 * 播放器的外框（規格第六節）：非全螢幕時寬滿、16:9，大螢幕橫向（Android 16 以上鎖不住直立）時
 * 高度最多 40%，影片在框裡置中、兩側黑邊；全螢幕時撐滿。
 * **呼叫端只能換這個 Modifier，不能把播放器搬到別的位置**——搬了 WebView 就會重建。
 */
@Composable
fun Modifier.playerFrame(fullscreen: Boolean): Modifier {
    if (fullscreen) return this.fillMaxSize().background(Color.Black)
    val config = LocalConfiguration.current
    val maxH = playerMaxHeightDp(config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat())
    val base = this.fillMaxWidth().background(Color.Black)
    if (maxH == null) return base.aspectRatio(16f / 9f)
    // 不用 aspectRatio(matchHeightConstraintsFirst)：高度被夾住之後它會連寬度一起縮（實測 800×400dp 的視窗
    // 框只剩 284dp 寬）。這裡自己量：寬滿、高 ＝ min(寬 × 9/16, 上限)
    return base.layout { measurable, constraints ->
        val w = constraints.maxWidth
        val h = minOf((w * 9f / 16f).toInt(), maxH.dp.roundToPx())
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }
}
