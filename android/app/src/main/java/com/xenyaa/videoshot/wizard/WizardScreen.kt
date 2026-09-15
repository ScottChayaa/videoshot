package com.xenyaa.videoshot.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenyaa.videoshot.player.PlayerSurface

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
    var askExit by remember { mutableStateOf(false) }
    var takenTapped by remember { mutableStateOf<Int?>(null) }

    // 返回鍵先給精靈消化；在第一步才讓系統關掉整個流程
    BackHandler { if (!vm.back()) onExit() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 用文字的 ✕ 而不是 material-icons 的向量圖：少一個相依。
                    // IconButton 沒有 contentDescription 參數，改由 semantics 提供。
                    IconButton(
                        onClick = {
                            // 第一步還沒有任何投入，直接走；之後要問草稿怎麼辦
                            if (step == WizardStep.URL) onExit() else askExit = true
                        },
                        modifier = Modifier.semantics { contentDescription = "關閉" },
                    ) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                }
                StepIndicator(
                    current = step,
                    furthest = furthest,
                    onJump = { vm.jumpTo(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
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
                        Text("正在載入縮圖…")
                    } else {
                        val state by current.state.collectAsStateWithLifecycle()
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
                                onShowAll = { current.setShowAll(it) },
                                onOnlySelected = { current.setOnlySelected(it) },
                                onDismissHint = { vm.dismissHint() },
                                onNext = { vm.goTo(WizardStep.DETAILS) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // 階段 6 的第三步會取代這個佔位畫面
                WizardStep.DETAILS -> Text("第三步（填資料）在階段 6 實作")
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

    takenTapped?.let {
        AlertDialog(
            onDismissRequest = { takenTapped = null },
            title = { Text("這一格已經收藏過了") },
            text = { Text("長按或按 ▶ 仍然可以跳到那一段看看。") },
            confirmButton = { TextButton(onClick = { takenTapped = null }) { Text("知道了") } },
        )
    }
}

/** 帶文字的三段進度。已完成的步驟可點回去，未到達的點不動。 */
@Composable
private fun StepIndicator(
    current: WizardStep,
    furthest: WizardStep,
    onJump: (WizardStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        WizardStep.entries.forEach { s ->
            val reachable = s.order <= furthest.order
            Text(
                text = s.indicator,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal,
                color = if (reachable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.clickable(enabled = reachable) { onJump(s) },
            )
        }
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
        confirmButton = { TextButton(onClick = onKeep) { Text("保留草稿並離開") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("捨棄草稿") } },
    )
}
