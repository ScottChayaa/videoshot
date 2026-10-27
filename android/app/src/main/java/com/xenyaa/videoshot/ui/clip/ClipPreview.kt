package com.xenyaa.videoshot.ui.clip

import com.xenyaa.videoshot.core.timeline.ClipDraft
import kotlinx.coroutines.flow.StateFlow

/**
 * 編輯畫面的預覽播放器。介面本身不碰 Media3：實作（`Media3ClipPreview`）才把草稿轉成
 * 合成來播，測試用假實作，所以 ViewModel 與畫面測試不必真的建播放器。
 *
 * 時間一律是時間軸上的毫秒。
 */
interface ClipPreview {
    /** 載入 [draft] 的目前時間軸並停在 [positionMs]（不自動播放）。時間軸變動後要再呼叫一次。 */
    fun show(draft: ClipDraft, positionMs: Long)

    fun seekTo(positionMs: Long)
    fun play()
    fun pause()

    /** 目前的播放位置（時間軸毫秒）。 */
    val positionMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>

    /** 播放出錯、停住了（讀不到素材等）。下次 [play] 會重試；[show] 也會清掉。 */
    val failed: StateFlow<Boolean>

    fun release()
}
