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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.player.Player
import com.xenyaa.videoshot.player.PlayerSurface
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.ChipSize
import com.xenyaa.videoshot.ui.common.TopBarIconButton
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.TopBarTitle
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.ui.thumb.labelOf
import java.time.LocalDate
import java.time.format.DateTimeParseException

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
        VsTopBar(state.title, nav = TopBarNav.Back(onBack), titleStyle = TopBarTitle.Small) {
            Box {
                TopBarIconButton(VsIcons.More, "這支影片的更多操作", { menuOpen = true })
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
                    // 讀取失敗不是破壞性動作，不用紅色（紅色只留給刪除）
                    color = AppTheme.colors.warn,
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
                    if (player.retryable) VsButton("重試", onRetryPlayer, variant = ButtonVariant.Secondary)
                }
                DetailViewModel.PlayerAvailability.Loading ->
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(AppTheme.colors.surface2))
            }

            // 目前這一張的圖資（手冊 §七第一條；原型 `.clip-meta`）
            state.focused?.let { focused ->
                val shape = RoundedCornerShape(AppTheme.radii.md)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, top = AppTheme.spacing.s3, bottom = AppTheme.spacing.s4)
                        .clip(shape)
                        .background(AppTheme.colors.surface)
                        .border(1.dp, AppTheme.colors.border, shape)
                        .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                ) {
                    val hasDesc = !focused.description.isNullOrBlank()
                    Text(
                        if (hasDesc) focused.description!! else "還沒有描述",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (hasDesc) AppTheme.colors.text else AppTheme.colors.textFaint,
                    )
                    // 純顯示的小膠囊：沒有 onClick，也就不會有點擊語意（原本的 AssistChip(onClick = {}) 假裝可點）
                    if (focused.place != null || state.focusedTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                        ) {
                            focused.place?.let { VsTagChip(it, ChipKind.PLACE, size = ChipSize.Mini) }
                            state.focusedTags.forEach { tag ->
                                VsTagChip(tag, ChipKind.ofTagKind(state.focusedTagKinds[tag] ?: "other"), size = ChipSize.Mini)
                            }
                        }
                    }
                    Text(
                        "${chineseDate(focused.eventDate)} · 影片 ${formatClock(focused.atSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textDim,
                    )
                    VsButton(
                        "編輯這張的圖資", { onEdit(focused) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Secondary,
                        icon = VsIcons.Edit,
                    )
                }
            }

            // 區塊標題（原型 `.section h3`）：「(N)」同一行、小一階、淡一階，單一 Text 讓朗讀也是一句
            Text(
                buildAnnotatedString {
                    append("這支影片的收藏")
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Normal, color = AppTheme.colors.textDim)) {
                        append(" (${state.shots.size})")
                    }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.text,
                modifier = Modifier.padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, top = AppTheme.spacing.s2, bottom = AppTheme.spacing.s3),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s1),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
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
                VsButton("刪除", { confirmingDelete = false; onDeleteVideo() }, variant = ButtonVariant.Danger)
            },
            dismissButton = { VsButton("取消", { confirmingDelete = false }, variant = ButtonVariant.Quiet) },
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
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Box(
        Modifier
            .aspectRatio(16f / 9f)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(AppTheme.colors.surface2),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onFocus, onLongClick = onEdit)
                .semantics { contentDescription = labelOf(shot) },
        ) {
            ThumbImage(shot = shot, loader = loader, modifier = Modifier.fillMaxSize(), showTimeOnPlaceholder = false)
            Text(
                formatClock(shot.atSec),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(AppTheme.spacing.s1)
                    .clip(shape)
                    .background(AppTheme.colors.scrim)
                    .padding(horizontal = AppTheme.spacing.s1),
            )
        }
        // 聚焦那格的 3dp 內框（畫在縮圖上方，不吃點擊）
        if (focused) Box(Modifier.fillMaxSize().border(3.dp, AppTheme.colors.accent, shape))
        // 觸控 44dp，視覺是 28dp 的 scrim 方塊＋白色圖示——白色而不是 accentInk：
        // 色系的 accentInk 不保證是淺色，疊在縮圖上可能看不見（同 Lightbox／首頁的處理）
        IconButton(
            onClick = onEdit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(AppTheme.spacing.tap)
                .focusRing(shape)
                .semantics { contentDescription = "編輯 ${formatClock(shot.atSec)} 的圖資" },
        ) {
            Box(
                Modifier.size(28.dp).clip(shape).background(AppTheme.colors.scrim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VsIcons.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** 「2026-08-03」→「2026年8月3日」；解析不了就原樣顯示，不為了格式讓整頁失敗。 */
private fun chineseDate(iso: String): String = try {
    val d = LocalDate.parse(iso)
    "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
} catch (_: DateTimeParseException) {
    iso
}
