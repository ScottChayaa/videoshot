package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.ui.common.SelectionBarAction
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsIconButton
import com.xenyaa.videoshot.ui.common.VsSelectionBar
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.ClipColors
import com.xenyaa.videoshot.ui.theme.OnDarkColors

/**
 * 剪輯的編輯畫面：頂欄（標題、總長、導出）、預覽、播放列、時間軸條、工具列。
 *
 * 只吃狀態與回呼，自己不載入草稿也不碰播放器的控制：按鈕一律回報給呼叫端，
 * 由呼叫端決定要不要同時動 [preview]。畫面自己做的只有兩件事：
 * - 播放中，播放列與時間軸跟著 [ClipPreview.positionMs]，不是 [ClipEditorState.playheadMs]；
 *   停下來（暫停或播完）時用 [onSeek] 把停下的位置寫回播放頭。
 * - 使用者開始拖時間軸、或播放中按【分割】【刪除】復原重做時，先 [ClipPreview.pause]，
 *   並先用 [onSeek] 把播放頭接到目前的播放位置（編輯才會作用在畫面上看到的位置）。播放中【分割】能不能按，看畫面顯示的位置。
 *
 * 權限（[ClipEditorState.access]）不是完整也不是部分時整片換成選素材同一個權限空狀態；
 * 請求權限與前往設定的接線同選素材（[canAskAgain] 為 false 時按鈕是【前往設定】）。
 */
@Composable
fun ClipEditorScreen(
    state: ClipEditorState,
    preview: ClipPreview,
    thumbLoader: MediaThumbLoader,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelect: (Int?) -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    canAskAgain: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        val draft = state.draft
        if (draft == null) {
            // 還在讀，或沒有草稿（呼叫端會退回剪輯頁）：只畫頂欄
            VsTopBar("編輯", nav = TopBarNav.Back(onBack))
            return@Column
        }
        if (state.access != MediaAccess.FULL && state.access != MediaAccess.PARTIAL) {
            VsTopBar("編輯", nav = TopBarNav.Back(onBack))
            MediaAccessDenied(canAskAgain, onRequestAccess, onOpenSettings)
            return@Column
        }
        EditorBody(
            state, draft, preview, thumbLoader, onBack, onExport, onPlayPause, onSeek, onSelect, onSplit, onDelete, onUndo, onRedo,
        )
    }
}

/** 播放列（播放、復原、重做）的按鈕大小。 */
private val PlayBarButtonSize = 36.dp

