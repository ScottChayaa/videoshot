package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.OnDarkColors
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 片段播放中點一下叫出的兩顆按鈕（規格第六節「自動播放片段」）：右下角【聲音】【全螢幕】。
 * 不像詳情頁的控制層把整個畫面調暗——片段只有 3 秒，調暗等於一直擋著畫面。
 *
 * 只佔按鈕本身的範圍：疊在 pager 上面也不會擋到上下滑。
 */
@Composable
internal fun ClipButtons(
    visible: Boolean,
    muted: Boolean,
    fullscreen: Boolean,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
            RoundButton(
                if (muted) VsIcons.VolumeOff else VsIcons.Volume,
                if (muted) "打開聲音" else "關掉聲音",
                onToggleMute,
            )
            RoundButton(
                if (fullscreen) VsIcons.Minimize else VsIcons.Maximize,
                if (fullscreen) "離開全螢幕" else "全螢幕",
                onToggleFullscreen,
            )
        }
    }
}

/** 44dp 圓形、半透明黑底、白色圖示：疊在任何畫面上都看得清楚。 */
@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(AppTheme.spacing.tap)
            .clip(CircleShape)
            .background(OnDarkColors.hint)
            .focusRing(CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = OnDarkColors.primary, modifier = Modifier.size(20.dp))
    }
}
