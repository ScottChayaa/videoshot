package com.xenyaa.videoshot.ui.lightbox

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.CompositionLocalProvider
import com.xenyaa.videoshot.player.LocalPlayerFullscreen
import com.xenyaa.videoshot.player.PlayerFullscreenState
import com.xenyaa.videoshot.player.CONTROLS_AUTO_HIDE_MS
import org.junit.Assert.assertFalse
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.ClipSource
import com.xenyaa.videoshot.player.FakeClipPlayer
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LightboxClipTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun rows(n: Int) = (1..n).map {
        ShotRow(
            id = it.toLong(), videoId = "v1", atSec = it * 30.0, source = "storyboard",
            frameIndex = it, sbLevel = 3, eventDate = "2026-03-01", place = null, description = "片段$it",
        )
    }

    private val player = FakeClipPlayer()
    private var covered by mutableStateOf(false)
    private var items by mutableStateOf(rows(3))
    private var muted by mutableStateOf(false)
    private val fullscreen = PlayerFullscreenState()
    private var closed = 0

    private val playing = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "片段播放中")
    private val pausedByUser = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "片段已暫停")

    private fun show() {
        // 倒數設 0：時序由 LightboxClipControllerTest 用虛擬時間驗，這裡只驗接線
        val deps = LightboxClipDeps(
            source = { ClipSource(playableInEmbed = true, durationSec = 600) },
            delayMs = 0,
            surface = { _, onPlayer, _ -> LaunchedEffect(Unit) { onPlayer(player) } },
        )
        compose.setContent {
            CompositionLocalProvider(LocalPlayerFullscreen provides fullscreen) {
                VideoshotTheme {
                    LightboxScreen(
                        items = items, hasMore = false, startIndex = 0, loader = loader,
                        hintSeen = true, onHintSeen = {}, onClose = { closed++ }, onLoadMore = {}, onIndexChange = {},
                        actions = LightboxActions(), clip = deps, covered = covered,
                        muted = muted, onMutedChange = { muted = it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * 狀態改了、點了之後要 [waitForIdle][androidx.compose.ui.test.junit4.ComposeTestRule.waitForIdle]：
     * Robolectric 下 `waitUntil` 不推進測試時鐘，重組與 LaunchedEffect 不會自己跑（實測：只用 `waitUntil`
     * 的話 `covered = true` 永遠不會進到組合裡）。
     */
    private fun act(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    private fun awaitPlaying() =
        compose.waitUntil(3_000) { compose.onAllNodes(playing).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun 停下來就從那張的秒數循環() {
        show()
        awaitPlaying()
        assertTrue("loop:30.0-33.0" in player.calls)
    }

    /** 片段開始播之後改成手動推時鐘：按鈕 3 秒自己收起，自動推進的話一 waitForIdle 就收掉了。 */
    private fun playingWithManualClock() {
        show()
        awaitPlaying()
        compose.mainClock.autoAdvance = false
    }

    /** 等過點兩下的判定時間（同 PlayerControlsTest）。 */
    private fun settleTap() = compose.mainClock.advanceTimeBy(400)

    @Test
    fun 點兩下暫停_再點兩下繼續() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { doubleClick() }
        settleTap()
        compose.onNode(pausedByUser).assertExists()
        assertEquals("pause", player.calls.last())
        compose.onNode(pausedByUser).performTouchInput { doubleClick() }
        settleTap()
        assertEquals("resume", player.calls.last())
    }

    @Test
    fun 點一下叫出聲音與全螢幕按鈕_不暫停() {
        playingWithManualClock()
        compose.onNodeWithContentDescription("全螢幕").assertDoesNotExist()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNodeWithContentDescription("全螢幕").assertExists()
        compose.onNodeWithContentDescription("關掉聲音").assertExists()
        compose.onNode(playing).assertExists()
        assertFalse("pause" in player.calls)
    }

    @Test
    fun 按鈕三秒沒動作自己收起() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.mainClock.advanceTimeBy(CONTROLS_AUTO_HIDE_MS + 500)
        compose.onNodeWithContentDescription("全螢幕").assertDoesNotExist()
    }

    @Test
    fun 按鈕開著時再點一下就收起() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.mainClock.advanceTimeBy(500)   // 淡出動畫
        compose.onNodeWithContentDescription("全螢幕").assertDoesNotExist()
    }

    @Test
    fun 聲音鈕切換靜音_播放中馬上生效() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNodeWithContentDescription("關掉聲音").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(muted)
        assertEquals("mute:true", player.calls.last())
        compose.onNodeWithContentDescription("打開聲音").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertFalse(muted)
        assertEquals("mute:false", player.calls.last())
    }

    @Test
    fun 關掉聲音時新的片段也是靜音() {
        muted = true
        show()
        awaitPlaying()
        assertTrue("loop:30.0-33.0:muted" in player.calls)
    }

    @Test
    fun 全螢幕藏起其他區塊_橫的影片轉橫向() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNodeWithContentDescription("全螢幕").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(fullscreen.active)
        assertTrue(fullscreen.landscape)
        compose.onNodeWithContentDescription("更多").assertDoesNotExist()
        compose.onNodeWithText("播放這一段").assertDoesNotExist()
        // 片段沒有被打斷
        compose.onNode(playing).assertExists()
    }

    @Test
    fun 直的影片全螢幕維持直向() {
        player.videoWidth = 1080
        player.videoHeight = 1920
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNodeWithContentDescription("全螢幕").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(fullscreen.active)
        assertFalse(fullscreen.landscape)
    }

    @Test
    fun 全螢幕按返回鍵只離開全螢幕() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNodeWithContentDescription("全螢幕").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(100)
        assertFalse(fullscreen.active)
        assertEquals(0, closed)
        compose.onNodeWithContentDescription("更多").assertExists()
    }

    @Test
    fun 全螢幕再按一次按鈕離開() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNodeWithContentDescription("全螢幕").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("離開全螢幕").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertFalse(fullscreen.active)
    }

    @Test
    fun 全螢幕中上下滑換張() {
        playingWithManualClock()
        compose.onNode(playing).performTouchInput { click() }
        settleTap()
        compose.onNodeWithContentDescription("全螢幕").performClick()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.waitUntil(3_000) { "loop:60.0-63.0" in player.calls }
        assertTrue(fullscreen.active)
    }

    @Test
    fun 關掉Lightbox時離開全螢幕() {
        var open by mutableStateOf(true)
        val deps = LightboxClipDeps(
            source = { ClipSource(playableInEmbed = true, durationSec = 600) },
            delayMs = 0,
            surface = { _, onPlayer, _ -> LaunchedEffect(Unit) { onPlayer(player) } },
        )
        compose.setContent {
            CompositionLocalProvider(LocalPlayerFullscreen provides fullscreen) {
                VideoshotTheme {
                    if (open) LightboxScreen(
                        items = items, hasMore = false, startIndex = 0, loader = loader,
                        hintSeen = true, onHintSeen = {}, onClose = {}, onLoadMore = {}, onIndexChange = {},
                        actions = LightboxActions(), clip = deps,
                    )
                }
            }
        }
        awaitPlaying()
        compose.runOnUiThread { fullscreen.enter(landscape = true) }
        compose.waitForIdle()
        act { open = false }
        assertFalse(fullscreen.active)
    }

    @Test
    fun 上下滑之後換下一張的秒數() {
        show()
        awaitPlaying()
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.waitUntil(3_000) { "loop:60.0-63.0" in player.calls }
        assertTrue("pause" in player.calls)
        // 同一支影片不重載頁面
        assertEquals(1, player.calls.count { it.startsWith("load") })
    }

    @Test
    fun 呼叫端的抽屜開著時暫停() {
        show()
        awaitPlaying()
        act { covered = true }
        compose.waitUntil(3_000) { player.calls.last() == "pause" }
        act { covered = false }
        compose.waitUntil(3_000) { player.calls.last() == "resume" }
    }

    @Test
    fun 更多選單打開時暫停() {
        show()
        awaitPlaying()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.waitForIdle()
        compose.waitUntil(3_000) { player.calls.last() == "pause" }
    }

    @Test
    fun 目前這張換成別張時重新倒數() {
        // 刪掉播放中的那張：Lightbox 停在同一個頁碼，但內容換成下一張
        show()
        awaitPlaying()
        act { items = items.drop(1) }
        compose.waitUntil(3_000) { "loop:60.0-63.0" in player.calls }
    }
}
