package com.xenyaa.videoshot.ui.clip

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.edit.failedTitle
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** [ClipExportRoute] 用的包裝：區分「還沒讀到」（沒有這個物件）與「讀到了、任務已經不在」（[task] 是 null）。 */
private class ExportTaskHolder(val task: EditTask?)

/**
 * 導覽用的入口：收集任務、接上取消／重試／勾選框，任務不在了（取消後被刪掉）就呼叫 [onGone]。
 *
 * 取消的順序見 [cancelExportTask]。
 * [scope] 要比這個畫面活得久（`AppRoot` 的 scope）：刪掉任務後這個畫面就離開組合了，補排不能跟著被取消。
 * 刪掉任務之後 [onGone] 由任務流變成 null 觸發，這裡不另外呼叫，避免退兩層。
 * 勾選框打勾、上傳失敗後【重試】都先 [authorize]（見 [YoutubeAuthorizer]）；使用者沒給授權用 [onNotice] 提示。
 */
@Composable
fun ClipExportRoute(
    taskId: Long,
    deps: ClipDeps,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onGone: () -> Unit,
    onPlay: () -> Unit,
    authorize: suspend () -> UploadAuthResult,
    onNotice: (String) -> Unit,
) {
    val repo: CacheRepo = deps.cacheRepo
    // 停在這個畫面時結果直接看得到，不另外跳通知（橫幅會蓋住頂欄）；退到背景或離開就恢復
    LifecycleResumeEffect(taskId, deps) {
        deps.viewingExport(taskId)
        onPauseOrDispose { deps.viewingExport(null) }
    }
    val holder by remember(taskId, repo) { repo.editTask(taskId).map { ExportTaskHolder(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val current = holder ?: return
    val task = current.task
    if (task == null) {
        LaunchedEffect(taskId) { onGone() }
        return
    }
    ClipExportScreen(
        task = task,
        thumbLoader = deps.mediaThumbLoader,
        onBack = onBack,
        onAutoUploadChange = { on ->
            scope.launch {
                // 取消打勾不用授權；打勾前先授權，使用者沒給就維持沒勾（授權服務叫不動照樣打勾）
                if (on && authorize() == UploadAuthResult.DECLINED) {
                    onNotice(NO_AUTH_NO_UPLOAD)
                    return@launch
                }
                guarded("改自動上傳") { repo.setEditTaskAutoUpload(taskId, on, deps.now()) }
            }
        },
        onCancel = { scope.launch { cancelExportTask(deps, taskId) } },
        onRetry = { scope.launch { retryExportTask(deps, taskId, authorize) { onNotice(NO_AUTH_CANT_UPLOAD) } } },
        onPlay = onPlay,
    )
}

/**
 * 取消一個任務（導出中畫面與剪輯頁的任務列共用）：收掉等縮圖的輪詢（處理中的任務才有）→
 * 取消正在跑的工作（等套用完；排隊中的不動）→ 刪任務 →
 * 再查一次（刪之前剛好輪到它開始跑的話，它已經讀到任務、會一路輸出完）→ 刪 app 裡的資料夾 →
 * 補排被連帶取消的後面幾個任務。補排不管前面成不成功都做（已排著的不動），而且一定在刪任務之後：
 * 補排最後會把「處理中」的任務排回輪詢，順序反了會把剛收掉的輪詢排回去。
 * 排隊中的工作留著，輪到時讀不到任務就直接結束。
 */
internal suspend fun cancelExportTask(deps: ClipDeps, taskId: Long) {
    try {
        guarded("取消輸出") {
            deps.cancelStoryboardPoll(taskId)
            deps.cancelExport(taskId)
            deps.cacheRepo.deleteEditTask(taskId)
            deps.cancelExport(taskId)
            deps.deleteExportFiles(taskId)
            deps.dismissExportResult(taskId)
        }
    } finally {
        guarded("補排輸出") { deps.resumeExports() }
    }
}

/**
 * 刪掉一個已經結束（已完成或失敗）的任務：收掉輪詢（失敗在處理那一步時可能還排著）、先刪任務、再刪 app 裡的成品資料夾（相簿裡的不動），最後補排一次。
 * 先刪任務：中途掛掉最多留下沒人指到的檔案，不會留下指向已刪成品的任務。
 */
internal suspend fun deleteExportTask(deps: ClipDeps, taskId: Long) {
    try {
        guarded("刪除任務") {
            deps.cancelStoryboardPoll(taskId)
            deps.cacheRepo.deleteEditTask(taskId)
            deps.deleteExportFiles(taskId)
            deps.dismissExportResult(taskId)
        }
    } finally {
        guarded("補排輸出") { deps.resumeExports() }
    }
}

/** 【開始】或勾選框打勾時沒拿到 YouTube 授權：任務照建（或維持）但不上傳。 */
const val NO_AUTH_NO_UPLOAD = "沒有取得 YouTube 授權，這支不會上傳"

/** 上傳那一步失敗後按【重試】時沒拿到 YouTube 授權：任務維持失敗。 */
const val NO_AUTH_CANT_UPLOAD = "沒有取得 YouTube 授權，無法上傳"

/**
 * 重試一個失敗的任務：依它失敗的步驟回到那一步的開頭（導出中畫面與剪輯頁的任務列共用）。
 * 回到等待中或上傳中重新排工作，回到處理中（等縮圖）排一次查詢。
 * 上傳那一步失敗的先 [authorize]，通過了才改狀態（先改的話被拒會留下沒排工作的上傳中任務）；
 * 被拒就什麼都不動、呼叫 [onAuthDenied]；授權服務叫不動（離線、Play 服務出錯）照樣重試。
 */
internal suspend fun retryExportTask(
    deps: ClipDeps,
    taskId: Long,
    authorize: suspend () -> UploadAuthResult,
    onAuthDenied: () -> Unit,
) {
    guarded("重試輸出") {
        val failed = deps.cacheRepo.editTask(taskId).first() ?: return@guarded
        if (failed.state == EditTaskState.FAILED && failed.failedStep == EditTaskStep.UPLOAD && authorize() == UploadAuthResult.DECLINED) {
            onAuthDenied()
            return@guarded
        }
        when (deps.cacheRepo.retryEditTask(taskId, deps.now())) {
            EditTaskState.QUEUED, EditTaskState.UPLOADING -> deps.enqueueExport(taskId)
            EditTaskState.PROCESSING -> deps.enqueueStoryboardPoll(taskId)
            else -> Unit
        }
    }
}

/** 執行 [block]；除了取消之外的例外只記 log（這些都是使用者按一下的動作，失敗了畫面狀態不變、可以再按）。 */
internal suspend fun guarded(what: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("VsClip", "$what 失敗", e)
    }
}

/**
 * 導出中畫面（階段 20）：頂欄「導出」＋返回；成品第一格的縮圖；依任務狀態顯示
 * 等待中／輸出中（縮圖疊上大的百分比、亮的部分隨進度往右，加提示）／
 * 完成（點縮圖或【播放檢查】、存相簿後的【播放】全螢幕播放）／上傳中（同輸出中的畫法）／YouTube 處理中／已完成／
 * 失敗（標題依失敗的步驟、原因與【重試】）。
 * 「完成後自動上傳到 YouTube」勾選框只在等待中與輸出中顯示；底部的取消鈕依階段是【取消導出】【取消上傳】【取消任務】。
 *
 * 前一次輸出殘留的略過個數與相簿位置（重試之後、完成之前還留在任務上）只在 [EditTaskState.RENDERED] 才看。
 */
@Composable
fun ClipExportScreen(
    task: EditTask,
    thumbLoader: MediaThumbLoader,
    onBack: () -> Unit,
    onAutoUploadChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
) {
    // 開確認框當下是哪一種取消；任務換了階段（例如輸出完開始上傳）框就不再成立，不會拿舊說法取消新階段
    var confirmCancel by rememberSaveable { mutableStateOf<CancelKind?>(null) }
    val cancelKind = task.state.cancelKind()
    val inProgress = cancelKind == CancelKind.EXPORT

    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().navigationBarsPadding()) {
        VsTopBar("導出", nav = TopBarNav.Back(onBack))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s4),
        ) {
            ExportThumb(
                task,
                thumbLoader,
                progress = if (task.state == EditTaskState.RENDERING || task.state == EditTaskState.UPLOADING) task.progress.coerceIn(0, 100) else null,
                onClick = if (task.state == EditTaskState.RENDERED) onPlay else null,
            )

            when (task.state) {
                EditTaskState.QUEUED -> StatusTitle("等待中…")
                EditTaskState.RENDERING -> {
                    StatusTitle("輸出影片中…")
                    Text(
                        "留在這個畫面會比較快；離開也會在背景繼續",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                }
                EditTaskState.RENDERED -> {
                    val saved = task.galleryUri != null
                    StatusTitle(if (saved) "已存到相簿" else "影片輸出完成")
                    Text(
                        "${task.title}・${formatDuration(task.durationMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                    if (task.skippedSources > 0) {
                        Text(
                            "有 ${task.skippedSources} 個素材已不存在，已略過",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                    VsButton(if (saved) "播放" else "播放檢查", onClick = onPlay)
                }
                // 縮圖上的大字百分比同輸出中；out.mp4 正在傳，縮圖不能點
                EditTaskState.UPLOADING -> StatusTitle("上傳中…")
                EditTaskState.PROCESSING -> {
                    StatusTitle("YouTube 處理中…")
                    Text(
                        "等 YouTube 產生縮圖，通常幾分鐘內完成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                }
                EditTaskState.DONE -> {
                    StatusTitle("已上傳到 YouTube（非公開）")
                    Text(
                        "${task.title}・${formatDuration(task.durationMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "開始取圖在下一個階段加入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                }
                EditTaskState.FAILED -> {
                    StatusTitle(failedTitle(task.failedStep))
                    Text(
                        task.failReason ?: "輸出失敗",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.danger,
                        textAlign = TextAlign.Center,
                    )
                    VsButton("重試", onClick = onRetry)
                }
            }

            // 勾選框只在等待中、輸出中出現（上傳中以後 out.mp4 已經在傳或已刪，改了也沒用）
            if (inProgress) AutoUploadCheckbox(task.autoUpload, onAutoUploadChange)
            if (cancelKind != null) {
                VsButton(cancelKind.buttonText, onClick = { confirmCancel = cancelKind }, variant = ButtonVariant.DangerQuiet)
            }
        }
    }

    val asking = confirmCancel
    if (asking != null && asking == cancelKind) {
        CancelTaskDialog(
            kind = asking,
            onConfirm = { confirmCancel = null; onCancel() },
            onDismiss = { confirmCancel = null },
        )
    }
}

/** 取消任務的三種說法：輸出（等待中、輸出中）、上傳中、等 YouTube 處理中。 */
internal enum class CancelKind(val buttonText: String) {
    EXPORT("取消導出"),
    UPLOAD("取消上傳"),
    PROCESSING("取消任務"),
}

/** 這個狀態的任務可以取消嗎、用哪一種說法；已結束的（輸出完成、已完成、失敗）是 null，只能刪除。 */
internal fun EditTaskState.cancelKind(): CancelKind? = when (this) {
    EditTaskState.QUEUED, EditTaskState.RENDERING -> CancelKind.EXPORT
    EditTaskState.UPLOADING -> CancelKind.UPLOAD
    EditTaskState.PROCESSING -> CancelKind.PROCESSING
    EditTaskState.RENDERED, EditTaskState.DONE, EditTaskState.FAILED -> null
}

/** 依 [kind] 選取消確認框：導出中畫面與剪輯頁的任務列共用。 */
@Composable
internal fun CancelTaskDialog(kind: CancelKind, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (kind) {
        CancelKind.EXPORT -> CancelExportDialog(onConfirm, onDismiss)
        CancelKind.UPLOAD -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("取消上傳？") },
            text = { Text("已經上傳的部分會作廢。") },
            confirmButton = { TextButton(onClick = onConfirm) { Text("取消上傳", color = AppTheme.colors.danger) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("繼續上傳") } },
        )
        CancelKind.PROCESSING -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("取消這個任務？") },
            text = { Text(STILL_ON_YOUTUBE) },
            confirmButton = { TextButton(onClick = onConfirm) { Text("取消任務", color = AppTheme.colors.danger) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("繼續等待") } },
        )
    }
}

/** 影片已經在使用者的 YouTube 頻道上（取消處理中、刪除有影片編號的任務時提醒）。 */
internal const val STILL_ON_YOUTUBE = "影片仍在你的 YouTube 頻道，可以到 YouTube 工作室刪除。"

/** 取消導出的確認框：導出中畫面與剪輯頁的任務列共用。 */
@Composable
fun CancelExportDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("取消導出？") },
        text = { Text("已經輸出的部分會刪掉。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("取消導出", color = AppTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("繼續導出") } },
    )
}

