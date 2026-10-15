package com.xenyaa.videoshot.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
        assertEquals(ClipStatus(30.5, 4, paused = false), parseClipStatus("\"30.5,4,0\""))

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
        val js = clipLoopJs(30.0, 33.0)
        assertTrue(js.indexOf("window.__vsMute = false") in 0 until js.indexOf("v.muted = false"))
    }

    @Test fun 停住會暫停並跳回起點() {
        val js = clipHoldJs(30.0)
        assertTrue(js.contains("v.pause()"))
        assertTrue(js.contains("v.currentTime = 30.0"))
    }

    @Test fun 循環在頁面裡每五十毫秒檢查_取消靜音() {
        val js = clipLoopJs(30.0, 33.0)
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
