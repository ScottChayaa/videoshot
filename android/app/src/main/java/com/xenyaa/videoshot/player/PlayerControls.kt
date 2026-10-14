package com.xenyaa.videoshot.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.player.scrubTargetSec
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.OnDarkColors
import kotlinx.coroutines.delay

/** 播放中控制層沒人碰多久自己收起（規格第六節「播放器」）。 */
const val CONTROLS_AUTO_HIDE_MS = 3_000L

/** 中央播放／暫停按鈕的觸控半徑（64dp 見方的圓）。 */
private val CENTER_BUTTON_RADIUS = 32.dp

/** 點兩下時中央閃一下的圖示停多久。 */
private const val FLASH_MS = 600L

/** TalkBack 一次前後跳幾秒。 */
private const val A11Y_STEP_SEC = 10.0

internal const val PLAYER_GESTURE_TAG = "player-gestures"
internal const val PREVIEW_BUBBLE_TAG = "player-preview"

/**
 * 蓋在影片上的控制層（規格第六節「播放器」）。只管畫面與手勢，不碰 WebView：
 * 狀態從 [status] 進來，動作從回呼出去，所以可以在 Robolectric 裡單獨測。
 *
 * @param fallbackDurationSec `<video>.duration` 還沒有值（沒開播）時用 watch page 的總長
 * @param enabled 播放器還沒載好時 false：手勢都不作用、控制層不出現
 * @param preview 第 N 秒的預覽圖；拿不到回 null，預覽泡泡只顯示時間
 */