@Composable
private fun StatusTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = AppTheme.colors.text,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AutoUploadCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(AppTheme.spacing.s2))
        Text("完成後自動上傳到 YouTube", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
    }
}

/** 成品第一格的縮圖圖片：時間軸第一段的素材；還沒載入（或解碼失敗）是 null。導出中畫面與剪輯頁的任務列共用。 */
@Composable
internal fun rememberFirstSourceThumb(task: EditTask, loader: MediaThumbLoader): ImageBitmap? {
    val first = remember(task.draft) {
        val firstId = task.draft.timeline.segments.firstOrNull()?.sourceId
        task.draft.sources.firstOrNull { it.id == firstId }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val bitmap by produceState<ImageBitmap?>(first?.let { loader.peek(it.id) }, first?.id, loader, lifecycle) {
        if (first == null) return@produceState
        // 讀不到時每次回到前景再試：使用者可能剛在系統設定把相片權限給回來
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (value == null) value = loader.load(first.toMediaItem())
        }
    }
    return bitmap
}

/**
 * 成品第一格的縮圖：還沒載入（或解碼失敗）時是中性的色塊。
 *
 * - [progress] 不是 null（輸出中）：疊上大的百分比，左邊已完成的部分是原本的亮度、右邊還沒完成的壓暗，亮的部分隨進度往右。
 * - [onClick] 不是 null（輸出完成）：點縮圖跟【播放檢查】／【播放】一樣全螢幕播放成品。
 */
