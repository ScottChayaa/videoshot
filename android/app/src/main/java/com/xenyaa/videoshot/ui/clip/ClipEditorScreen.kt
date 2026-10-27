package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsIconButton
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 剪輯的編輯畫面：頂欄（標題、總長、導出）、預覽、播放列、時間軸條、工具列。
 *
 * 只吃狀態與回呼，自己不載入草稿也不碰播放器的控制：按鈕一律回報給呼叫端，
 * 由呼叫端決定要不要同時動 [preview]。畫面自己做的只有兩件事：
 * - 播放中，播放列與時間軸跟著 [ClipPreview.positionMs]，不是 [ClipEditorState.playheadMs]；
 *   停下來（暫停或播完）時用 [onSeek] 把停下的位置寫回播放頭。
 * - 使用者開始拖時間軸時先 [ClipPreview.pause]，並先用 [onSeek] 把播放頭接到目前的播放位置。
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
    val durationMs = draft.timeline.durationMs
    val shownMs = (if (playing) previewPosition else state.playheadMs).coerceIn(0L, durationMs)

    // 停下時把位置寫回播放頭。拖曳開始時的暫停已經先寫過了，不再重複（否則可能蓋掉拖到的新位置）
    val sync = remember { PlaybackSync(playing) }
    val latestSeek by rememberUpdatedState(onSeek)
    LaunchedEffect(playing) {
        if (sync.wasPlaying && !playing) {
            if (sync.suppress) sync.suppress = false else latestSeek(preview.positionMs.value)
        }
        sync.wasPlaying = playing
    }

    val limitMinutes = draft.orientation.maxDurationMs / 60_000
    VsTopBar(
        "編輯",
        nav = TopBarNav.Back(onBack),
        subtitle = if (state.overLimit) "總長 ${formatDuration(durationMs)}（超過 $limitMinutes 分鐘上限）" else "總長 ${formatDuration(durationMs)}",
        subtitleColor = if (state.overLimit) AppTheme.colors.danger else AppTheme.colors.textDim,
        actions = { VsButton("導出", onExport, enabled = !state.isEmpty) },
    )

    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
        ClipPreviewSurface(preview, draft.orientation)
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
    ) {
        VsIconButton(
            icon = if (playing) VsIcons.Pause else VsIcons.Play,
            contentDescription = if (playing) "暫停" else "播放",
            onClick = onPlayPause,
            enabled = !state.isEmpty,
        )
        Text(
            "${formatDuration(shownMs)} / ${formatDuration(durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        VsIconButton(VsIcons.Undo, "復原", onUndo, enabled = state.canUndo)
        VsIconButton(VsIcons.Redo, "重做", onRedo, enabled = state.canRedo)
    }

    Box(Modifier.fillMaxWidth().heightIn(min = StripHeight).padding(bottom = AppTheme.spacing.s2)) {
        if (state.isEmpty) {
            VsEmptyState(
                message = "時間軸是空的",
                // 修剪掉全部素材時復原紀錄已清空，這句提示就不成立了
                secondary = if (state.canUndo) "按 ↶ 復原可以找回剛刪掉的段落" else null,
            )
        } else {
            ClipTimelineStrip(
                timeline = draft.timeline,
                sources = draft.sources,
                playheadMs = shownMs,
                selected = state.selected,
                thumbLoader = thumbLoader,
                onSeek = onSeek,
                onSelect = onSelect,
                onDragStart = {
                    if (preview.isPlaying.value) {
                        sync.suppress = true
                        onSeek(preview.positionMs.value)
                        preview.pause()
                    }
                },
            )
        }
    }

    VsActionDock {
        VsButton("分割", onSplit, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = state.canSplit)
        VsButton("刪除", onDelete, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = state.canDelete)
    }
}

/** [ClipEditorScreen] 的播放狀態同步用的小記事本：不需要觸發重組，所以不是 Compose 狀態。 */
private class PlaybackSync(var wasPlaying: Boolean, var suppress: Boolean = false)
