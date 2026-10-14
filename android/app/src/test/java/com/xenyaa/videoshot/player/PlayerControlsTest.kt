package com.xenyaa.videoshot.player

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.Palettes
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayerControlsTest {

    @get:Rule val compose = createComposeRule()

    private var toggles = 0
    private val seeks = mutableListOf<Double>()
    private var fullscreenClicks = 0

    private fun show(
        status: PlaybackStatus = PlaybackStatus(60.0, 600.0, paused = false, started = true, 1920, 1080),
        enabled: Boolean = true,
        fullscreen: Boolean = false,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VideoshotTheme(spec = Palettes.byId(null)) {
                PlayerControls(
                    status = status,
                    fallbackDurationSec = 600.0,
                    enabled = enabled,
                    fullscreen = fullscreen,
                    preview = { null as ImageBitmap? },
                    onTogglePlay = { toggles++ },
                    onSeek = { seeks += it },
                    onToggleFullscreen = { fullscreenClicks++ },
                    modifier = Modifier.size(400.dp, 225.dp),
                )
            }
        }
    }

    private fun tapCenter() = compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { click(center) }

    @Test
    fun 點一下叫出控制層() {
        show()
        compose.onNodeWithContentDescription("暫停").assertDoesNotExist()
        tapCenter()
        compose.mainClock.advanceTimeBy(400)   // 等過點兩下的判定時間
        compose.onNodeWithContentDescription("暫停").assertIsDisplayed()
        compose.onNodeWithContentDescription("全螢幕").assertIsDisplayed()
        compose.onNodeWithText("01:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("10:00", substring = true).assertIsDisplayed()
    }

    @Test
    fun 播放中三秒沒動作自己收起() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.mainClock.advanceTimeBy(CONTROLS_AUTO_HIDE_MS + 500)
        compose.onNodeWithContentDescription("暫停").assertDoesNotExist()
    }

    @Test
    fun 暫停中不自己收起() {
        show(status = PlaybackStatus(60.0, 600.0, paused = true, started = true, 1920, 1080))
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.mainClock.advanceTimeBy(CONTROLS_AUTO_HIDE_MS * 3)
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun 控制層開著時再點一下就收起() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        // 控制層開著時正中央是播放／暫停按鈕，第二下改點偏離中央的位置
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 0.2f, height * 0.3f))
        }
        compose.mainClock.advanceTimeBy(400 + 500)   // 點兩下判定時間，再等淡出動畫播完
        compose.onNodeWithContentDescription("暫停").assertDoesNotExist()
    }

    @Test
    fun 點兩下切換播放暫停且不叫出控制層() {
        show()
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(1, toggles)
        compose.onNodeWithContentDescription("全螢幕").assertDoesNotExist()
    }

    @Test
    fun 中央按鈕切換播放() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        // 中央按鈕不攔觸控，單擊由手勢框判斷位置；要等過點兩下的判定時間
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        assertEquals(1, toggles)
    }

    @Test
    fun 控制層開著時在中央點兩下只切換一次() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(1, toggles)
    }

    @Test
    fun 讀屏用的中央按鈕語意動作切換播放() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("暫停").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, toggles)
    }

    @Test
    fun 拖曳時顯示預覽時間_放手才跳播() {
        show()
        // 手指按住不放時，暫停的時鐘不會把拖曳造成的狀態變化套進畫面，這個測試改讓時鐘自己走
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(viewConfiguration.touchSlop + 1f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(width / 4f, 0f))
        }
        compose.onNodeWithTag(PREVIEW_BUBBLE_TAG).assertIsDisplayed()
        assertEquals(emptyList<Double>(), seeks)
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { up() }
        assertEquals(1, seeks.size)
        // 超過觸控門檻的部分才算：約 1/4 寬 ≈ 150 秒，從 60 秒起算 ≈ 212 秒
        val target = seeks.single()
        assert(target in 205.0..225.0) { "target=$target" }
    }

    @Test
    fun 全螢幕鈕() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("全螢幕").performClick()
        assertEquals(1, fullscreenClicks)
    }

    @Test
    fun 全螢幕時按鈕叫離開全螢幕() {
        show(fullscreen = true)
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("離開全螢幕").assertIsDisplayed()
    }

    @Test
    fun 還沒準備好時手勢不作用() {
        show(enabled = false)
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("全螢幕").assertDoesNotExist()
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(0, toggles)
    }

    @Test
    fun 總長未知時不能拖曳() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VideoshotTheme(spec = Palettes.byId(null)) {
                PlayerControls(
                    status = PlaybackStatus(0.0, 0.0, paused = true, started = false, 0, 0),
                    fallbackDurationSec = 0.0, enabled = true, fullscreen = false,
                    preview = { null }, onTogglePlay = {}, onSeek = { seeks += it }, onToggleFullscreen = {},
                    modifier = Modifier.size(400.dp, 225.dp),
                )
            }
        }
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(emptyList<Double>(), seeks)
    }
}
