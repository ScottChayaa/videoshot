package com.xenyaa.videoshot.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 手冊 §零：「接上實體鍵盤時，用 Tab 走得到每個可點的東西、看得到焦點框」。
 *
 * 焦點框只在**鍵盤／方向鍵**造成的焦點下出現 —— 觸控不產生焦點，所以手機上平常看不到它。
 * 這裡直接量像素，因為「看得到」就是這個功能的全部：改成只記狀態不畫、測試就該紅。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FocusRingTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val underneath = Color(0xFF123456)

    /**
     * 左邊緣（框會畫在這裡）與正中央（框不該畫到這裡）。
     *
     * 不用 `captureToImage()` —— 它會等真正的視窗重繪，Robolectric 下等不到（`forceRedraw` 逾時）。
     * 這裡直接把 content view 畫進 bitmap 再照節點的座標裁出來。
     */
    private fun edgeAndCenter(): Pair<Color, Color> {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("target").fetchSemanticsNode().boundsInRoot
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
        return pixels[1, pixels.height / 2] to pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun showLeaf(focusRequester: FocusRequester) {
        compose.setContent {
            VideoshotTheme {
                Box(
                    Modifier
                        .testTag("target")
                        .size(64.dp)
                        .background(underneath)
                        .focusRing()
                        .focusRequester(focusRequester)
                        .focusable(),
                )
            }
        }
    }

    @Test
    fun 沒有焦點就不畫框() {
        showLeaf(FocusRequester())

        val (edge, center) = edgeAndCenter()
        assertEquals(underneath, edge)
        assertEquals(underneath, center)
    }

    @Test
    fun 拿到焦點就在邊緣畫出框() {
        val requester = FocusRequester()
        showLeaf(requester)

        compose.runOnIdle { requester.requestFocus() }

        val (edge, center) = edgeAndCenter()
        assertNotEquals("拿到焦點後邊緣應該畫出焦點框", underneath, edge)
        assertEquals("框只畫在邊緣，中間維持原樣", underneath, center)
    }

    /**
     * 焦點落在**子節點**上也要畫框 —— `NavigationBarItem` 這類現成元件的 focusable 藏在內部，
     * 外面套 modifier 的人拿不到 `isFocused`，只拿得到 `hasFocus`。
     */
    @Test
    fun 子節點拿到焦點時外層也畫框() {
        val requester = FocusRequester()
        compose.setContent {
            VideoshotTheme {
                Box(
                    Modifier
                        .testTag("target")
                        .size(64.dp)
                        .background(underneath)
                        .focusRing(),
                ) {
                    Box(Modifier.size(32.dp).focusRequester(requester).focusable())
                }
            }
        }

        compose.runOnIdle { requester.requestFocus() }

        val (edge, _) = edgeAndCenter()
        assertNotEquals("子節點拿到焦點時，外層也要畫出焦點框", underneath, edge)
    }

    /** 框的顏色來自色系的 `focusRing`，不是寫死的顏色 —— 換色系時框要跟著換。 */
    @Test
    fun 框的顏色來自色系() {
        val requester = FocusRequester()
        showLeaf(requester)

        compose.runOnIdle { requester.requestFocus() }

        val (edge, _) = edgeAndCenter()
        val ring = Palettes.DEFAULT.light.focusRing
        val expected = Color(
            red = ring.red * ring.alpha + underneath.red * (1 - ring.alpha),
            green = ring.green * ring.alpha + underneath.green * (1 - ring.alpha),
            blue = ring.blue * ring.alpha + underneath.blue * (1 - ring.alpha),
        )
        assertEquals(expected.red, edge.red, 0.02f)
        assertEquals(expected.green, edge.green, 0.02f)
        assertEquals(expected.blue, edge.blue, 0.02f)
    }
}
