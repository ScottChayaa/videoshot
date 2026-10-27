package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 時間軸比例：1 秒 40dp。 */
internal const val DP_PER_SECOND = 40f

/** 段與段之間的間隔。 */
internal const val SEGMENT_GAP_DP = 2f

/** 時間軸條的高度。 */
internal val StripHeight = 64.dp

private fun dpOf(ms: Long): Float = ms * DP_PER_SECOND / 1000f

/**
 * 時間軸條的座標表：每段的起點（毫秒）與左緣、右緣（內容座標 dp，從第 1 段左緣算起）一次算好，
 * 之後查座標、找可見範圍都用二分搜尋，不必每次從頭累加。純計算，不依賴 Compose。
 * 段與段之間有 2dp 間隔，所以座標不是單純的毫秒乘比例。
 */
internal class TimelineGeometry(timeline: Timeline) {
    private val lengths = LongArray(timeline.segments.size) { timeline.segments[it].lengthMs }
    private val startMs = LongArray(lengths.size)
    private val leftDp = FloatArray(lengths.size)
    private val rightDp = FloatArray(lengths.size)

    val count: Int get() = lengths.size
    val durationMs: Long

    init {
        var start = 0L
        for (i in lengths.indices) {
            startMs[i] = start
            leftDp[i] = dpOf(start) + i * SEGMENT_GAP_DP
            rightDp[i] = leftDp[i] + dpOf(lengths[i])
            start += lengths[i]
        }
        durationMs = start
    }

    fun leftOf(index: Int): Float = leftDp[index]
    fun widthOf(index: Int): Float = rightDp[index] - leftDp[index]

    /** [ms] 的內容座標。超出範圍夾在兩端；空時間軸回 0。 */
    fun xDp(ms: Long): Float {
        if (count == 0) return 0f
        // 最後一個起點不大於 ms 的段；ms 在第 1 段之前（負數）夾到第 1 段
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (startMs[mid] <= ms) lo = mid else hi = mid - 1
        }
        return leftDp[lo] + dpOf((ms - startMs[lo]).coerceIn(0L, lengths[lo]))
    }

    /** [xDp] 的反運算。落在間隔上算後一段的起點，超出兩端夾回 0 與總長。 */
    fun msAt(xDp: Float): Long {
        val i = firstEndingAfter(xDp)
        if (i >= count) return durationMs
        if (xDp <= leftDp[i]) return startMs[i]
        return startMs[i] + ((xDp - leftDp[i]) * 1000f / DP_PER_SECOND).roundToLong()
    }

    /** 右緣在 [x] 右邊的第一段（全部都在左邊就回 [count]）。 */
    fun firstEndingAfter(x: Float): Int {
        var lo = 0
        var hi = count
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (rightDp[mid] > x) hi = mid else lo = mid + 1
        }
        return lo
    }

    /** 左緣在 [x] 左邊的最後一段（全部都在右邊就回 -1）。 */
    fun lastStartingBefore(x: Float): Int {
        var lo = -1
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (leftDp[mid] < x) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/** 剪輯用的素材轉成 [MediaThumbLoader] 吃的型別（兩邊欄位相同）。 */
internal fun ClipSource.toMediaItem(): MediaItem = MediaItem(
    id = id,
    uri = uri,
    kind = if (kind == SourceKind.VIDEO) MediaKind.VIDEO else MediaKind.PHOTO,
    takenAtMs = takenAtMs,
    durationMs = durationMs,
    width = width,
    height = height,
    rotation = rotation,
)

/**
 * 時間軸條：水平一排，1 秒 40dp，段與段之間 2dp。
 *
 * 播放頭是畫面正中間一條固定的主色直線，時間軸在它底下捲動：[playheadMs] 決定捲到哪裡。
 * 左右拖整條＝[onSeek]（拖曳距離換回毫秒）；開始拖的時候先呼叫 [onDragStart]（呼叫端用來暫停播放）。
 * 點一段＝[onSelect]（index），點已選的那段＝[onSelect]（null）。已選的段有 2dp 主色外框；
 * 每段用該素材的縮圖鋪滿、裁切；縮圖還沒載好時是灰底。
 *
 * 只組合畫面上看得到的段落（二分搜尋找範圍），長影片（10 分鐘＝24000dp 寬）不會一次畫出整條。
 * 每段用自訂排版以「段的完整寬度」量測、再放到（可能在畫面外的）位置：
 * 比條寬還長的段不會被父層的寬度限制截短。
 */
@Composable
fun ClipTimelineStrip(
    timeline: Timeline,
    sources: List<ClipSource>,
    playheadMs: Long,
    selected: Int?,
    thumbLoader: MediaThumbLoader,
    onSeek: (Long) -> Unit,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
) {
    val sourceById = remember(sources) { sources.associateBy { it.id } }
    val geometry = remember(timeline) { TimelineGeometry(timeline) }
    val density = LocalDensity.current.density
    val latestGeometry by rememberUpdatedState(geometry)
    val latestPlayhead by rememberUpdatedState(playheadMs)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestDragStart by rememberUpdatedState(onDragStart)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(StripHeight)
            .clipToBounds()
            .pointerInput(Unit) {
                // 以拖曳開始時的內容座標加上累計位移算位置，不靠回頭讀（可能還沒更新的）播放頭
                var startX = 0f
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        latestDragStart()
                        startX = latestGeometry.xDp(latestPlayhead)
                        total = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        total += amount / density
                        latestSeek(latestGeometry.msAt(startX - total))
                    },
                )
            },
    ) {
        val widthDp = maxWidth.value
        // 內容座標 → 畫面座標：播放頭所在的內容座標要對到正中間
        val scrollDp = geometry.xDp(playheadMs)
        val originDp = widthDp / 2f - scrollDp
        val first = geometry.firstEndingAfter(scrollDp - widthDp / 2f)
        val last = geometry.lastStartingBefore(scrollDp + widthDp / 2f)
        Layout(
            content = {
                for (index in first..last) {
                    key(index) {
                        val source = sourceById[timeline.segments[index].sourceId]
                        val leftDp = originDp + geometry.leftOf(index)
                        val segWidthDp = geometry.widthOf(index)
                        SegmentCell(
                            index = index,
                            source = source,
                            lengthMs = timeline.segments[index].lengthMs,
                            visibleFromDp = (-leftDp).coerceAtLeast(0f),
                            visibleToDp = (widthDp - leftDp).coerceAtMost(segWidthDp),
                            isSelected = selected == index,
                            thumbLoader = thumbLoader,
                            onClick = { onSelect(if (selected == index) null else index) },
                        )
                    }
                }
                // 播放頭：固定在正中間，不跟著捲動；純裝飾，位置由時間顯示唸出
                Box(Modifier.fillMaxHeight().background(AppTheme.colors.accent))
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val height = constraints.maxHeight
            val segmentCount = measurables.size - 1
            val placed = List(segmentCount) { k ->
                val index = first + k
                val wPx = geometry.widthOf(index).dp.roundToPx().coerceAtLeast(1)
                measurables[k].measure(Constraints.fixed(wPx, height)) to (originDp + geometry.leftOf(index)).dp.roundToPx()
            }
            val lineWidth = 2.dp.roundToPx()
            val line = measurables[segmentCount].measure(Constraints.fixed(lineWidth, height))
            layout(constraints.maxWidth, height) {
                placed.forEach { (placeable, x) -> placeable.place(x, 0) }
                line.place((constraints.maxWidth - lineWidth) / 2, 0)
            }
        }
    }
}