@Composable
fun PlayerControls(
    status: PlaybackStatus,
    fallbackDurationSec: Double,
    enabled: Boolean,
    fullscreen: Boolean,
    preview: suspend (Double) -> ImageBitmap?,
    onTogglePlay: () -> Unit,
    onSeek: (Double) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = status.durationSec.takeIf { it > 0 } ?: fallbackDurationSec
    var visible by remember { mutableStateOf(false) }
    // 每次有操作就 +1，讓自動收起的計時重來
    var touches by remember { mutableIntStateOf(0) }
    var dragSec by remember { mutableStateOf<Double?>(null) }
    var flash by remember { mutableStateOf<Boolean?>(null) }   // true＝播放圖示、false＝暫停圖示

    val latestStatus by rememberUpdatedState(status)
    val latestDuration by rememberUpdatedState(duration)
    val latestEnabled by rememberUpdatedState(enabled)
    // pointerInput(Unit) 不會重建，回呼要用最新的版本
    val latestOnTogglePlay by rememberUpdatedState(onTogglePlay)
    val latestOnSeek by rememberUpdatedState(onSeek)

    // 播放中、沒在拖、3 秒沒人碰 → 收起；暫停中一直留著
    LaunchedEffect(visible, status.paused, touches, dragSec == null) {
        if (visible && !status.paused && dragSec == null) {
            delay(CONTROLS_AUTO_HIDE_MS)
            visible = false
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) { delay(FLASH_MS); flash = null }
    }

    val position = dragSec ?: status.currentSec
    val fraction = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f

    BoxWithConstraints(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag(PLAYER_GESTURE_TAG)
                .semantics {
                    contentDescription = "影片"
                    onClick(label = if (visible) "隱藏控制項" else "顯示控制項") {
                        if (latestEnabled) visible = !visible
                        latestEnabled
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (latestEnabled) {
                                // 中央按鈕不攔觸控（否則點兩下會被它吃成兩次單擊）：控制層開著、點在它的範圍內就由這裡切換播放
                                val centerHit = visible &&
                                    (offset - Offset(size.width / 2f, size.height / 2f)).getDistance() <= CENTER_BUTTON_RADIUS.toPx()
                                if (centerHit) latestOnTogglePlay() else visible = !visible
                                touches++
                            }
                        },
                        onDoubleTap = {
                            if (latestEnabled) {
                                flash = latestStatus.paused   // 原本暫停 → 要播 → 閃播放圖示
                                latestOnTogglePlay()
                                touches++
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var startSec = 0.0
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (latestEnabled && latestDuration > 0) {
                                startSec = latestStatus.currentSec
                                dx = 0f
                                dragSec = startSec
                                visible = true
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            if (dragSec != null) {
                                change.consume()
                                dx += amount
                                dragSec = scrubTargetSec(startSec, dx, size.width.toFloat(), latestDuration)
                            }
                        },
                        onDragEnd = {
                            dragSec?.let(latestOnSeek)
                            dragSec = null
                            touches++
                        },
                        onDragCancel = { dragSec = null },
                    )
                },
        )

        // 半透明底＋中央按鈕＋全螢幕鈕
        AnimatedVisibility(visible && enabled, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                if (dragSec == null) {
                    // 只畫圖示、不攔觸控；點擊由手勢框的 onTap 依位置判斷，TalkBack 走這裡的語意動作
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(CENTER_BUTTON_RADIUS * 2)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = if (status.paused) "播放" else "暫停"
                                onClick { onTogglePlay(); touches++; true }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (status.paused) VsIcons.Play else VsIcons.Pause,
                            contentDescription = null,
                            tint = OnDarkColors.primary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { onToggleFullscreen(); touches++ },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp),
                ) {
                    Icon(
                        if (fullscreen) VsIcons.Minimize else VsIcons.Maximize,
                        contentDescription = if (fullscreen) "離開全螢幕" else "全螢幕",
                        tint = OnDarkColors.primary,
                    )
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 22.dp),
                ) {
                    Text(
                        "${formatClock(position)} / ${formatClock(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkColors.primary,
                    )
                }
            }
        }

        // 點兩下時中央閃一下
        flash?.let { playing ->
            Icon(
                if (playing) VsIcons.Play else VsIcons.Pause,
                contentDescription = null,
                tint = OnDarkColors.primary,
                modifier = Modifier.align(Alignment.Center).size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(8.dp),
            )
        }

        // 拖曳預覽泡泡：跟著進度條位置走，夾在畫面內
        dragSec?.let { sec ->
            val bubbleWidth = 160.dp
            val maxOffset = maxWidth - bubbleWidth
            val x = (maxWidth * fraction - bubbleWidth / 2).coerceIn(0.dp, maxOffset.coerceAtLeast(0.dp))
            PreviewBubble(sec, preview, Modifier.align(Alignment.BottomStart).offset(x = x, y = (-28).dp).width(bubbleWidth))
        }

        ProgressBar(
            fraction = fraction,
            thick = visible && enabled,
            duration = duration,
            position = position,
            onSeek = { onSeek(it); touches++ },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

/** 下緣進度條：收起時 2dp 細線；控制層打開時 4dp＋圓形把手（規格第六節）。宣告成 TalkBack 滑桿。 */
@Composable
private fun ProgressBar(
    fraction: Float,
    thick: Boolean,
    duration: Double,
    position: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barHeight: Dp = if (thick) 4.dp else 2.dp
    BoxWithConstraints(
        modifier
            .height(if (thick) 16.dp else 2.dp)
            .semantics {
                contentDescription = "播放進度"
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..duration.toFloat().coerceAtLeast(0f))
                if (duration > 0) {
                    setProgress { target -> onSeek(target.toDouble().coerceIn(0.0, duration)); true }
                    customActions = listOf(
                        CustomAccessibilityAction("往後 10 秒") { onSeek((position + A11Y_STEP_SEC).coerceAtMost(duration)); true },
                        CustomAccessibilityAction("往前 10 秒") { onSeek((position - A11Y_STEP_SEC).coerceAtLeast(0.0)); true },
                    )
                }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(Modifier.fillMaxWidth().height(barHeight).background(OnDarkColors.tertiary.copy(alpha = 0.4f)))
        Box(Modifier.fillMaxWidth(fraction).height(barHeight).background(AppTheme.colors.accent))
        if (thick) {
            Box(
                Modifier
                    .offset(x = maxWidth * fraction - 6.dp, y = 4.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.accent),
            )
        }
    }
}

/** 預覽縮圖＋時間。圖還沒到（詳情頁要下載）或沒有 storyboard 時只顯示時間。 */
@Composable
private fun PreviewBubble(sec: Double, preview: suspend (Double) -> ImageBitmap?, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, sec) { value = preview(sec) }
    Column(
        modifier.testTag(PREVIEW_BUBBLE_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        image?.let {
            Image(
                it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
        Text(
            formatClock(sec),
            style = MaterialTheme.typography.labelMedium,
            color = OnDarkColors.primary,
            modifier = Modifier.padding(top = 4.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
