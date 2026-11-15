package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 快門白光淡掉要多久。 */
private const val FLASH_MS = 150

/** 截到的那一格飛進【截圖】鈕要多久。 */
private const val FLY_MS = 450

/** 存好之後鈕上的勾勾留多久。 */
private const val CHECK_MS = 1_000L

/**
 * Lightbox【截圖】的動畫（2026-11-16 scott）：影片那一格閃一下白光像快門，截到的那一格縮成圓角小卡、
 * 沿弧線飛進【截圖】鈕，鈕彈一下、剪刀換成勾勾。存成功就只有動畫，不跳文字。
 *
 * 影片那一層是 WebView，本身不能拿來縮放移動——飛的是截到的那張圖，疊在最上面，飛完就拿掉。
 * 位置一律是 root 座標，畫的時候扣掉 [CaptureFlightOverlay] 自己的位置。
 */
@Stable
class CaptureFlight {
    /** 大圖那一區（影片照 Fit 置中畫在裡面）。 */
    var area: Rect = Rect.Zero

    /** 【截圖】鈕。 */
    var button: Rect = Rect.Zero

    internal val flash = Animatable(0f)
    internal val progress = Animatable(0f)
    internal var frame by mutableStateOf<ImageBitmap?>(null)

    /** 鈕的縮放（彈一下）。 */
    val bounce = Animatable(1f)

    /** true：鈕上顯示勾勾。 */
    var saved by mutableStateOf(false)
        private set

    /** 快門白光（只蓋影片那一格，不蓋左右留白）同時開始飛。 */
    suspend fun fly(image: ImageBitmap) = coroutineScope {
        progress.snapTo(0f)
        flash.snapTo(0.8f)
        frame = image
        launch { flash.animateTo(0f, tween(FLASH_MS)) }
        progress.animateTo(1f, tween(FLY_MS, easing = FastOutSlowInEasing))
        frame = null
    }

    suspend fun confirm() {
        saved = true
        bounce.animateTo(1.2f, tween(100))
        bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        delay(CHECK_MS)
        saved = false
    }
}

/** 白光與飛行中的小卡。不接觸控，疊在 Lightbox 最上面。 */
@Composable
fun CaptureFlightOverlay(flight: CaptureFlight, origin: Offset, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val image = flight.frame ?: return@Canvas
        val t = flight.progress.value
        val aspect = image.width.toFloat() / image.height
        val start = fitIn(flight.area, aspect)
        val flash = flight.flash.value
        if (flash > 0f) drawRect(Color.White.copy(alpha = flash), start.topLeft - origin, start.size)
        val endW = flight.button.width
        val end = Rect(flight.button.center - Offset(endW / 2, endW / aspect / 2), Size(endW, endW / aspect))
        // 橫向照進度走、縱向慢起快落，路線是一道往下的弧
        val center = Offset(
            lerp(start.center.x, end.center.x, t),
            lerp(start.center.y, end.center.y, t * t),
        ) - origin
        val size = Size(lerp(start.width, end.width, t), lerp(start.height, end.height, t))
        val card = Rect(center - Offset(size.width / 2, size.height / 2), size)
        val corner = CornerRadius(lerp(0f, 12.dp.toPx(), t))
        val alpha = 1f - 0.6f * t
        val path = Path().apply { addRoundRect(RoundRect(card, corner)) }
        clipPath(path) {
            drawImage(
                image,
                dstOffset = IntOffset(card.left.toInt(), card.top.toInt()),
                dstSize = IntSize(card.width.toInt().coerceAtLeast(1), card.height.toInt().coerceAtLeast(1)),
                alpha = alpha,
            )
        }
        // 白邊讓它像一張相片
        drawRoundRect(Color.White.copy(alpha = alpha), card.topLeft, card.size, corner, Stroke(2.dp.toPx()))
    }
}

/** 寬高比 [aspect] 的圖照 Fit 置中放進 [box]。 */
private fun fitIn(box: Rect, aspect: Float): Rect {
    val size = if (box.width / box.height > aspect) Size(box.height * aspect, box.height)
    else Size(box.width, box.width / aspect)
    return Rect(box.center - Offset(size.width / 2, size.height / 2), size)
}