@Composable
private fun SegmentCell(
    index: Int,
    source: ClipSource?,
    lengthMs: Long,
    visibleFromDp: Float,
    visibleToDp: Float,
    isSelected: Boolean,
    thumbLoader: MediaThumbLoader,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AppTheme.radii.sm / 2)
    val kindName = when (source?.kind) {
        SourceKind.VIDEO -> "影片"
        SourceKind.PHOTO -> "照片"
        null -> "素材"
    }
    val description = "第 ${index + 1} 段，$kindName，${formatDuration(lengthMs)}"
    Box(
        Modifier
            .fillMaxSize()
            .focusRing(shape)
            .clip(shape)
            .background(AppTheme.colors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = isSelected
            },
    ) {
        if (source != null) SegmentThumb(source, thumbLoader, visibleFromDp.dp, visibleToDp.dp)
        if (isSelected) {
            Box(Modifier.fillMaxSize().border(2.dp, AppTheme.colors.accent, shape))
        }
    }
}

/**
 * 一段的縮圖底：把素材縮圖裁成正方形，依段落高度一格一格往右鋪滿（長段落不會把一張圖拉到幾千 dp 寬）。
 * 只畫 [from]..[to]（段內座標）之間的格子，捲到畫面外的部分不畫。
 */
@Composable
private fun SegmentThumb(source: ClipSource, loader: MediaThumbLoader, from: Dp, to: Dp) {
    val thumb by produceState<ImageBitmap?>(loader.peek(source.id), source.id, loader) {
        if (value == null) value = loader.load(source.toMediaItem())
    }
    val bitmap = thumb ?: return
    Canvas(Modifier.fillMaxSize()) {
        val tile = size.height
        if (tile <= 0f) return@Canvas
        val side = minOf(bitmap.width, bitmap.height)
        val srcOffset = IntOffset((bitmap.width - side) / 2, (bitmap.height - side) / 2)
        val srcSize = IntSize(side, side)
        val first = floor(from.toPx() / tile).toInt().coerceAtLeast(0)
        val last = ceil(to.toPx() / tile).toInt()
        for (i in first until last) {
            val dstWidth = minOf(tile, size.width - i * tile)
            if (dstWidth <= 0f) break
            // 最後一格不夠寬時只畫左邊那一部分，不拉伸
            val srcWidth = (side * dstWidth / tile).roundToInt().coerceAtLeast(1)
            drawImage(
                bitmap,
                srcOffset = srcOffset,
                srcSize = IntSize(srcWidth, side),
                dstOffset = IntOffset((i * tile).roundToInt(), 0),
                dstSize = IntSize(dstWidth.roundToInt().coerceAtLeast(1), tile.roundToInt()),
            )
        }
    }
}
