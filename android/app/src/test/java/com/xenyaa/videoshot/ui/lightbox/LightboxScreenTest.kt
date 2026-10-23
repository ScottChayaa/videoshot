package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xenyaa.videoshot.ui.testing.pixelsAround
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

    private var closed = 0
    private var loadMores = 0
    private var lastIndex = -1

    private fun show(
        items: List<ShotRow> = rows(3),
        hasMore: Boolean = false,
        startIndex: Int = 0,
    ) {
        compose.setContent {
            VideoshotTheme {
                LightboxScreen(
                    items = items,
                    hasMore = hasMore,
                    startIndex = startIndex,
                    loader = loader,
                    onClose = { closed++ },
                    onLoadMore = { loadMores++ },
                    onIndexChange = { lastIndex = it },
                    actions = LightboxActions(),
                )
            }
        }
    }

    /** 16B 設計決議 1：看圖時不顯示第幾張。 */
    @Test
    fun 不顯示第幾張() {
        show(items = rows(3), startIndex = 1)
        compose.onNodeWithText("共", substring = true).assertDoesNotExist()
        compose.onNodeWithText("第 ", substring = true).assertDoesNotExist()
    }

    @Test
    fun 上下滑動換張() {
        show(items = rows(3), startIndex = 0)
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("片段2").assertIsDisplayed()
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithText("片段1").assertIsDisplayed()
    }

    @Test
    fun 換張會回報目前位置() {
        show(items = rows(3), startIndex = 0)
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(1, lastIndex)
    }

    @Test
    fun 按返回會回報() {
        show()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, closed)
    }

    /** 滑到已載入的尾端要去要下一頁，否則 N 會卡在已載入的最後一張。 */
    @Test
    fun 滑到尾端會要求載入下一頁() {
        show(items = rows(3), hasMore = true, startIndex = 1)
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assert(loadMores >= 1)
    }

    @Test
    fun 沒有下一頁時滑到尾端不會要求載入() {
        show(items = rows(3), hasMore = false, startIndex = 1)
        compose.onNodeWithContentDescription("收藏的大圖").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(0, loadMores)
    }

    @Test
    fun 顯示這一張的描述_不顯示影片秒數() {
        show(items = rows(3), startIndex = 2)
        compose.onNodeWithText("片段3").assertIsDisplayed()
        compose.onNodeWithText("01:30").assertDoesNotExist()
    }

    /** Lightbox 的底固定是近黑，圖示色要固定是白色，不能跟著色系的 accentInk 走。 */
    @Test
    fun 圖示用白色() {
        show()
        // 不用 captureToImage()（Robolectric 下 forceRedraw 逾時），改用 FocusProbe 的 pixelsAround
        val pixels = compose.pixelsAround(compose.onNodeWithContentDescription("加入相簿"), margin = 0)
        var maxBrightness = 0f
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val c = pixels[x, y]
            maxBrightness = maxOf(maxBrightness, (c.red + c.green + c.blue) / 3f)
        }
        assert(maxBrightness > 0.9f) { "加入分類圖示最亮像素只有 $maxBrightness，深底上看不見" }
    }
}
