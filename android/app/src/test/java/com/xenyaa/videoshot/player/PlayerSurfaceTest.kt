package com.xenyaa.videoshot.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayerSurfaceTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 全螢幕時返回鍵只離開全螢幕() {
        val fs = PlayerFullscreenState().apply { enter(landscape = true) }
        var outerBacks = 0
        compose.setContent {
            CompositionLocalProvider(LocalPlayerFullscreen provides fs) {
                androidx.activity.compose.BackHandler { outerBacks++ }
                PlayerSurface("aqz-KE-bpKQ", true, 600, { null }, {})
            }
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(fs.active)
        assertEquals(0, outerBacks)
    }

    @Test
    fun 播放器離開畫面時一併離開全螢幕() {
        val fs = PlayerFullscreenState().apply { enter(landscape = true) }
        var show by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalPlayerFullscreen provides fs) {
                if (show) PlayerSurface("aqz-KE-bpKQ", true, 600, { null }, {})
            }
        }
        compose.runOnUiThread { show = false }
        compose.waitForIdle()
        assertFalse(fs.active)
    }

    @Config(sdk = [35], qualifiers = "w800dp-h400dp-land")
    @Test
    fun 橫向視窗時播放器限高百分之四十() {
        compose.setContent {
            Box(Modifier.requiredSize(800.dp, 400.dp)) {
                Box(Modifier.playerFrame(fullscreen = false).testTag("frame"))
            }
        }
        val bounds = compose.onNodeWithTag("frame").getUnclippedBoundsInRoot()
        // 視窗高度含系統列，Robolectric 下約等於 400dp；容許 2dp 誤差
        assert((bounds.bottom - bounds.top).value <= 162f) { "height=${bounds.bottom - bounds.top}" }
        // 限高只動高度，框仍然寬滿（影片在框內置中、兩側黑邊）
        assert((bounds.right - bounds.left).value >= 799f) { "width=${bounds.right - bounds.left}" }
    }
}
