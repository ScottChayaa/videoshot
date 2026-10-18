package com.xenyaa.videoshot.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenyaa.videoshot.core.player.nearestFrameIndex
import com.xenyaa.videoshot.player.LocalPlayerFullscreen
import com.xenyaa.videoshot.player.PlayerSurface
import com.xenyaa.videoshot.player.playerFrame
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.TopBarTitle
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 取圖精靈的外殼：頂欄（左上角返回鍵）與 Android 返回。
 *
 * **沒有底部導覽**（規格第五節、手冊 §四）—— 導覽列常疊在縮圖牆上方，
 * 而精靈是一條有終點的流程，不該讓使用者中途跳去別的分頁。
 */
@Composable
fun WizardScreen(vm: WizardViewModel, haptics: Haptics, onExit: () -> Unit) {
    val step by vm.step.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    /**
     * 相簿選圖（規格第五節的退路）。用 Photo Picker —— 它不需要讀取儲存空間的權限，
     * 使用者只交出他挑的那一張。
     */
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // 按取消就是 null —— 什麼都不做，不要冒出一句錯誤訊息
        if (uri != null) vm.addFromGalleryUri(context.contentResolver, uri)
    }

    var takenTapped by remember { mutableStateOf<Int?>(null) }

    // 頂欄返回鍵與 Android 返回同一套：第二、三步回上一步（選擇不會丟，草稿會存），第一步才離開取圖
    val goBack = { if (!vm.back()) onExit() }
    BackHandler(onBack = goBack)

    val loadedForTitle by vm.loaded.collectAsStateWithLifecycle()
    // 播放器全螢幕時藏頂欄與縮圖牆（規格第六節「播放器」）
    val fullscreen = LocalPlayerFullscreen.current.active
    // 原型 .wz-top：第一步標題是「取圖」，之後換成影片標題（取不到時退回「取圖」）
    val title = if (step == WizardStep.URL) "取圖" else loadedForTitle?.page?.meta?.title ?: "取圖"

    // 不用 Scaffold：這裡沒有底部導覽，內容區的底部 inset 交給各步自己的 VsActionDock
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        if (!fullscreen) {
            VsTopBar(
                title = title,
                modifier = Modifier.statusBarsPadding(),
                nav = TopBarNav.Back(onClick = goBack),
                titleStyle = TopBarTitle.Small,
                divider = false,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                WizardStep.URL -> {
                    val status by vm.status.collectAsStateWithLifecycle()
                    val recent by vm.recent.collectAsStateWithLifecycle()
                    Step1UrlScreen(
                        status = status,
                        recent = recent,
                        onSubmit = { vm.submit(it) },
                        onOpenRecent = { vm.openRecent(it) },
                    )
                }
                WizardStep.PICK -> {
                    val store by vm.step2.collectAsStateWithLifecycle()
                    val loaded by vm.loaded.collectAsStateWithLifecycle()
                    val current = store
                    if (current == null) {
                        LoadingText("正在載入縮圖…")
                    } else {
                        val state by current.state.collectAsStateWithLifecycle()
                        val captureError by vm.captureError.collectAsStateWithLifecycle()
                        Column(Modifier.fillMaxSize()) {
                            // 播放器**釘在頂部**（規格第五節第二步的線框）；全螢幕時原地撐滿（第六節「播放器」）。
                            // PlayerSurface 在組合裡的位置不能動，全螢幕只換 Modifier、兄弟節點用 if 包起來
                            loaded?.page?.meta?.let { meta ->
                                // 預覽圖：最接近那個秒數的縮圖牆格子，不多下載（第六節「預覽縮圖的來源」）
                                val preview: suspend (Double) -> androidx.compose.ui.graphics.ImageBitmap? =
                                    remember(current) {
                                        { sec ->
                                            nearestFrameIndex(current.state.value.plan.atSec, sec)
                                                ?.let { current.source.bitmapOf(it) }
                                        }
                                    }
                                PlayerSurface(
                                    videoId = meta.videoId,
                                    playableInEmbed = meta.playableInEmbed,
                                    durationSec = meta.durationSec,
                                    preview = preview,
                                    onPlayerReady = { vm.attachPlayer(it) },
                                    modifier = Modifier.playerFrame(fullscreen),
                                    // WebView 被釋放了 ViewModel 就不能再握著它
                                    onPlayerReleased = { vm.detachPlayer() },
                                )
                                // 播放器還在畫面上才輪詢：每 0.25 秒讀一次目前秒數，更新縮圖牆的外框
                                LaunchedEffect(meta.videoId) {
                                    while (true) {
                                        delay(PLAYBACK_POLL_MS)
                                        vm.pollPlayback()
                                    }
                                }
                            }
                            // 捲動狀態放在 if 外面：全螢幕時縮圖牆離開組合，退出後要回到原來捲到的位置（rememberSaveable：Activity 重建也撐得過）；
                            // 以 store 當 key，換影片就重來
                            val gridState = rememberSaveable(current, saver = LazyGridState.Saver) { LazyGridState() }
                            if (!fullscreen) {
                                // remember 起來、以 store 當 key：FrameImage 拿它當 produceState 的 key，
                                // 每次重組都給新 lambda 的話每一格都會重新解圖
                                val bitmapFor: suspend (Int) -> androidx.compose.ui.graphics.ImageBitmap? =
                                    remember(current) { { cell -> current.bitmapOfCell(cell) } }
                                Step2GridScreen(
                                    state = state,
                                    bitmapFor = bitmapFor,
                                    haptics = haptics,
                                    onToggle = { current.toggle(it) },
                                    onTakenTap = { takenTapped = it },
                                    onPlayFrame = { vm.playFrame(it) },
                                    onSelectAll = { current.selectAll() },
                                    onTakeShot = { vm.takeShot() },
                                    onShowAll = { current.setShowAll(it) },
                                    onDismissHint = { vm.dismissHint() },
                                    onNext = { vm.goTo(WizardStep.DETAILS) },
                                    captureError = captureError,
                                    onPickFromGallery = { pickImage.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    ) },
                                    onDismissCaptureError = { vm.dismissCaptureError() },
                                    onNudgeManual = { cell, delta -> vm.nudgeManual(cell, delta) },
                                    gridState = gridState,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                WizardStep.DETAILS -> {
                    val store by vm.step3.collectAsStateWithLifecycle()
                    val step2 by vm.step2.collectAsStateWithLifecycle()
                    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
                    val current = store
                    if (current == null) {
                        LoadingText("正在準備縮圖…")
                    } else {
                        val state by current.state.collectAsStateWithLifecycle()
                        // 圖仍然由第二步的狀態機供應 —— 它同時認得 storyboard 格與手動格
                        val bitmapFor: suspend (Int) -> androidx.compose.ui.graphics.ImageBitmap? =
                            remember(step2) { { cell -> step2?.bitmapOfCell(cell) } }
                        Step3DetailsScreen(
                            state = state,
                            bitmapFor = bitmapFor,
                            onToggle = { current.toggle(it) },
                            onSelectAll = { current.selectAll() },
                            requireAllFilled = true,
                            onInvert = { current.invert() },
                            onSelectUnapplied = { current.selectUnapplied() },
                            onEditEventDate = { current.editEventDate(it) },
                            onEditPlace = { current.editPlace(it) },
                            onEditDescription = { current.editDescription(it) },
                            onEditTags = { current.editTags(it) },
                            onApply = { vm.applyDetails() },
                            onFinish = { vm.finish() },
                            placeSuggestions = suggestions.places,
                            tagSuggestions = suggestions.tags,
                        )
                    }
                }
            }
        }
    }

    val draftPrompt by vm.draftPrompt.collectAsStateWithLifecycle()
    draftPrompt?.let { prompt ->
        val stepName = WizardStep.entries.firstOrNull { it.order == prompt.step }?.label ?: "挑畫面"
        AlertDialog(
            // 點外面不算回答 —— 兩個選項的後果差很多（其中一個會刪掉手動補圖）
            onDismissRequest = {},
            title = { Text("上次做到「$stepName」，要繼續嗎？") },
            text = { Text("選【重新開始】會把上次的選擇與補圖一起清掉。") },
            confirmButton = { VsButton("繼續", { vm.resumeDraft() }) },
            dismissButton = { VsButton("重新開始", { vm.startOver() }, variant = ButtonVariant.Quiet) },
        )
    }

    takenTapped?.let {
        AlertDialog(
            onDismissRequest = { takenTapped = null },
            title = { Text("這一格已經收藏過了") },
            text = { Text("長按仍然可以跳到那一段看看。") },
            confirmButton = { VsButton("知道了", { takenTapped = null }) },
        )
    }

    // 第三步的【完成】要每一張都套用過才按得下（2026-10-17），正常操作走不到這個提醒框；
    // 留著當 ViewModel 的防線（finish() 本身仍會擋下還沒填完的）
    val pendingFinish by vm.pendingFinish.collectAsStateWithLifecycle()
    pendingFinish?.let { count ->
        AlertDialog(
            onDismissRequest = { vm.dismissPendingFinish() },
            title = { Text("還有 $count 張沒填資料，仍要完成嗎？") },
            // 提醒但不阻擋（規格第五節「完成」）—— 圖本身已經有價值，圖資可以之後補
            text = { Text("沒填的圖仍然會進圖庫，之後可以在詳情頁補上。") },
            confirmButton = { VsButton("仍要完成", { vm.finish(force = true) }) },
            dismissButton = { VsButton("回去填", { vm.dismissPendingFinish() }, variant = ButtonVariant.Quiet) },
        )
    }

    val commitFailed by vm.commitFailed.collectAsStateWithLifecycle()
    if (commitFailed) {
        AlertDialog(
            onDismissRequest = { vm.dismissCommitFailed() },
            title = { Text("存不進圖庫") },
            // 草稿還在 —— 這是使用者現在最需要知道的事
            text = { Text("剛才的選擇都還留著，可以再試一次。") },
            confirmButton = { VsButton("知道了", { vm.dismissCommitFailed() }) },
        )
    }
}

/** 播放進度輪詢間隔。夠順又不至於一直打擾 WebView。 */
private const val PLAYBACK_POLL_MS = 250L

/** 步驟內容還沒備妥時的置中提示（原型沒有對應畫面，沿用 15 textDim）。 */
@Composable
private fun LoadingText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textDim)
    }
}
