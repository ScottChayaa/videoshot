package com.xenyaa.videoshot.ui.clip

import android.app.Activity
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 假播放器：記下收到的指令，狀態由測試直接設。 */
private class FakeResultPlayback(
    duration: Long = 48_000,
    position: Long = 0,
    playing: Boolean = true,
) : ClipResultPlayback {
    override val isPlaying = MutableStateFlow(playing)
    override val positionMs = MutableStateFlow(position)
    override val durationMs = MutableStateFlow(duration)
    val seeks = mutableListOf<Long>()
    var released = false

    override fun play() { isPlaying.value = true }
    override fun pause() { isPlaying.value = false }
    override fun seekTo(positionMs: Long) { seeks += positionMs; this.positionMs.value = positionMs }
    override fun release() { released = true }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ClipResultScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(playback: ClipResultPlayback, onBack: () -> Unit = {}) {
        compose.setContent {
            VideoshotTheme { ClipResultContent(playback, onBack = onBack, surface = {}) }
        }
    }

    @Test
    fun 有播放暫停鈕與進度條() {
        show(FakeResultPlayback(playing = true))

        // 播放中：按鈕是「暫停」
        compose.onNodeWithContentDescription("暫停").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放進度").assertIsDisplayed()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.onNodeWithText("0:48").assertIsDisplayed()
    }

    @Test
    fun 暫停中顯示播放鈕_點了就播放() {
        val playback = FakeResultPlayback(playing = false)
        show(playback)

        compose.onNodeWithContentDescription("播放").performClick()
        assertTrue(playback.isPlaying.value)
        compose.onNodeWithContentDescription("暫停").assertIsDisplayed()
    }

    @Test
    fun 播放中點暫停() {
        val playback = FakeResultPlayback(playing = true)
        show(playback)

        compose.onNodeWithContentDescription("暫停").performClick()
        assertFalse(playback.isPlaying.value)
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
    }

    @Test
    fun 拖進度條跳到對應時間() {
        val playback = FakeResultPlayback(duration = 48_000)
        show(playback)

        // 進度條範圍是 0..1：停在一半＝24 秒
        compose.onNodeWithContentDescription("播放進度")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        assertEquals(24_000L, playback.seeks.last())
    }

    @Test
    fun 播放位置跟著走() {
        val playback = FakeResultPlayback(duration = 48_000, position = 12_000)
        show(playback)
        compose.onNodeWithText("0:12").assertIsDisplayed()

        playback.positionMs.value = 30_000
        compose.waitForIdle()
        compose.onNodeWithText("0:30").assertIsDisplayed()
    }

    @Test
    fun 點返回鈕回上一頁() {
        var backs = 0
        show(FakeResultPlayback(), onBack = { backs++ })

        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun 系統返回鍵回上一頁() {
        var backs = 0
        val owner = object : OnBackPressedDispatcherOwner {
            private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
        }
        compose.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                VideoshotTheme { ClipResultContent(FakeResultPlayback(), onBack = { backs++ }, surface = {}) }
            }
        }
        compose.runOnUiThread { owner.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }

    @Test
    fun 離開畫面時釋放播放器() {
        val playback = FakeResultPlayback()
        var visible by mutableStateOf(true)
        compose.setContent {
            VideoshotTheme { if (visible) ClipResultContent(playback, onBack = {}, surface = {}) }
        }
        assertFalse(playback.released)

        visible = false
        compose.waitForIdle()
        assertTrue(playback.released)
    }

    @Test
    fun 退到背景時暫停() {
        val playback = FakeResultPlayback(playing = true)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                VideoshotTheme { ClipResultContent(playback, onBack = {}, surface = {}) }
            }
        }
        assertTrue(playback.isPlaying.value)

        compose.runOnUiThread { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        assertFalse(playback.isPlaying.value)
    }

    @Test
    fun 黑底時狀態列圖示是白的_離開後還原() {
        var visible by mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            VideoshotTheme { if (visible) ClipResultContent(FakeResultPlayback(), onBack = {}, surface = {}) }
        }
        val controller = compose.runOnIdle {
            WindowCompat.getInsetsController((view.context as Activity).window, view).apply { isAppearanceLightStatusBars = true }
        }

        compose.runOnIdle { visible = true }
        compose.runOnIdle { assertFalse(controller.isAppearanceLightStatusBars) }

        compose.runOnIdle { visible = false }
        compose.runOnIdle { assertTrue(controller.isAppearanceLightStatusBars) }
    }

    @Test
    fun 大字型時_10分的時間文字不折行() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                VideoshotTheme {
                    ClipResultContent(FakeResultPlayback(duration = 600_000, position = 600_000), onBack = {}, surface = {})
                }
            }
        }

        val times = compose.onAllNodesWithText("10:00").fetchSemanticsNodes()
        assertEquals(2, times.size)
        times.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertEquals(1, layouts.single().lineCount)
        }
    }
}
