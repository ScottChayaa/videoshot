package com.xenyaa.videoshot.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.player.LocalPlayerFullscreen
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.player.PlayerSurface
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.OnDarkColors

/**
 * 原片播放（規格第六節）：一進來就是全螢幕，直式影片鎖直立、橫式鎖橫向。
 * 上緣是【←】＋影片標題＋⋯（只有【重新取圖】），跟控制層一起出現、一起收起；沒有全螢幕鈕。
 * 返回（【←】或系統返回）回到 Lightbox，由呼叫端處理。
 */
@Composable
fun PlayScreen(
    state: PlayViewModel.State,
    onBack: () -> Unit,
    onPlayerReady: (Player) -> Unit,
    onPlayerReleased: () -> Unit,
    onRetryPlayer: () -> Unit,
    onRecapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullscreen = LocalPlayerFullscreen.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    // 猜到方向才進全螢幕（縮圖讀起來只要幾毫秒），免得先轉橫再轉回直；之後播放器讀到實際寬高會再校正
    LaunchedEffect(state.landscape) { state.landscape?.let { fullscreen.enter(it) } }
    // 播放器沒出現（影片播不了）時 PlayerSurface 不在，這裡自己負責離開時轉回直立
    DisposableEffect(Unit) { onDispose { fullscreen.exit() } }

    val topBar: @Composable () -> Unit = {
        PlayTopBar(
            title = state.title,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onBack = onBack,
            onRecapture = { menuOpen = false; confirming = true },
        )
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (val player = state.player) {
            is PlayViewModel.PlayerAvailability.Ready -> PlayerSurface(
                videoId = state.videoId,
                playableInEmbed = player.playableInEmbed,
                durationSec = player.durationSec,
                preview = player.preview ?: { null },
                onPlayerReady = onPlayerReady,
                onPlayerReleased = onPlayerReleased,
                modifier = Modifier.fillMaxSize(),
                lockedFullscreen = true,
                topBar = topBar,
                holdControls = menuOpen,
            )
            is PlayViewModel.PlayerAvailability.Unavailable -> {
                Column(
                    Modifier.align(Alignment.Center).padding(AppTheme.spacing.s4),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
                ) {
                    Text(player.message, color = OnDarkColors.secondary, style = MaterialTheme.typography.bodyMedium)
                    if (player.retryable) VsButton("重試", onRetryPlayer, variant = ButtonVariant.Secondary)
                }
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) { topBar() }
            }
            PlayViewModel.PlayerAvailability.Loading ->
                Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) { topBar() }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("重新取圖？") },
            text = {
                Text(
                    "將刪除這支影片已收藏的 ${state.shotCount} 張" +
                        (if (state.manualCount > 0) "（含 ${state.manualCount} 張截圖）" else "") +
                        "，再從第二步重新挑畫面。",
                )
            },
            confirmButton = {
                VsButton("重新取圖", { confirming = false; onRecapture() }, variant = ButtonVariant.Danger)
            },
            dismissButton = { VsButton("取消", { confirming = false }, variant = ButtonVariant.Quiet) },
        )
    }
}

/** 上緣一列：【←】、影片標題（一行、太長截斷）、⋯。白字，底下是控制層的半透明黑。 */
@Composable
private fun PlayTopBar(
    title: String,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRecapture: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = AppTheme.spacing.s1, vertical = AppTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(VsIcons.Back, contentDescription = "返回", tint = OnDarkColors.primary)
        }
        Text(
            title,
            color = OnDarkColors.primary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(VsIcons.More, contentDescription = "這支影片的更多操作", tint = OnDarkColors.primary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text("重新取圖") },
                    leadingIcon = { Icon(VsIcons.RotateCcw, contentDescription = null) },
                    onClick = onRecapture,
                )
            }
        }
    }
}
