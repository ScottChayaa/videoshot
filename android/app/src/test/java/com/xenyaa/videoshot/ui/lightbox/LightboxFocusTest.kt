package com.xenyaa.videoshot.ui.lightbox

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.requestFocus
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.testing.pixelsAround
import com.xenyaa.videoshot.ui.testing.ringPixels
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 手冊 §零 ＋ §三：Lightbox 的動作列在鍵盤下也走得到、看得到框。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LightboxFocusTest {

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

    private lateinit var inputMode: InputModeManager

    private fun show() {
        val items = (1..3).map {
            ShotRow(
                id = it.toLong(), videoId = "v1", atSec = it * 30.0, source = "storyboard",
                frameIndex = it, sbLevel = 3, eventDate = "2026-03-01", place = null, description = null,
            )
        }
        compose.setContent {
            inputMode = LocalInputModeManager.current
            VideoshotTheme {
                LightboxScreen(
                    items = items,
                    total = items.size,
                    startIndex = 0,
                    loader = loader,
                    hintSeen = true,
                    onHintSeen = {},
                    onClose = {},
                    onLoadMore = {},
                    onIndexChange = {},
                    actions = LightboxActions(),
                )
            }
        }
    }

    /**
     * 【播放這一段】刻意不驗顏色 —— 它的底就是主色，跟焦點框同一個色相，
     * 疊上去幾乎不變色，這樣的比對驗不出東西。它跟其他鈕共用同一個 modifier。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 關閉鈕拿到焦點會畫出框() {
        show()
        val close = compose.onNodeWithContentDescription("關閉")
        val before = compose.pixelsAround(close)

        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        close.requestFocus()

        close.assertIsFocused()
        val ring = ringPixels(before, compose.pixelsAround(close))
        assertTrue("關閉鈕拿到焦點時要畫出焦點框（只找到 $ring 個框的像素）", ring > 50)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 分享鈕拿到焦點會畫出框() {
        show()
        val share = compose.onNodeWithContentDescription("分享")
        val before = compose.pixelsAround(share)

        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        share.requestFocus()

        share.assertIsFocused()
        val ring = ringPixels(before, compose.pixelsAround(share))
        assertTrue("分享鈕拿到焦點時要畫出焦點框（只找到 $ring 個框的像素）", ring > 50)
    }
}