@Composable
private fun ExportThumb(task: EditTask, loader: MediaThumbLoader, progress: Int?, onClick: (() -> Unit)?) {
    val bitmap = rememberFirstSourceThumb(task, loader)
    val portrait = task.draft.orientation == ClipOrientation.PORTRAIT
    val shape = RoundedCornerShape(12.dp)
    val box = if (portrait) Modifier.width(160.dp).aspectRatio(9f / 16f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    var modifier = box.clip(shape).background(AppTheme.colors.surface2)
    val done = (progress ?: 0) / 100f
    if (progress != null) {
        modifier = modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(done, 0f..1f) }
    }
    if (onClick != null) {
        modifier = modifier.focusRing(shape).clickable(onClickLabel = "播放", role = Role.Button, onClick = onClick)
    }
    Box(modifier.testTag("exportThumb"), contentAlignment = Alignment.Center) {
        val thumb = bitmap
        if (thumb != null) {
            Image(thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (progress != null) {
            // 還沒完成的部分（右邊）壓暗；百分比的字在這層上面，不跟著變暗
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        ThumbDim,
                        topLeft = Offset(size.width * done, 0f),
                        size = Size(size.width * (1f - done), size.height),
                    )
                },
            )
            Text(
                "$progress%",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f),
                ),
                color = Color.White,
            )
        }
    }
}

/** 導出中縮圖還沒完成那一段的壓暗色。 */
private val ThumbDim = Color.Black.copy(alpha = 0.55f)
