package com.xenyaa.videoshot.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/**
 * 取圖精靈的外殼：三段進度、【✕】、返回鍵、離開確認。
 *
 * **沒有底部導覽**（規格第五節、手冊 §四）—— 導覽列常疊在縮圖牆上方，
 * 而精靈是一條有終點的流程，不該讓使用者中途跳去別的分頁。
 */
@Composable
fun WizardScreen(vm: WizardViewModel, onExit: () -> Unit) {
    val step by vm.step.collectAsStateWithLifecycle()
    val furthest by vm.furthest.collectAsStateWithLifecycle()
    var askExit by remember { mutableStateOf(false) }

    // 返回鍵先給精靈消化；在第一步才讓系統關掉整個流程
    BackHandler { if (!vm.back()) onExit() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
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
                WizardStep.URL -> Text("第一步在 Task 4a.3 實作")
                // 階段 4b 的縮圖牆、階段 6 的第三步會取代這兩個佔位畫面
                WizardStep.PICK -> Text("第二步（挑畫面）在階段 4b 實作")
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
