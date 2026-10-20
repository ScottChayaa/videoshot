package com.xenyaa.videoshot.player

import org.junit.Assert.assertTrue
import org.junit.Test

/** 取圖第二步與原片播放的【聲音】：靜音要撐得住 YouTube 自己把 `muted` 改回去（同 Lightbox 片段）。 */
class PlayerMuteJsTest {

    @Test fun 靜音同時改強制旗標與影片() {
        val js = playerMuteJs(true)
        assertTrue(js.contains("window.__vsPlayerMute = true"))
        assertTrue(js.contains("v.muted = true"))
    }

    @Test fun 打開聲音解除強制旗標() {
        val js = playerMuteJs(false)
        assertTrue(js.contains("window.__vsPlayerMute = false"))
        assertTrue(js.contains("v.muted = false"))
    }

    @Test fun YouTube取消靜音會被當場蓋回去() {
        val js = playerMuteJs(true)
        // 媒體事件不冒泡，掛在 document 的 capture 階段才收得到；<video> 晚出現或被換掉也接得住
        assertTrue(js.contains("'volumechange'"))
        assertTrue(js.contains("'playing'"))
        assertTrue(js.contains("addEventListener(n, window.__vsPlayerMuteHook, true)"))
        assertTrue(js.contains("if (!window.__vsPlayerMuteHook)"))
    }
}
