package com.xenyaa.videoshot.player

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayerFullscreenTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 方向對照() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientationFor(active = false, landscape = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, orientationFor(active = true, landscape = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientationFor(active = true, landscape = false))
    }

    @Test
    fun 狀態進出() {
        val s = PlayerFullscreenState()
        assertFalse(s.active)
        s.enter(landscape = false)
        assertTrue(s.active); assertFalse(s.landscape)
        s.exit()
        assertFalse(s.active)
    }

    @Test
    fun 橫的影片全螢幕時_Activity_轉橫向_離開轉回直立() {
        val s = PlayerFullscreenState()
        compose.setContent { FullscreenSystemEffect(s) }
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
        s.enter(landscape = true)
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
        s.exit()
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
    }

    /** 直的影片全螢幕留著系統導覽列：圖示換白的（黑底），離開時還原成原本的深色。 */
    @Test
    fun 直的影片全螢幕時導覽列圖示換白_離開還原() {
        val s = PlayerFullscreenState()
        compose.setContent { FullscreenSystemEffect(s) }
        compose.waitForIdle()
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        compose.runOnIdle { controller.isAppearanceLightNavigationBars = true }
        s.enter(landscape = false)
        compose.waitForIdle()
        assertFalse(controller.isAppearanceLightNavigationBars)
        s.exit()
        compose.waitForIdle()
        assertTrue(controller.isAppearanceLightNavigationBars)
    }
}
