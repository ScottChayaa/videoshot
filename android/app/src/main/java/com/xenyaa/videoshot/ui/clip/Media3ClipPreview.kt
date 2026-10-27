package com.xenyaa.videoshot.ui.clip

import android.content.Context
import android.view.Choreographer
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用 Media3 的 `CompositionPlayer` 預覽目前時間軸（規格第十九節）。要在主執行緒建立與呼叫。
 *
 * [show] 把草稿轉成合成（[clipComposition]）交給播放器並停在指定位置，不自動播放；
 * 時間軸變動後要再呼叫一次。時間軸是空的（合成為 null）就只停掉播放器、畫面留黑。
 * 播放位置沒有事件可以聽，所以播放中每一幀（`Choreographer`）讀一次 `currentPosition`，時間軸才會順順地走。
 */
@OptIn(UnstableApi::class)
class Media3ClipPreview(context: Context) : ClipPreview {

    internal val player: CompositionPlayer = CompositionPlayer.Builder(context.applicationContext).build()

    private var polling = false
    private val pollFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!polling) return
            _positionMs.value = player.currentPosition
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private var hasComposition = false

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPolling()
                } else {
                    stopPolling()
                    // 停下（暫停或播完）的那一刻補讀一次，位置才會停在真正停下的地方
                    _positionMs.value = player.currentPosition
                }
            }
        })
    }

    override fun show(draft: ClipDraft, positionMs: Long) {
        player.pause()
        val composition = clipComposition(draft)
        if (composition == null) {
            if (hasComposition) player.stop()
            hasComposition = false
            _positionMs.value = 0
            return
        }
        player.setComposition(composition)
        player.seekTo(positionMs)
        player.prepare()
        hasComposition = true
        _positionMs.value = positionMs
    }

    override fun seekTo(positionMs: Long) {
        if (!hasComposition) return
        player.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    override fun play() {
        if (!hasComposition) return
        // 播完停在尾端，再按播放從頭開始
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun release() {
        stopPolling()
        player.release()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        Choreographer.getInstance().postFrameCallback(pollFrame)
    }

    private fun stopPolling() {
        polling = false
        Choreographer.getInstance().removeFrameCallback(pollFrame)
    }
}

/**
 * 預覽畫面：外框比例照輸出方向（直式 9:16、橫式 16:9），在可用空間裡盡量放大並置中，底色黑。
 * [preview] 是 [Media3ClipPreview] 時畫 `SurfaceView`（影片走硬體解碼，TextureView 在這條路上較吃力）；
 * 其他實作（測試用的假預覽）只畫黑底。
 * [showVideo] 為 false（時間軸是空的）時也只畫黑底：播放器停掉後 `SurfaceView` 會留著最後一格。
 */
@OptIn(UnstableApi::class)
@Composable
fun ClipPreviewSurface(preview: ClipPreview, orientation: ClipOrientation, modifier: Modifier = Modifier, showVideo: Boolean = true) {
    val portrait = orientation == ClipOrientation.PORTRAIT
    Box(
        modifier
            .aspectRatio(orientation.width.toFloat() / orientation.height, matchHeightConstraintsFirst = portrait)
            .background(Color.Black),
    ) {
        if (showVideo && preview is Media3ClipPreview) {
            PlayerSurface(preview.player, Modifier.fillMaxSize(), SURFACE_TYPE_SURFACE_VIEW)
        }
    }
}
