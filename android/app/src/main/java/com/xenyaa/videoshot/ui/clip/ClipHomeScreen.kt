package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 剪輯頁「編輯中」那一列要顯示的草稿摘要。 */
data class ClipDraftSummary(val orientation: ClipOrientation, val sourceCount: Int, val durationMs: Long)

/** 草稿的摘要：素材個數數的是時間軸上還用得到的素材（分割不會讓個數變多，刪光的素材不算）。 */
fun ClipDraft.toSummary() = ClipDraftSummary(
    orientation = orientation,
    sourceCount = timeline.segments.mapTo(HashSet()) { it.sourceId }.size,
    durationMs = timeline.durationMs,
)

/**
 * 剪輯分頁（底部導覽第三格）。沒有草稿時是空狀態；有草稿時最上面多一列「編輯中」，點了接著編輯，
 * 長按或右側 ⋯ 選單可以【放棄】（確認後才通知呼叫端）。任務清單是階段 20 起的事。
 *
 * 【新增剪輯】只通知呼叫端，「選方向」的抽屜由 [AppRoot] 掛（見 [OrientationSheet]）。
 * 有草稿時按【新增剪輯】會先問是否放棄：確認後先 [onDiscardDraft]、再 [onNewClip]。
 */
@Composable
fun ClipHomeScreen(
    onNewClip: () -> Unit,
    modifier: Modifier = Modifier,
    draft: ClipDraftSummary? = null,
    onOpenDraft: () -> Unit = {},
    onDiscardDraft: () -> Unit = {},
) {
    // null＝沒在問；false＝從草稿列放棄；true＝按了【新增剪輯】、確認放棄後接著新增
    var confirmDiscard by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier.fillMaxSize()) {
        VsTopBar("剪輯")
        if (draft == null) {
            VsEmptyState(
                message = "把手機裡的照片和影片剪成一支片",
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s6),
                icon = VsIcons.Clapperboard,
                secondary = "上傳到你的 YouTube（非公開）後，就能用取圖精靈挑畫面",
                actionText = "新增剪輯",
                onAction = onNewClip,
            )
        } else {
            DraftRow(
                draft = draft,
                onOpen = onOpenDraft,
                onDiscard = { confirmDiscard = false },
                modifier = Modifier.padding(AppTheme.spacing.s4),
            )
            VsButton(
                "新增剪輯",
                { confirmDiscard = true },
                Modifier.padding(horizontal = AppTheme.spacing.s4),
                variant = ButtonVariant.Secondary,
            )
        }
    }

    confirmDiscard?.let { thenNew ->
        DiscardClipDraftDialog(
            onConfirm = {
                confirmDiscard = null
                onDiscardDraft()
                if (thenNew) onNewClip()
            },
            onDismiss = { confirmDiscard = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraftRow(draft: ClipDraftSummary, onOpen: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.border, shape)
            .focusRing(shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "接著編輯",
                onLongClickLabel = "更多操作",
                onLongClick = { menuOpen = true },
                onClick = onOpen,
            )
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        Icon(VsIcons.Clapperboard, contentDescription = null, tint = AppTheme.colors.accent)
        Column(Modifier.weight(1f)) {
            Text("編輯中", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            Text(
                "${if (draft.orientation == ClipOrientation.PORTRAIT) "直式" else "橫式"}・${draft.sourceCount} 個素材・${formatDuration(draft.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape),
            ) {
                Icon(VsIcons.More, contentDescription = "編輯中的更多操作", tint = AppTheme.colors.textDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    // 破壞性動作：用 danger 色（手冊 §零第二條）
                    text = { Text("放棄", color = AppTheme.colors.danger) },
                    leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                    onClick = { menuOpen = false; onDiscard() },
                )
            }
        }
    }
}

/** 放棄剪輯草稿的確認框：從草稿列放棄、或有草稿時按【新增剪輯】都用這一個。 */
@Composable
fun DiscardClipDraftDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("放棄目前編輯中的剪輯？") },
        text = { Text("時間軸和復原紀錄都會清掉，手機裡的照片和影片不受影響。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("放棄", color = AppTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
