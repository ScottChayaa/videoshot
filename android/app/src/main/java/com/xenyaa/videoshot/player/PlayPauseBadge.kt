package com.xenyaa.videoshot.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.OnDarkColors

/** 中央播放／暫停圖示的直徑（觸控範圍同這個圓）。 */
internal val PLAY_PAUSE_BADGE_SIZE = 64.dp

/** 點兩下（或鍵盤切換）後中央圖示停多久。 */
internal const val PLAY_PAUSE_FLASH_MS = 600L

/**
 * 影片正中央的播放／暫停圖示，所有播放器共用一個：控制層的中央按鈕、點兩下的閃現（播放器與 Lightbox 片段）都畫這個，
 * 不會疊出兩個。圖示一律表示「按下去會做什麼」：暫停中是播放、播放中是暫停。
 *
 * 只畫圖示，不處理觸控與語意，由呼叫端決定。
 */
@Composable
internal fun PlayPauseBadge(paused: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(PLAY_PAUSE_BADGE_SIZE).background(OnDarkColors.hint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (paused) VsIcons.Play else VsIcons.Pause,
            contentDescription = null,
            tint = OnDarkColors.primary,
            modifier = Modifier.size(40.dp),
        )
    }
}
