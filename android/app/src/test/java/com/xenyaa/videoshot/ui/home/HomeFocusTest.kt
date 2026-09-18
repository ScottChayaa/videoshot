package com.xenyaa.videoshot.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.Palettes
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
import kotlin.math.abs

/**
 * 手冊 §零：接上實體鍵盤時，Tab 走得到縮圖、看得到焦點框。
 *
 * 焦點框自己的行為在 `FocusRingTest`；這裡驗的是**首頁真的把它接上去了**。
 * 光看「像素有沒有變」不夠 —— `clickable` 本來就會在拿到焦點時鋪一層淡淡的底色，
 * 少掛 `focusRing()` 也會變。所以這裡比對**顏色**：邊緣要變成色系的 `focusRing`。
 *
 * 測試一開始要切到鍵盤輸入模式：`clickable` 的可聚焦性在觸控模式下是關的
 * （Compose 的 `focusableInNonTouchMode`），實機上第一次按 Tab 就會切過去。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeFocusTest {

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

    private val tile = "片段縮圖 01:05"

    private lateinit var inputMode: InputModeManager

    private fun show() {
        val row = ShotRow(
            id = 1, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
            eventDate = "2026-03-05", place = null, description = null,
        )
        val state = HomeStore.appendPage(HomeState(), Page(listOf(row), null), 1)
        compose.setContent {
            inputMode = LocalInputModeManager.current
            VideoshotTheme {
                HomeScreen(
                    state = state,
                    loader = loader,
                    listState = rememberLazyGridState(),
                    onOpen = {},
                    onLoadMore = {},
                    onPickMonth = {},
                    onFacetClick = { _, _ -> },
                )
            }
        }
    }

    /** 縮圖上緣中點的像素 —— 框畫在這裡。 */
    private fun topEdgeOfTile(): Color {
        compose.waitForIdle()
        val bounds = compose.onNodeWithContentDescription(tile).fetchSemanticsNode().boundsInRoot
        val view = compose.activity.findViewById<View>(android.R.id.content)
        val shot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(shot)) }
        val pixels = Bitmap.createBitmap(
            shot,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt(),
            bounds.height.toInt(),
        ).asImageBitmap().toPixelMap()
        return pixels[pixels.width / 2, 1]
    }

    /** `focusRing` 疊在 [under] 上應該長成的樣子。 */
    private fun ringOver(under: Color): Color {
        val ring = Palettes.DEFAULT.light.focusRing
        return Color(
            red = ring.red * ring.alpha + under.red * (1 - ring.alpha),
            green = ring.green * ring.alpha + under.green * (1 - ring.alpha),
            blue = ring.blue * ring.alpha + under.blue * (1 - ring.alpha),
        )
    }

    private fun near(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < 0.08f && abs(a.green - b.green) < 0.08f && abs(a.blue - b.blue) < 0.08f

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun Tab_走得到縮圖並畫出焦點框() {
        show()
        val before = topEdgeOfTile()

        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        // 日曆鈕是這個畫面第一個可聚焦的東西，往下一個就是縮圖
        compose.onNodeWithContentDescription("依時間篩選").requestFocus()
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }

        compose.onNodeWithContentDescription(tile).assertIsFocused()

        val edge = topEdgeOfTile()
        val expected = ringOver(before)
        assertTrue("縮圖邊緣應該是焦點框的顏色（預期約 $expected，實際 $edge）", near(edge, expected))
    }
}
