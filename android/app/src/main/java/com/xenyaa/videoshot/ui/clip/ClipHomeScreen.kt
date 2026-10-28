package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.media.MediaThumbLoader
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

/** 沒有真的縮圖來源時用的載入器（解碼一律失敗，縮圖位置是中性色塊）；測試與預覽不必傳。 */
private val NoThumbs = MediaThumbLoader(decode = { null })

/**
 * 剪輯分頁（底部導覽第三格）。沒有草稿也沒有任務時是空狀態；否則由上到下是：
 * 「編輯中」草稿列（點了接著編輯，長按或右側 ⋯ 選單可以【放棄】）→ 導出任務（[tasks] 照傳進來的順序，新的在前）→【新增剪輯】。
 *
 * 任務列：點了進導出中畫面；等待中與輸出中的長按選單是【取消】，完成與失敗的是【刪除任務】，
 * 兩者都要確認才通知呼叫端；失敗列另有【重試】。
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
    tasks: List<EditTask> = emptyList(),
    thumbLoader: MediaThumbLoader = NoThumbs,
    onOpenTask: (Long) -> Unit = {},
    onCancelTask: (Long) -> Unit = {},
    onDeleteTask: (Long) -> Unit = {},
    onRetryTask: (Long) -> Unit = {},
) {
    // null＝沒在問；false＝從草稿列放棄；true＝按了【新增剪輯】、確認放棄後接著新增
    var confirmDiscard by remember { mutableStateOf<Boolean?>(null) }
    // 任務列的確認框：存任務編號，內容（要不要多一句）每次從最新的任務清單查；任務消失了框也跟著消失
    var confirmCancelId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }

    Column(modifier.fillMaxSize()) {
        VsTopBar("剪輯")
        if (draft == null && tasks.isEmpty()) {
            VsEmptyState(
                message = "把手機裡的照片和影片剪成一支片",
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s6),
                icon = VsIcons.Clapperboard,
                secondary = "上傳到你的 YouTube（非公開）後，就能用取圖精靈挑畫面",
                actionText = "新增剪輯",
                onAction = onNewClip,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(AppTheme.spacing.s4),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
            ) {
                if (draft != null) {
                    item(key = "draft") {
                        DraftRow(draft = draft, onOpen = onOpenDraft, onDiscard = { confirmDiscard = false })
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        thumbLoader = thumbLoader,
                        onOpen = { onOpenTask(task.id) },
                        onCancel = { confirmCancelId = task.id },
                        onDelete = { confirmDeleteId = task.id },
                        onRetry = { onRetryTask(task.id) },
                    )
                }
                item(key = "new") {
                    VsButton(
                        "新增剪輯",
                        { if (draft != null) confirmDiscard = true else onNewClip() },
                        variant = ButtonVariant.Secondary,
                    )
                }
            }
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

    val cancelling = tasks.firstOrNull { it.id == confirmCancelId }
    if (cancelling != null) {
        CancelExportDialog(
            onConfirm = { confirmCancelId = null; onCancelTask(cancelling.id) },
            onDismiss = { confirmCancelId = null },
        )
    }
    val deleting = tasks.firstOrNull { it.id == confirmDeleteId }
    if (deleting != null) {
        DeleteTaskDialog(
            // 沒存進相簿的完成品只在 app 裡：刪任務會連影片一起刪，要講清楚
            alsoDeletesVideo = deleting.state == EditTaskState.RENDERED && deleting.galleryUri == null,
            onConfirm = { confirmDeleteId = null; onDeleteTask(deleting.id) },
            onDismiss = { confirmDeleteId = null },
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

/** 刪除任務的確認框；[alsoDeletesVideo]＝完成品只存在 app 裡（沒存進相簿）。 */
@Composable
fun DeleteTaskDialog(alsoDeletesVideo: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("刪除這個任務？") },
        text = if (alsoDeletesVideo) ({ Text("app 裡輸出的影片會一起刪掉。") }) else null,
        confirmButton = { TextButton(onClick = onConfirm) { Text("刪除", color = AppTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 一個導出任務的列：縮圖（第一段素材）＋標題＋狀態文字；右側是 ▶︎（完成）、【重試】（失敗）和 ⋯ 選單。
 * 前一次輸出留在任務上的相簿位置只在「輸出完成」才看（重試後它還在，但那時狀態是等待中）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: EditTask,
    thumbLoader: MediaThumbLoader,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val inProgress = task.state == EditTaskState.QUEUED || task.state == EditTaskState.RENDERING
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .focusRing(shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "查看導出",
                onLongClickLabel = "更多操作",
                onLongClick = { menuOpen = true },
                onClick = onOpen,
            )
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        TaskThumb(task, thumbLoader)
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (task.state) {
                    EditTaskState.QUEUED -> "等待中"
                    EditTaskState.RENDERING -> "輸出影片中 ${task.progress}%"
                    EditTaskState.RENDERED -> if (task.galleryUri != null) "已存到相簿" else "輸出完成"
                    EditTaskState.FAILED -> "失敗：${task.failReason ?: "輸出失敗"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.state == EditTaskState.FAILED) colors.danger else colors.textDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (task.state == EditTaskState.RENDERING) {
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = colors.accent,
                    trackColor = colors.surface2,
                )
            }
        }
        when (task.state) {
            EditTaskState.RENDERED -> Icon(VsIcons.Play, contentDescription = null, tint = colors.accent)
            EditTaskState.FAILED -> VsButton("重試", onRetry, variant = ButtonVariant.Secondary)
            else -> Unit
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape),
            ) {
                Icon(VsIcons.More, contentDescription = "${task.title}的更多操作", tint = colors.textDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    // 破壞性動作：用 danger 色（手冊 §零第二條）
                    text = { Text(if (inProgress) "取消" else "刪除任務", color = colors.danger) },
                    leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = colors.danger) },
                    onClick = { menuOpen = false; if (inProgress) onCancel() else onDelete() },
                )
            }
        }
    }
}

/** 任務列左邊的方形縮圖（時間軸第一段的素材）；還沒載入時是中性色塊。 */
@Composable
private fun TaskThumb(task: EditTask, loader: MediaThumbLoader) {
    val bitmap = rememberFirstSourceThumb(task, loader)
    val modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(AppTheme.colors.surface2))
    }
}
