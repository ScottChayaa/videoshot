package com.xenyaa.videoshot.ui.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.xenyaa.videoshot.ui.theme.Palettes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 量焦點框用的工具（手冊 §零：「看得到焦點框」）。
 *
 * 不用 `captureToImage()` —— 它會等真正的視窗重繪，Robolectric 下等不到（`forceRedraw` 逾時）。
 * 這裡直接把 content view 畫進 bitmap，再照節點座標裁出來。
 *
 * 裁的範圍比節點大一圈：`IconButton` 這類元件的語意節點是內層的觸控區，
 * 框畫在外層，剛好落在節點外面。
 */
fun AndroidComposeTestRule<*, ComponentActivity>.pixelsAround(
    node: SemanticsNodeInteraction,
    margin: Int = 12,
): Grid {
    waitForIdle()
    val bounds = node.fetchSemanticsNode().boundsInRoot
    val view = activity.findViewById<View>(android.R.id.content)
    val shot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    runOnUiThread { view.draw(Canvas(shot)) }
    val left = max(0, bounds.left.toInt() - margin)
    val top = max(0, bounds.top.toInt() - margin)
    val right = min(view.width, bounds.right.toInt() + margin)
    val bottom = min(view.height, bounds.bottom.toInt() + margin)
    val map = Bitmap.createBitmap(shot, left, top, right - left, bottom - top).asImageBitmap().toPixelMap()
    return Grid(map.width, map.height) { x, y -> map[x, y] }
}

/** 一塊裁好的像素，只為了讓兩張圖能逐點比對。 */
class Grid(val width: Int, val height: Int, private val at: (Int, Int) -> Color) {
    operator fun get(x: Int, y: Int): Color = at(x, y)
}

/** 預設色系的焦點框疊在 [under] 上應該長成的樣子。 */
fun ringOver(under: Color): Color {
    val ring = Palettes.DEFAULT.light.focusRing
    return Color(
        red = ring.red * ring.alpha + under.red * (1 - ring.alpha),
        green = ring.green * ring.alpha + under.green * (1 - ring.alpha),
        blue = ring.blue * ring.alpha + under.blue * (1 - ring.alpha),
    )
}

/**
 * 逐點比對聚焦前後，數出「這一點正好變成焦點框的顏色」的像素有幾個。
 *
 * 用數的而不是抓固定座標：框畫在哪一圈要看元件（`IconButton` 的框在語意節點外面）。
 * 也不能只問「有沒有變色」—— `clickable` 拿到焦點本來就會鋪一層淡底色，
 * 少掛 `focusRing()` 一樣會變；那層淡底色離框的顏色很遠，比對顏色才擋得住。
 */
fun ringPixels(before: Grid, after: Grid, tolerance: Float = 0.06f): Int {
    require(before.width == after.width && before.height == after.height) { "兩張圖大小不同" }
    var hits = 0
    for (y in 0 until before.height) {
        for (x in 0 until before.width) {
            val was = before[x, y]
            val now = after[x, y]
            // 要求「有變」再比顏色：底色本來就接近框色的地方（例如主色按鈕）不算數
            if (!near(now, was, 0.02f) && near(now, ringOver(was), tolerance)) hits++
        }
    }
    return hits
}

fun near(a: Color, b: Color, tolerance: Float = 0.06f): Boolean =
    abs(a.red - b.red) < tolerance &&
        abs(a.green - b.green) < tolerance &&
        abs(a.blue - b.blue) < tolerance
