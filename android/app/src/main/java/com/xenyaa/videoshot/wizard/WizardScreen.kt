package com.xenyaa.videoshot.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenyaa.videoshot.player.PlayerSurface
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.TopBarTitle
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsStepIndicator
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 取圖精靈的外殼：三段進度、【✕】、返回鍵、離開確認。
 *
 * **沒有底部導覽**（規格第五節、手冊 §四）—— 導覽列常疊在縮圖牆上方，
 * 而精靈是一條有終點的流程，不該讓使用者中途跳去別的分頁。
 */
@Composable
fun WizardScreen(vm: WizardViewModel, haptics: Haptics, onExit: () -> Unit) {
    val step by vm.step.collectAsStateWithLifecycle()
    val furthest by vm.furthest.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    /**
     * 相簿選圖（規格第五節的退路）。用 Photo Picker —— 它不需要讀取儲存空間的權限，
     * 使用者只交出他挑的那一張。
     */
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // 按取消就是 null —— 什麼都不做，不要冒出一句錯誤訊息
        if (uri != null) vm.addFromGalleryUri(context.contentResolver, uri)
    }

    var askExit by remember { mutableStateOf(false) }
    var takenTapped by remember { mutableStateOf<Int?>(null) }

    // 返回鍵先給精靈消化；在第一步才讓系統關掉整個流程
    BackHandler { if (!vm.back()) onExit() }

    val loadedForTitle by vm.loaded.collectAsStateWithLifecycle()
    // 原型 .wz-top：第一步標題是「取圖」，之後換成影片標題（取不到時退回「取圖」）
    val title = if (step == WizardStep.URL) "取圖" else loadedForTitle?.page?.meta?.title ?: "取圖"

    // 不用 Scaffold：這裡沒有底部導覽，內容區的底部 inset 交給各步自己的 VsActionDock
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        VsTopBar(
            title = title,
            modifier = Modifier.statusBarsPadding(),
            // 第一步還沒有任何投入，直接走；之後要問草稿怎麼辦
            nav = TopBarNav.Close(onClick = { if (step == WizardStep.URL) onExit() else askExit = true }),
            titleStyle = TopBarTitle.Small,
        )
        VsStepIndicator(
            steps = WizardStep.entries.map { it.label },
            current = step.order - 1,
            furthest = furthest.order - 1,
            onStepClick = { vm.jumpTo(WizardStep.entries[it]) },
        )
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
                            // 播放器**釘在頂部**（規格第五節第二步的線框）
                            loaded?.page?.meta?.let { meta ->
                                PlayerSurface(
                                    videoId = meta.videoId,
                                    playableInEmbed = meta.playableInEmbed,
                                    onPlayerReady = { vm.attachPlayer(it) },
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                    // WebView 被釋放了 ViewModel 就不能再握著它
                                    onPlayerReleased = { vm.detachPlayer() },
                                )
                            }
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
                                onOnlySelected = { current.setOnlySelected(it) },
                                onDismissHint = { vm.dismissHint() },
                                onNext = { vm.goTo(WizardStep.DETAILS) },
                                captureError = captureError,
                                onPickFromGallery = { pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                ) },
                                onDismissCaptureError = { vm.dismissCaptureError() },
                                onNudgeManual = { cell, delta -> vm.nudgeManual(cell, delta) },
                                modifier = Modifier.weight(1f),
                            )
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
                            onSelectNone = { current.selectNone() },
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

    if (askExit) {
        ExitDialog(
            onKeep = { askExit = false; vm.keepDraft(); onExit() },
            onDiscard = { askExit = false; vm.discardDraft(); onExit() },
            onDismiss = { askExit = false },
        )
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
            text = { Text("長按或按 ▶ 仍然可以跳到那一段看看。") },
            confirmButton = { VsButton("知道了", { takenTapped = null }) },
        )
    }

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

/** 步驟內容還沒備妥時的置中提示（原型沒有對應畫面，沿用 15 textDim）。 */
@Composable
private fun LoadingText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textDim)
    }
}

/**
 * 離開確認。**文案要說得出草稿還在不在**（手冊 §四）——
 * 「要離開嗎？」這種問法答完了使用者還是不知道剛才選的東西會怎樣。
 */
@Composable
private fun ExitDialog(onKeep: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("要離開取圖嗎？") },
        text = { Text("保留草稿的話，下次按【取圖】可以接著做。") },
        confirmButton = { VsButton("保留草稿並離開", onKeep) },
        dismissButton = { VsButton("捨棄草稿", onDiscard, variant = ButtonVariant.DangerQuiet) },
    )
}
