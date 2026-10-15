package com.xenyaa.videoshot.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebViewClipPlayerTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun 狀態字串解析() =
        assertEquals(ClipStatus(30.5, 4, paused = false, videoWidth = 1280, videoHeight = 720), parseClipStatus("\"30.5,4,0,1280,720\""))

    @Test fun 狀態帶影片寬高() =
        assertTrue(CLIP_STATUS_JS.contains("v.videoWidth, v.videoHeight"))

    @Test fun 沒有影片元素是未知() =
        assertEquals(ClipStatus.Unknown, parseClipStatus("\"none\""))

    @Test fun 格式不對是未知() {
        assertEquals(ClipStatus.Unknown, parseClipStatus(""))
        assertEquals(ClipStatus.Unknown, parseClipStatus("\"NaN,4,0\""))
        assertEquals(ClipStatus.Unknown, parseClipStatus("\"1,2\""))
    }

    @Test fun 預載會停掉上一段循環_靜音_跳到起點_點封面() {
        val js = clipCueJs(30.0)
        assertTrue(js.contains("clearInterval(window.__vsLoop)"))
        assertTrue(js.contains("v.muted = true"))
        assertTrue(js.contains("v.currentTime = 30.0"))
        assertTrue(js.contains("CuedOverlayPlayButton"))
        // 控制列的播放／暫停鍵，正在播時點下去是暫停（WebViewPlayer.kt 的 PLAY_VIDEO_JS 說明）
        assertTrue(!js.contains(".ytp-play-button"))
    }

    @Test fun 預載期間YouTube取消靜音會被立刻蓋回去() {
        // 2026-10-15 實機：剛載好的頁面點封面開播後，YouTube 會套用自己的音量狀態把 muted 改回 false，
        // 快速滑過的中間那幾張會出聲。用 capture 階段的事件（媒體事件不冒泡，但 capture 收得到）當場蓋回去
        val js = clipCueJs(30.0)
        assertTrue(js.contains("window.__vsMute = true"))
        assertTrue(js.contains("addEventListener(n, window.__vsMuteHook, true)"))
        assertTrue(js.contains("'volumechange'"))
        assertTrue(js.contains("'playing'"))
    }

    @Test fun 開始循環前先解除強制靜音() {
        val js = clipLoopJs(30.0, 33.0, muted = false)
        assertTrue(js.indexOf("window.__vsMute = false") in 0 until js.indexOf("v.muted = false"))
    }

    @Test fun 使用者關掉聲音時循環維持強制靜音() {
        // 強制靜音（__vsMute）一直開著：YouTube 自己把 muted 改回 false 時會被蓋回去
        val js = clipLoopJs(30.0, 33.0, muted = true)
        assertTrue(js.contains("window.__vsMute = true"))
        assertTrue(js.contains("v.muted = true"))
        assertTrue(!js.contains("v.muted = false"))
    }

    @Test fun 切換聲音同時改強制靜音與影片() {
        assertTrue(clipMuteJs(true).contains("window.__vsMute = true"))
        assertTrue(clipMuteJs(true).contains("v.muted = true"))
        assertTrue(clipMuteJs(false).contains("window.__vsMute = false"))
        assertTrue(clipMuteJs(false).contains("v.muted = false"))
    }

    @Test fun 停住會暫停並跳回起點() {
        val js = clipHoldJs(30.0)
        assertTrue(js.contains("v.pause()"))
        assertTrue(js.contains("v.currentTime = 30.0"))
    }

    @Test fun 循環在頁面裡每五十毫秒檢查_取消靜音() {
        val js = clipLoopJs(30.0, 33.0, muted = false)
        assertTrue(js.contains("setInterval"))
        assertTrue(js.contains(", 50)"))
        assertTrue(js.contains("v.currentTime >= 33.0"))
        assertTrue(js.contains("v.muted = false"))
        // 播到片尾 <video> 會停在 ended，跳回之後要再 play
        assertTrue(js.contains("v.ended"))
    }

    @Test fun 頁面背景透明() =
        assertTrue(CLIP_TRANSPARENT_JS.contains("background:transparent"))

    @Test
    fun 舊網址的載入完成不算這次的() = kotlinx.coroutines.test.runTest {
        // 上一支影片的 onPageFinished 晚到，不能讓這次的 load 提早回來（最終審查 I-2(a)）
        val p = WebViewClipPlayer(android.webkit.WebView(androidx.test.core.app.ApplicationProvider.getApplicationContext()))
        val job = launch { p.load("v2", playableInEmbed = true) }
        runCurrent()
        p.onPageFinished("https://www.youtube.com/embed/v1?playsinline=1")
        p.onPageFinished("about:blank")
        runCurrent()
        assertTrue(job.isActive)
        p.onPageFinished("https://www.youtube.com/embed/v2?playsinline=1")
        runCurrent()
        assertTrue(job.isCompleted)
    }

    @Test
    fun Surface建好會給播放器_離開畫面會收回() {
        var got: ClipPlayer? = null
        var calls = 0
        var show by mutableStateOf(true)
        compose.setContent {
            if (show) ClipPlayerSurface(alpha = 0f, onPlayer = { got = it; calls++ }, modifier = Modifier.size(200.dp))
        }
        compose.waitForIdle()
        assertNotNull(got)
        compose.runOnUiThread { show = false }
        compose.waitForIdle()
        assertNull(got)
        assertEquals(2, calls)
    }
}
