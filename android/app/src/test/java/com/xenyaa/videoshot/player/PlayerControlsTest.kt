package com.xenyaa.videoshot.player

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
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
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PlayerControlsTest {

    @get:Rule val compose = createComposeRule()

    private var toggles = 0
    private val seeks = mutableListOf<Double>()
    private var fullscreenClicks = 0
    private var muteClicks = 0

    private fun show(
        status: PlaybackStatus = PlaybackStatus(60.0, 600.0, paused = false, started = true, 1920, 1080),
        enabled: Boolean = true,
        fullscreen: Boolean = false,
        showTimeline: Boolean = true,
        muted: Boolean? = null,
        playable: Boolean = true,
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
                    showTimeline = showTimeline,
                    muted = muted,
                    onToggleMute = { muteClicks++ },
                    playable = playable,
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

    private fun centerIcons() = compose.onAllNodesWithTag(PLAYER_CENTER_TAG, useUnmergedTree = true)

    /** 中央按鈕與點兩下的閃現是同一個圖示：控制層開著時點兩下不能疊出第二個。 */
    @Test
    fun 控制層開著時點兩下_中央只有一個圖示() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(100)
        centerIcons().assertCountEquals(1)
    }

    @Test
    fun 鍵盤切換播放_中央只有一個圖示() {
        show()
        focusGesture()
        press(Key.Enter)
        compose.mainClock.advanceTimeBy(100)
        centerIcons().assertCountEquals(1)
    }

    /** 閃現跟按鈕一樣顯示「按下去會做什麼」：播放中點兩下暫停 → 閃播放圖示，收起後不留。 */
    @Test
    fun 控制層收著時點兩下_閃一下按鈕同款圖示後消失() {
        show()
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { doubleClick(center) }
        // 先讓手勢處理完（autoAdvance 關著時不會自己跑），閃現在下一幀畫出來
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        centerIcons().assertCountEquals(1)
        compose.onNodeWithContentDescription("播放", useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        centerIcons().assertCountEquals(0)
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

    // ---- 實體鍵盤（驗收手冊 §零） ----

    private fun focusGesture() {
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).requestFocus()
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performKeyInput { pressKey(key) }
    }

    @Test
    fun 手勢區拿得到焦點() {
        show()
        focusGesture()
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).assertIsFocused()
    }

    @Test
    fun 鍵盤_Enter_切換播放() {
        show()
        focusGesture()
        press(Key.Enter)
        assertEquals(1, toggles)
    }

    @Test
    fun 鍵盤_數字鍵盤_Enter_與空白鍵也切換播放() {
        show()
        focusGesture()
        press(Key.NumPadEnter)
        press(Key.Spacebar)
        assertEquals(2, toggles)
    }

    @Test
    fun 鍵盤切換播放時叫出控制層() {
        show()
        focusGesture()
        press(Key.Enter)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("全螢幕").assertIsDisplayed()
    }

    @Test
    fun 鍵盤_右左方向鍵前後十秒() {
        show()   // 目前 60 秒、總長 600 秒
        focusGesture()
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        assertEquals(listOf(70.0, 50.0), seeks)
    }

    @Test
    fun 鍵盤跳播夾在零到總長之間() {
        show(status = PlaybackStatus(3.0, 600.0, paused = false, started = true, 1920, 1080))
        focusGesture()
        press(Key.DirectionLeft)
        assertEquals(listOf(0.0), seeks)
    }

    @Test
    fun 鍵盤_F_切換全螢幕() {
        show()
        focusGesture()
        press(Key.F)
        assertEquals(1, fullscreenClicks)
    }

    @Test
    fun 有焦點時控制層不自己收起() {
        show()
        focusGesture()
        press(Key.DirectionRight)   // 叫出控制層
        compose.mainClock.advanceTimeBy(CONTROLS_AUTO_HIDE_MS * 2)
        compose.onNodeWithContentDescription("暫停").assertIsDisplayed()
    }

    @Test
    fun 還沒準備好時鍵盤不作用() {
        show(enabled = false)
        focusGesture()
        press(Key.Enter)
        press(Key.DirectionRight)
        press(Key.F)
        assertEquals(0, toggles)
        assertEquals(0, fullscreenClicks)
        assertEquals(emptyList<Double>(), seeks)
    }

    @Test
    fun 進度條語意帶步數且目前值夾在範圍內() {
        show(status = PlaybackStatus(900.0, 600.0, paused = false, started = true, 1920, 1080))
        val info = compose.onNodeWithContentDescription("播放進度").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(600f, info.current, 0f)
        assertEquals(59, info.steps)
    }

    // ---- 時間軸、聲音鈕、還不能播（Lightbox 片段與全螢幕的縮圖狀態用） ----

    private fun tapAside() = compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput {
        click(androidx.compose.ui.geometry.Offset(width * 0.2f, height * 0.3f))
    }

    @Test
    fun 不顯示時間軸時_沒有時間與進度條() {
        show(showTimeline = false)
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("暫停").assertIsDisplayed()
        compose.onNodeWithContentDescription("全螢幕").assertIsDisplayed()
        compose.onNodeWithText("01:00", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("播放進度").assertDoesNotExist()
    }

    @Test
    fun 不顯示時間軸時_拖曳與方向鍵都不跳播() {
        show(showTimeline = false)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(viewConfiguration.touchSlop + 1f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(width / 4f, 0f))
        }
        compose.onNodeWithTag(PREVIEW_BUBBLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { up() }
        focusGesture()
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        assertEquals(emptyList<Double>(), seeks)
    }

    @Test
    fun 沒給靜音狀態就沒有聲音鈕() {
        show()
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("關掉聲音").assertDoesNotExist()
        compose.onNodeWithContentDescription("打開聲音").assertDoesNotExist()
    }

    @Test
    fun 聲音鈕在全螢幕鈕旁邊_點了切換() {
        show(muted = false)
        tapAside(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("關掉聲音").assertIsDisplayed().performClick()
        assertEquals(1, muteClicks)
        val sound = compose.onNodeWithContentDescription("關掉聲音").fetchSemanticsNode().boundsInRoot
        val full = compose.onNodeWithContentDescription("全螢幕").fetchSemanticsNode().boundsInRoot
        assert(sound.right <= full.left) { "sound=$sound full=$full" }
    }

    @Test
    fun 靜音中聲音鈕叫打開聲音() {
        show(muted = true)
        tapAside(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("打開聲音").assertIsDisplayed()
    }

    @Test
    fun 還不能播時_叫得出按鈕但沒有中央鈕_點兩下不切換() {
        show(playable = false)
        tapCenter(); compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("全螢幕").assertIsDisplayed()
        compose.onAllNodesWithTag(PLAYER_CENTER_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag(PLAYER_GESTURE_TAG).performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(0, toggles)
        compose.onAllNodesWithTag(PLAYER_CENTER_TAG, useUnmergedTree = true).assertCountEquals(0)
    }
}
