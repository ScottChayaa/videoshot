package com.xenyaa.videoshot.ui.lightbox

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.ClipSource
import com.xenyaa.videoshot.player.FakeClipPlayer
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import org.junit.Assert.assertEquals
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
    private var items by mutableStateOf(rows(3))

    private fun show() {
        val deps = LightboxClipDeps(
            source = { ClipSource(playableInEmbed = true, durationSec = 600) },
            surface = { _, onPlayer, _ -> LaunchedEffect(Unit) { onPlayer(player) } },
        )
        compose.setContent {
            VideoshotTheme {
                LightboxScreen(
                    items = items, hasMore = false, startIndex = 0, loader = loader,
                    onClose = {}, onLoadMore = {}, onIndexChange = {},
                    actions = LightboxActions(), clip = deps,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun act(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    @Test
    fun 停在那張的秒數_不循環() {
        show()
        compose.waitUntil(3_000) { "hold:30.0" in player.calls }
        assertEquals(listOf("load:v1", "cue:30.0", "hold:30.0"), player.calls)
    }

    /** 影片畫面不能操作：沒有播放／暫停、聲音、全螢幕鈕（2026-11-15 scott）。 */
    @Test
    fun 沒有控制層() {
        show()
        compose.waitUntil(3_000) { "hold:30.0" in player.calls }
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { click() }
        compose.waitForIdle()
        listOf("暫停", "播放", "靜音", "取消靜音", "全螢幕").forEach {
            compose.onNodeWithContentDescription(it).assertDoesNotExist()
        }
    }

    @Test
    fun 上下滑之後停在下一張的秒數() {
        show()
        compose.waitUntil(3_000) { "hold:30.0" in player.calls }
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.waitUntil(3_000) { "hold:60.0" in player.calls }
        // 同一支影片不重載頁面
        assertEquals(1, player.calls.count { it.startsWith("load") })
    }

    @Test
    fun 目前這張換成別張時重新定格() {
        // 刪掉目前那張：Lightbox 停在同一個頁碼，但內容換成下一張
        show()
        compose.waitUntil(3_000) { "hold:30.0" in player.calls }
        act { items = items.drop(1) }
        compose.waitUntil(3_000) { "hold:60.0" in player.calls }
    }
}
