package com.xenyaa.videoshot.ui.clip

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.OnDarkColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * 成品播放器。介面本身不碰 Media3：實作（[Media3ClipResultPlayback]）才建 ExoPlayer，
 * 畫面測試用假實作，不必在 Robolectric 裡真的解碼影片。
 */
interface ClipResultPlayback {
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>

    /** 成品長度；還沒讀到是 0。 */
    val durationMs: StateFlow<Long>

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

/**
 * 用 ExoPlayer 播 [source]（檔案路徑或 `content://` 位址），建好就自動播放。要在主執行緒建立與呼叫。
 * 播放位置沒有事件可以聽：播放中每 [POLL_MS] 毫秒讀一次 `currentPosition`。
 */
@OptIn(UnstableApi::class)
class Media3ClipResultPlayback(context: Context, source: String) : ClipResultPlayback {

    internal val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            _positionMs.value = player.currentPosition
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                handler.removeCallbacks(poll)
                if (isPlaying) handler.post(poll) else _positionMs.value = player.currentPosition
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = player.duration
                if (d != C.TIME_UNSET) _durationMs.value = d
            }
        })
        val uri = if (source.startsWith("content:")) Uri.parse(source) else Uri.fromFile(File(source))
        player.setMediaItem(MediaItem.fromUri(uri))
        player.playWhenReady = true
        player.prepare()
    }

    override fun play() {
        // 播完停在尾端，再按播放從頭開始
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    override fun release() {
        handler.removeCallbacks(poll)
        player.release()
    }

    private companion object {
        const val POLL_MS = 200L
    }
}

/**
 * 導覽用的入口：查出任務，找到成品（存進相簿就是 [EditTask.galleryUri]，否則是 app 裡的 `edits/{id}/out.mp4`）再交給 [ClipResultScreen]。
 * 還沒查到（或任務已經不在）時只有黑底與返回鈕。
 */
@Composable
fun ClipResultRoute(taskId: Long, cacheRepo: CacheRepo, onBack: () -> Unit) {
    val context = LocalContext.current
    val source by produceState<String?>(null, taskId) {
        value = try {
            cacheRepo.editTask(taskId).first()?.let { it.galleryUri ?: File(context.filesDir, "edits/$taskId/out.mp4").path }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VsClip", "讀取輸出任務失敗", e)
            null
        }
    }
    val src = source
    if (src != null) {
        ClipResultScreen(src, onBack)
    } else {
        BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                Icon(VsIcons.Back, contentDescription = "返回", tint = OnDarkColors.primary)
            }
        }
    }
}

/**
 * 全螢幕播放剪輯成品（階段 20）：黑底、進來自動播放，下方播放／暫停鈕與可拖的進度條；返回鍵回上一頁並釋放播放器。
 * [source] 是 app 裡的成品檔路徑，或存進相簿之後的 content URI。
 */
@OptIn(UnstableApi::class)
@Composable
fun ClipResultScreen(source: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val playback = remember(source) { Media3ClipResultPlayback(context, source) }
    ClipResultContent(
        playback = playback,
        onBack = onBack,
        surface = { modifier ->
            ContentFrame(
                player = playback.player,
                modifier = modifier,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                contentScale = ContentScale.Fit,
            )
        },
    )
}

/** [ClipResultScreen] 的畫面本體。[surface] 畫影片（測試傳空的）；離開組合時釋放 [playback]，退到背景時暫停。 */
@Composable
internal fun ClipResultContent(
    playback: ClipResultPlayback,
    onBack: () -> Unit,
    surface: @Composable (Modifier) -> Unit,
) {
    DisposableEffect(playback) { onDispose { playback.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.pause() }
    BackHandler(onBack = onBack)

    val isPlaying by playback.isPlaying.collectAsStateWithLifecycle()
    val positionMs by playback.positionMs.collectAsStateWithLifecycle()
    val durationMs by playback.durationMs.collectAsStateWithLifecycle()
    // 拖動中以手指位置為準；放手送出跳轉後才回到跟著播放位置走
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        surface(Modifier.fillMaxSize())

        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(4.dp).align(Alignment.TopStart),
        ) {
            Icon(VsIcons.Back, contentDescription = "返回", tint = OnDarkColors.primary)
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (isPlaying) playback.pause() else playback.play() },
                modifier = Modifier.size(48.dp).background(OnDarkColors.hint, CircleShape),
            ) {
                Icon(
                    if (isPlaying) VsIcons.Pause else VsIcons.Play,
                    contentDescription = if (isPlaying) "暫停" else "播放",
                    tint = OnDarkColors.primary,
                )
            }
            Text(
                formatDuration(dragging?.let { (it * durationMs).toLong() } ?: positionMs),
                color = OnDarkColors.primary,
                modifier = Modifier.padding(start = 12.dp).width(40.dp),
            )
            Slider(
                value = fraction,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { playback.seekTo((it * durationMs).toLong()) }
                    dragging = null
                },
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(
                    thumbColor = OnDarkColors.primary,
                    activeTrackColor = OnDarkColors.primary,
                    inactiveTrackColor = OnDarkColors.tertiary,
                    disabledThumbColor = OnDarkColors.tertiary,
                    disabledActiveTrackColor = OnDarkColors.tertiary,
                    disabledInactiveTrackColor = OnDarkColors.fill,
                ),
                modifier = Modifier.weight(1f).semantics { contentDescription = "播放進度" },
            )
            Text(formatDuration(durationMs), color = OnDarkColors.secondary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
