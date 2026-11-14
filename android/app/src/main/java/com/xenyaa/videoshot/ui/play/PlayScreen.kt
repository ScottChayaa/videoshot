package com.xenyaa.videoshot.ui.play

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.xenyaa.videoshot.player.LocalPlayerFullscreen
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.player.PlayerSurface
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.OnDarkColors

/**
 * 原片播放（規格第六節）：直式影片一進來就全螢幕、鎖直立；橫式影片維持直拿，影片寬滿 16:9 置中，
 * 正下方的【全螢幕】才轉橫向全螢幕（返回鍵或控制層的離開全螢幕鈕回直拿）。
 * 上緣是【←】＋影片標題＋⋯（【繼續取圖】、【重新取圖】）：全螢幕時跟控制層一起出現、一起收起，直拿時一直顯示。
 * 返回（【←】或系統返回）回到 Lightbox，由呼叫端處理。
 */
@Composable
fun PlayScreen(
    state: PlayViewModel.State,
    onBack: () -> Unit,
    onPlayerReady: (Player) -> Unit,
    onPlayerReleased: () -> Unit,
    onRetryPlayer: () -> Unit,
    onContinueCapture: () -> Unit,
    onRecapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullscreen = LocalPlayerFullscreen.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    // 影片是不是橫的：先照縮圖比例猜（讀起來只要幾毫秒），播放器讀到實際寬高後以它為準
    var actualLandscape by remember { mutableStateOf<Boolean?>(null) }
    val landscape = actualLandscape ?: state.landscape
    // 直的進全螢幕；橫的維持直拿，猜成直的才要退回來
    LaunchedEffect(landscape) {
        when (landscape) {
            false -> fullscreen.enter(false)
            true -> if (fullscreen.active && !fullscreen.landscape) fullscreen.exit()
            null -> Unit
        }
    }
    val landscapeFullscreen = fullscreen.active && fullscreen.landscape
    // 橫向全螢幕時返回只回直拿；比呼叫端的返回晚註冊，所以優先
    BackHandler(enabled = landscapeFullscreen) { fullscreen.exit() }
    val inline = landscape == true && !fullscreen.active
    // 直拿時系統列照常顯示，黑底上要換白圖示；離開或進全螢幕時還原（全螢幕由 FullscreenSystemEffect 管）
    val view = LocalView.current
    DisposableEffect(inline) {
        val window = (view.context as? Activity)?.window
        if (!inline || window == null) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    // 播放器沒出現（影片播不了）時 PlayerSurface 不在，這裡自己負責離開時轉回直立
    DisposableEffect(Unit) { onDispose { fullscreen.exit() } }

    val topBar: @Composable () -> Unit = {
        PlayTopBar(
            title = state.title,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onBack = onBack,
            onContinueCapture = { menuOpen = false; onContinueCapture() },
            onRecapture = { menuOpen = false; confirming = true },
        )
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        when (val player = state.player) {
            is PlayViewModel.PlayerAvailability.Ready -> {
                // 換直拿／全螢幕只換 Modifier，播放器在組合裡的位置不變，WebView 才不會重載
                PlayerSurface(
                    videoId = state.videoId,
                    playableInEmbed = player.playableInEmbed,
                    durationSec = player.durationSec,
                    preview = player.preview ?: { null },
                    onPlayerReady = onPlayerReady,
                    onPlayerReleased = onPlayerReleased,
                    modifier = if (inline) Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f)
                    else Modifier.fillMaxSize(),
                    onVideoLandscape = { actualLandscape = it },
                    showFullscreenButton = landscapeFullscreen,
                    topBar = if (inline) null else topBar,
                    holdControls = menuOpen,
                )
                if (inline) {
                    Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) { topBar() }
                    RotateButton(
                        onClick = { fullscreen.enter(true) },
                        modifier = Modifier.align(Alignment.Center)
                            .offset(y = maxWidth * 9f / 32f + ROTATE_BUTTON_GAP + ROTATE_BUTTON_HEIGHT / 2),
                    )
                }
            }
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

/** 橫的影片直拿時，影片正下方的【全螢幕】：按下轉橫向全螢幕。白字、半透明白底的膠囊。 */
@Composable
private fun RotateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(OnDarkColors.fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AppTheme.spacing.s4)
            .height(ROTATE_BUTTON_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VsIcons.SmartphoneLandscape, contentDescription = null, tint = OnDarkColors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AppTheme.spacing.s2))
        Text("全螢幕", color = OnDarkColors.primary, style = MaterialTheme.typography.labelLarge)
    }
}

private val ROTATE_BUTTON_HEIGHT = 40.dp
private val ROTATE_BUTTON_GAP = 24.dp

/** 上緣一列：【←】、影片標題（一行、太長截斷）、⋯。白字，底下是控制層的半透明黑。 */
@Composable
private fun PlayTopBar(
    title: String,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinueCapture: () -> Unit,
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
                    text = { Text("繼續取圖") },
                    leadingIcon = { Icon(VsIcons.CopyPlus, contentDescription = null) },
                    onClick = onContinueCapture,
                )
                DropdownMenuItem(
                    text = { Text("重新取圖") },
                    leadingIcon = { Icon(VsIcons.RotateCcw, contentDescription = null) },
                    onClick = onRecapture,
                )
            }
        }
    }
}
