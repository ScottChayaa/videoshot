package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.xenyaa.videoshot.data.repo.model.ShotRow
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

/** Lightbox 的大圖照照片本身的寬高比完整顯示：橫的撐滿寬、直的（Shorts）撐滿高，不裁切。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LightboxAspectTest {

    @get:Rule val compose = createComposeRule()

    private fun loaderOf(bitmap: ImageBitmap?) = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Bytes(ByteArray(0))
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
        },
        decodeFile = { null }, decodeBytes = { bitmap }, cover = { null },
    )

    private val shot = ShotRow(
        id = 7, videoId = "abc123", atSec = 20.0, source = "storyboard", frameIndex = 20, sbLevel = 3,
        eventDate = "2026-08-16", place = null, description = "大圖",
    )

    /** 回傳大圖在畫面上的寬與高（像素）。 */
    private fun showAndMeasure(bitmap: ImageBitmap?): Pair<Int, Int> {
        compose.setContent {
            VideoshotTheme {
                LightboxScreen(
                    items = listOf(shot), hasMore = false, startIndex = 0, loader = loaderOf(bitmap),
                    hintSeen = true, onHintSeen = {}, onClose = {}, onLoadMore = {}, onIndexChange = {},
                    actions = LightboxActions({}, {}, {}, {}, {}),
                )
            }
        }
        compose.waitForIdle()
        val size = compose.onNodeWithContentDescription("大圖").fetchSemanticsNode().size
        return size.width to size.height
    }

    @Test
    fun 直的照片是直的框_不裁成橫的() {
        val (w, h) = showAndMeasure(ImageBitmap(101, 180))
        assertTrue("直的照片高要大於寬：${w}x$h", h > w)
        assertEquals(101f / 180f, w.toFloat() / h, 0.02f)
    }

    @Test
    fun 橫的照片撐滿寬度() {
        val (w, h) = showAndMeasure(ImageBitmap(160, 90))
        assertEquals(16f / 9f, w.toFloat() / h, 0.02f)
        val screenW = compose.onNodeWithContentDescription("收藏的大圖").fetchSemanticsNode().size.width
        assertEquals(screenW, w)
    }

    @Test
    fun 拿不到圖時預留框維持橫的十六比九() {
        val (w, h) = showAndMeasure(null)
        assertEquals(16f / 9f, w.toFloat() / h, 0.02f)
    }
}
