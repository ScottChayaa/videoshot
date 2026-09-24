package com.xenyaa.videoshot.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.player.PlayerSurface
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.ui.thumb.labelOf

/**
 * 詳情頁（規格第六節）。保留底部導覽，由呼叫端疊在 `AppShell` 裡（比照資料夾頁）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    state: DetailViewModel.State,
    loader: ThumbLoader,
    onBack: () -> Unit,
    onPlayerReady: (Player) -> Unit,
    onPlayerReleased: () -> Unit,
    onRetryPlayer: () -> Unit,
    onFocus: (ShotRow) -> Unit,
    onEdit: (ShotRow) -> Unit,
    onContinueCapture: () -> Unit,
    onBatchEdit: () -> Unit,
    onDeleteVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {

        // 沒有 statusBarsPadding()——這支畫面掛在 AppShell 的 Scaffold 裡（比照 FolderScreen），
        // Scaffold 已經把系統列 inset 當 padding 傳進來了（見 AppRoot.kt 呼叫端），這裡再加
        // 一次會把狀態列的高度墊兩遍，標題上方多出一截真機才看得出來的空白（最終審查 Finding 4）
        Row(
            Modifier.fillMaxWidth().padding(AppTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape)) {
                Icon(VsIcons.Back, contentDescription = "返回", tint = AppTheme.colors.textDim)
            }
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = AppTheme.spacing.s2),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape)) {
                    Icon(VsIcons.More, contentDescription = "這支影片的更多操作", tint = AppTheme.colors.textDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("繼續取這支的圖") },
                        leadingIcon = { Icon(VsIcons.Plus, contentDescription = null) },
                        onClick = { menuOpen = false; onContinueCapture() },
                    )
                    DropdownMenuItem(
                        text = { Text("批次編輯圖資") },
                        leadingIcon = { Icon(VsIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onBatchEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("刪除整支收藏", color = AppTheme.colors.danger) },
                        leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                        onClick = { menuOpen = false; confirmingDelete = true },
                    )
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在載入…") }
        } else {
            if (state.error != null) {
                Text(
                    state.error,
                    color = AppTheme.colors.danger,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
                )
            }

            // 播放器區：影片播不了時用中性訊息取代，圖資與縮圖仍要看得到（規格第六節）
            when (val player = state.player) {
                is DetailViewModel.PlayerAvailability.Ready -> PlayerSurface(
                    videoId = state.videoId,
                    playableInEmbed = player.playableInEmbed,
                    onPlayerReady = onPlayerReady,
                    onPlayerReleased = onPlayerReleased,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                is DetailViewModel.PlayerAvailability.Unavailable -> Column(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(AppTheme.colors.surface2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        player.message,
                        color = AppTheme.colors.textDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(AppTheme.spacing.s4),
                    )
                    if (player.retryable) TextButton(onClick = onRetryPlayer) { Text("重試") }
                }
                DetailViewModel.PlayerAvailability.Loading ->
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(AppTheme.colors.surface2))
            }

            // 目前這一張的圖資（手冊 §七第一條）
            state.focused?.let { focused ->
                Column(Modifier.fillMaxWidth().padding(AppTheme.spacing.s4)) {
                    val hasDesc = !focused.description.isNullOrBlank()
                    Text(
                        if (hasDesc) focused.description!! else "還沒有描述",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasDesc) AppTheme.colors.text else AppTheme.colors.textFaint,
                    )
                    val chips = listOfNotNull(focused.place) + state.focusedTags
                    if (chips.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                            modifier = Modifier.padding(top = AppTheme.spacing.s2),
                        ) {
                            chips.forEach { chip -> AssistChip(onClick = {}, label = { Text(chip) }) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s2),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(focused.eventDate, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textFaint)
                        Text("影片 ${formatClock(focused.atSec)}", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textFaint)
                    }
                    TextButton(onClick = { onEdit(focused) }, modifier = Modifier.padding(top = AppTheme.spacing.s1)) {
                        Icon(VsIcons.Edit, contentDescription = null)
                        Text("編輯這張的圖資", modifier = Modifier.padding(start = AppTheme.spacing.s2))
                    }
                }
            }

            Text(
                "這支影片的收藏 (${state.shots.size})",
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.colors.text,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = AppTheme.spacing.s3),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
            ) {
                items(state.shots, key = { it.id }) { shot ->
                    DetailGridTile(
                        shot = shot,
                        loader = loader,
                        focused = shot.id == state.focusedShotId,
                        onFocus = { onFocus(shot) },
                        onEdit = { onEdit(shot) },
                    )
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("刪除整支收藏？") },
            text = {
                val manual = state.manualCount
                Text(
                    "將刪除這支影片的 ${state.shots.size} 張收藏" +
                        (if (manual > 0) "(含 $manual 張截圖)" else "") +
                        "，YouTube 原片不受影響。",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDeleteVideo() }) {
                    Text("刪除", color = AppTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } },
        )
    }
}

/**
 * 收藏網格的一格：點＝跳播、編輯鈕或長按＝就地編輯（手冊 §七第二條）。
 *
 * 編輯鈕是外層 [Box] 的手足，不是 `combinedClickable` 那個 [Box] 的子孫——
 * 理由同 `wizard/Step2GridScreen.kt` 的 ▶ 鈕（子孫的 `onClick` 會被 `combinedClickable` 合併掉）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailGridTile(
    shot: ShotRow,
    loader: ThumbLoader,
    focused: Boolean,
    onFocus: () -> Unit,
    onEdit: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(16f / 9f)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) AppTheme.colors.accent else Color.Transparent,
            )
            .clip(RoundedCornerShape(AppTheme.radii.sm)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onFocus, onLongClick = onEdit)
                .semantics { contentDescription = labelOf(shot) },
        ) {
            ThumbImage(shot = shot, loader = loader, modifier = Modifier.fillMaxSize())
        }
        IconButton(
            onClick = onEdit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(AppTheme.spacing.tap)
                .focusRing(CircleShape)
                .semantics { contentDescription = "編輯 ${formatClock(shot.atSec)} 的圖資" },
        ) {
            Icon(VsIcons.Edit, contentDescription = null, tint = AppTheme.colors.accentInk)
        }
    }
}
