package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
class LightboxScreenTest {

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

    private fun rows(n: Int) = (1..n).map {
        ShotRow(
            id = it.toLong(), videoId = "v1", atSec = it * 30.0, source = "storyboard",
            frameIndex = it, sbLevel = 3, eventDate = "2026-03-01", place = null, description = null,
        )
    }

    private var closed = 0
    private var loadMores = 0
    private var lastIndex = -1

    private fun show(
        items: List<ShotRow> = rows(3),
        total: Int = items.size,
        startIndex: Int = 0,
        hintSeen: Boolean = true,
        onHintSeen: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                LightboxScreen(
                    items = items,
                    total = total,
                    startIndex = startIndex,
                    loader = loader,
                    hintSeen = hintSeen,
                    onHintSeen = onHintSeen,
                    onClose = { closed++ },
                    onLoadMore = { loadMores++ },
                    onIndexChange = { lastIndex = it },
                    actions = LightboxActions(),
                )
            }
        }
    }

    @Test
    fun 上方顯示第幾張與總數() {
        show(items = rows(3), total = 12, startIndex = 1)
        compose.onNodeWithText("第 2 / 共 12 張").assertIsDisplayed()
    }

    @Test
    fun 左右滑動換張() {
        show(items = rows(3), startIndex = 0)
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("第 2 / 共 3 張").assertIsDisplayed()
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("第 1 / 共 3 張").assertIsDisplayed()
    }

    @Test
    fun 換張會回報目前位置() {
        show(items = rows(3), startIndex = 0)
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, lastIndex)
    }

    /** 手冊 §三第三條：第一次開啟才出現。 */
    @Test
    fun 第一次開啟會出現滑動提示() {
        var seen = 0
        show(hintSeen = false, onHintSeen = { seen++ })
        compose.onNodeWithText("左右滑動看上一張／下一張").assertIsDisplayed()
        // **還沒消失也還沒記下來** —— 一開啟就記成看過的話，提示會在使用者讀到之前就沒了
        assertEquals(0, seen)
    }

    @Test
    fun 滑一次之後提示消失並記成看過() {
        var seen = 0
        show(hintSeen = false, onHintSeen = { seen++ })
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("左右滑動看上一張／下一張").assertDoesNotExist()
        assertEquals(1, seen)
    }

    @Test
    fun 點提示本身也可以關掉它() {
        var seen = 0
        show(hintSeen = false, onHintSeen = { seen++ })
        compose.onNodeWithText("左右滑動看上一張／下一張").performClick()
        compose.onNodeWithText("左右滑動看上一張／下一張").assertDoesNotExist()
        assertEquals(1, seen)
    }

    @Test
    fun 看過之後不再出現() {
        show(hintSeen = true)
        compose.onNodeWithText("左右滑動看上一張／下一張").assertDoesNotExist()
    }

    @Test
    fun 按關閉會回報() {
        show()
        compose.onNodeWithContentDescription("關閉").performClick()
        assertEquals(1, closed)
    }

    /** 滑到已載入的尾端要去要下一頁，否則 N 會卡在已載入的最後一張。 */
    @Test
    fun 滑到尾端會要求載入下一頁() {
        show(items = rows(3), total = 50, startIndex = 1)
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assert(loadMores >= 1)
    }

    @Test
    fun 顯示這一張的影片秒數() {
        show(items = rows(3), startIndex = 2)
        compose.onNodeWithText("01:30").assertIsDisplayed()
    }
}
