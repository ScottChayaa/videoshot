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
import androidx.compose.ui.test.swipeLeft
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
            VideoshotTheme {
                LightboxScreen(
                    items = items, hasMore = false, startIndex = 0, loader = loader,
                    hintSeen = true, onHintSeen = {}, onClose = {}, onLoadMore = {}, onIndexChange = {},
                    actions = LightboxActions(), clip = deps, covered = covered,
                )
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

    @Test
    fun 點一下暫停_再點一下繼續() {
        show()
        awaitPlaying()
        compose.onNode(playing).performClick()
        compose.waitForIdle()
        compose.waitUntil(3_000) { compose.onAllNodes(pausedByUser).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("pause", player.calls.last())
        compose.onNode(pausedByUser).performClick()
        compose.waitForIdle()
        compose.waitUntil(3_000) { player.calls.last() == "resume" }
    }

    @Test
    fun 左右滑之後換下一張的秒數() {
        show()
        awaitPlaying()
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeLeft() }
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
