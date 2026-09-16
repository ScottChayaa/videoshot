package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.data.repo.model.ShotRow
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
class LightboxDeleteTest {

    @get:Rule val compose = createComposeRule()

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

    private fun row(id: Long, sec: Double) = ShotRow(
        id = id, videoId = "v1", atSec = sec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
        eventDate = "2026-03-01", place = null, description = null,
    )

    private var closed = 0

    /** 案例 17：刪除之後自動滑到下一張；刪到最後一張則關閉。 */
    @Test
    fun 刪除後停在下一張() {
        compose.setContent {
            var items by remember { mutableStateOf(listOf(row(1, 30.0), row(2, 90.0), row(3, 150.0))) }
            VideoshotTheme {
                LightboxScreen(
                    items = items,
                    total = items.size,
                    startIndex = 1,
                    loader = loader,
                    hintSeen = true, onHintSeen = {},
                    onClose = { closed++ }, onLoadMore = {}, onIndexChange = {},
                    actions = LightboxActions(onDelete = { shot -> items = items.filterNot { it.id == shot.id } }),
                )
            }
        }
        compose.onNodeWithText("01:30").assertExists()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刪除這張收藏").performClick()
        compose.onNodeWithText("刪除").performClick()
        compose.waitForIdle()
        // 原本第 2 張沒了，位置 1 現在是原本的第 3 張（02:30）
        compose.onNodeWithText("02:30").assertExists()
        compose.onNodeWithText("第 2 / 共 2 張").assertExists()
    }

    @Test
    fun 刪到最後一張會關閉() {
        compose.setContent {
            var items by remember { mutableStateOf(listOf(row(1, 30.0))) }
            VideoshotTheme {
                LightboxScreen(
                    items = items,
                    total = items.size,
                    startIndex = 0,
                    loader = loader,
                    hintSeen = true, onHintSeen = {},
                    onClose = { closed++ }, onLoadMore = {}, onIndexChange = {},
                    actions = LightboxActions(onDelete = { items = emptyList() }),
                )
            }
        }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刪除這張收藏").performClick()
        compose.onNodeWithText("刪除").performClick()
        compose.waitForIdle()
        assertEquals(1, closed)
    }
}
