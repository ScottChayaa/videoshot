package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.ui.test.assertIsDisplayed
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
class LightboxActionsTest {

    @get:Rule val compose = createComposeRule()

    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private val shot = ShotRow(
        id = 7, videoId = "abc123", atSec = 95.6, source = "storyboard", frameIndex = 3, sbLevel = 3,
        eventDate = "2026-03-01", place = null, description = null,
    )

    private val clicked = mutableListOf<String>()

    private fun show() {
        clicked.clear()
        compose.setContent {
            VideoshotTheme {
                LightboxScreen(
                    items = listOf(shot),
                    total = 1,
                    startIndex = 0,
                    loader = loader,
                    hintSeen = true,
                    onHintSeen = {},
                    onClose = {},
                    onLoadMore = {},
                    onIndexChange = {},
                    actions = LightboxActions(
                        onPlay = { clicked += "play" },
                        onAddToFolder = { clicked += "folder" },
                        onShare = { clicked += "share" },
                        onEdit = { clicked += "edit" },
                        onDelete = { clicked += "delete" },
                    ),
                )
            }
        }
    }

    /** 手冊 §三第四條:主要動作只有一個,而且是【播放這一段】。 */
    @Test
    fun 主按鈕是播放這一段() {
        show()
        compose.onNodeWithText("播放這一段").assertIsDisplayed()
        compose.onNodeWithText("播放這一段").performClick()
        assertEquals(listOf("play"), clicked)
    }

    @Test
    fun 兩顆圖示鈕在外層() {
        show()
        compose.onNodeWithContentDescription("加入分類").performClick()
        compose.onNodeWithContentDescription("分享").performClick()
        assertEquals(listOf("folder", "share"), clicked)
    }

    /** 刪除不該跟瀏覽動作並排 —— 它在 `⋯` 裡。 */
    @Test
    fun 刪除與編輯收在更多選單裡() {
        show()
        compose.onNodeWithText("刪除這張收藏").assertDoesNotExist()
        compose.onNodeWithText("編輯圖資").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("編輯圖資").assertIsDisplayed()
        compose.onNodeWithText("刪除這張收藏").assertIsDisplayed()
    }

    @Test
    fun 選單裡的編輯會回報() {
        show()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("編輯圖資").performClick()
        assertEquals(listOf("edit"), clicked)
    }

    /** 刪除要先確認,而且說得出 YouTube 原片不受影響。 */
    @Test
    fun 刪除會先問一次() {
        show()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刪除這張收藏").performClick()
        compose.onNodeWithText("刪除這張收藏？").assertIsDisplayed()
        compose.onNodeWithText("YouTube 原片不受影響").assertIsDisplayed()
        assertEquals(emptyList<String>(), clicked)
        compose.onNodeWithText("刪除").performClick()
        assertEquals(listOf("delete"), clicked)
    }

    @Test
    fun 確認框可以取消() {
        show()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刪除這張收藏").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun 分享的內容是帶秒數的短網址() {
        assertEquals("https://youtu.be/abc123?t=95", shareTextOf(shot))
    }
}