@Composable
private fun ColumnScope.EditorBody(
    state: ClipEditorState,
    draft: ClipDraft,
    preview: ClipPreview,
    thumbLoader: MediaThumbLoader,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelect: (Int?) -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val previewPosition by preview.positionMs.collectAsState()
    val playing by preview.isPlaying.collectAsState()
    val failed by preview.failed.collectAsState()
    val durationMs = draft.timeline.durationMs
    // 停下（暫停、播完）的那一次組合，播放頭還沒寫回、停在按播放那一刻：沿用預覽的位置，時間軸才不會先跳回去再跳回來
    val sync = remember { PlaybackSync(playing) }
    val shownMs = (if (playing || sync.wasPlaying) previewPosition else state.playheadMs).coerceIn(0L, durationMs)

    // 停下時把位置寫回播放頭。拖曳開始時的暫停已經先寫過了，不再重複（否則可能蓋掉拖到的新位置）
    val latestSeek by rememberUpdatedState(onSeek)
    LaunchedEffect(playing) {
        if (sync.wasPlaying && !playing) {
            if (sync.suppress) sync.suppress = false else latestSeek(preview.positionMs.value)
        }
        sync.wasPlaying = playing
    }

    // 播放中要動手編輯（拖時間軸、分割、刪除、復原、重做）：先停下，並把畫面上的播放位置寫回播放頭。
    // 播放時 VM 的播放頭還停在按下播放那一刻，不先寫回，編輯會作用在錯的位置。
    // 暫停造成的「停下寫回」不必再做一次（否則可能蓋掉編輯後的新位置），所以先標記略過
    val stopPlayback: () -> Unit = {
        if (preview.isPlaying.value) {
            sync.suppress = true
            onSeek(preview.positionMs.value)
            preview.pause()
        }
    }
    // 時間軸比例是 [TIMELINE_ZOOM_LEVELS] 的第幾格；只是看的方式，不存進草稿
    var zoom by rememberSaveable { mutableIntStateOf(TIMELINE_ZOOM_LEVELS.indexOf(DP_PER_SECOND)) }
    VsTopBar("編輯", nav = TopBarNav.Back(onBack), actions = { VsButton("導出", onExport, icon = VsIcons.Upload, enabled = !state.isEmpty, compact = true) })

    Box(Modifier.weight(1f).fillMaxWidth().background(ClipColors.preview), contentAlignment = Alignment.Center) {
        ClipPreviewSurface(preview, draft.orientation, showVideo = !state.isEmpty)
        if (failed) {
            // 停住時畫面留著最後一格，字要墊一層半透明黑底才看得清楚
            Text(
                "預覽出錯，按播放重試",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkColors.primary,
                modifier = Modifier
                    .background(OnDarkColors.hint, RoundedCornerShape(AppTheme.radii.sm))
                    .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s2),
            )
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
    ) {
        // 播放列的三顆比一般圖示按鈕（44dp）小一號，讓預覽多一點高度（scott 2026-11-10）
        val small = Modifier.size(PlayBarButtonSize)
        VsIconButton(
            icon = if (playing) VsIcons.Pause else VsIcons.Play,
            contentDescription = if (playing) "暫停" else "播放",
            onClick = onPlayPause,
            modifier = small,
            enabled = !state.isEmpty,
        )
        Text(
            "${formatDuration(shownMs)} / ${formatDuration(durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            // 總長超過方向的上限（直式 3 分鐘、橫式 10 分鐘）時變紅
            color = if (state.overLimit) AppTheme.colors.danger else AppTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        VsIconButton(VsIcons.Undo, "復原", { stopPlayback(); onUndo() }, small, enabled = state.canUndo)
        VsIconButton(VsIcons.Redo, "重做", { stopPlayback(); onRedo() }, small, enabled = state.canRedo)
    }

    // 時間軸用深色底（跟一般剪輯軟體一樣）
    Box(Modifier.fillMaxWidth().background(ClipColors.timeline).padding(vertical = AppTheme.spacing.s2)) {
        if (state.isEmpty) {
            EmptyTimeline(state.canUndo)
        } else {
            ClipTimelineStrip(
                dpPerSecond = TIMELINE_ZOOM_LEVELS[zoom],
                timeline = draft.timeline,
                sources = draft.sources,
                playheadMs = shownMs,
                selected = state.selected,
                thumbLoader = thumbLoader,
                onSeek = onSeek,
                onSelect = onSelect,
                onDragStart = stopPlayback,
            )
        }
    }

    // 底部列圖示在上、文字在下（同多選的動作列）；放大、縮小只換時間軸比例，不算編輯、不停播放
    VsSelectionBar(
        listOf(
            SelectionBarAction(VsIcons.ZoomIn, "放大", { zoom++ }, enabled = !state.isEmpty && zoom < TIMELINE_ZOOM_LEVELS.lastIndex),
            SelectionBarAction(VsIcons.ZoomOut, "縮小", { zoom-- }, enabled = !state.isEmpty && zoom > 0),
            SelectionBarAction(
                VsIcons.Split,
                "分割",
                { stopPlayback(); onSplit() },
                // 播放中，播放頭停在舊位置：能不能分割要看畫面上顯示的位置
                enabled = if (playing) draft.timeline.canSplitAt(shownMs) else state.canSplit,
            ),
            SelectionBarAction(VsIcons.Trash, "刪除", { stopPlayback(); onDelete() }, enabled = state.canDelete),
        ),
    )
}

/**
 * 時間軸空掉時，在時間軸的位置（深色底）顯示的說明。跟時間軸一樣高，預覽區才不會跟著縮小、版面不跳。
 * 復原提示裡畫跟【復原】按鈕一樣的圖示（字元 ↶ 在手機字型裡太小、不像按鈕）。
 */
@Composable
private fun EmptyTimeline(canUndo: Boolean) {
    Column(
        Modifier.fillMaxWidth().height(RulerHeight + StripHeight),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("時間軸是空的", style = MaterialTheme.typography.bodyLarge, color = OnDarkColors.secondary)
        // 修剪掉全部素材時復原紀錄已清空，這句提示就不成立了
        if (canUndo) {
            Text(
                buildAnnotatedString {
                    append("按 ")
                    appendInlineContent(UNDO_ICON, "↶")
                    append(" 復原可以找回剛刪掉的段落")
                },
                inlineContent = mapOf(
                    UNDO_ICON to InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
                        Icon(VsIcons.Undo, null, Modifier.fillMaxSize(), tint = OnDarkColors.tertiary)
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkColors.tertiary,
            )
        }
    }
}

private const val UNDO_ICON = "undo"

/** [ClipEditorScreen] 的播放狀態同步用的小記事本：不需要觸發重組，所以不是 Compose 狀態。 */
private class PlaybackSync(var wasPlaying: Boolean, var suppress: Boolean = false)
