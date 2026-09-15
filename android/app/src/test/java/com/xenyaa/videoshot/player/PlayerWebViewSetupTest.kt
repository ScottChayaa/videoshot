package com.xenyaa.videoshot.player

import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 播放器 WebView 的初始設定。
 *
 * 這組測試存在的原因是一個實機上很難看出來的 bug（2026-09-15）：影片有聲音、
 * `readyState` 是 4、`currentTime` 也在前進，但畫面上播放器整片空白。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerWebViewSetupTest {

    private fun webView() = WebView(ApplicationProvider.getApplicationContext())

    @Test
    fun 版面參數是填滿而不是包住內容() {
        // `AndroidView` 預設給子 View 的是 WRAP_CONTENT。WebView 拿到不確定的高度時，
        // Chromium 會用「內容多高就多高」排版，於是 YouTube 那條 html → body → #player
        // 的 height:100% 全部算成 0 —— 影片在播，但高度是 0，畫面上什麼都看不到。
        val view = webView().apply { applyPlayerViewSettings() }
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.width)
    }

    @Test
    fun 要開_javascript_與_dom_storage() {
        val view = webView().apply { applyPlayerViewSettings() }
        assertTrue(view.settings.javaScriptEnabled)
        assertTrue(view.settings.domStorageEnabled)
    }

    @Test
    fun 播放不需要使用者手勢() {
        // 關掉的話連 cued 覆蓋層點下去都不會播
        val view = webView().apply { applyPlayerViewSettings() }
        assertTrue(view.settings.mediaPlaybackRequiresUserGesture.not())
    }
}
