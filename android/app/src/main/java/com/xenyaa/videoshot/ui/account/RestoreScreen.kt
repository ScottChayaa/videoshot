package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.backup.RemoteBackup
import com.xenyaa.videoshot.core.format.formatBytes
import com.xenyaa.videoshot.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 還原挑選畫面（規格第十節流程圖）。首次開啟（Task 14）與帳號頁【從 Drive 還原】共用。 */
@Composable
fun RestoreScreen(
    step: RestoreStep,
    onBack: () -> Unit,
    onPick: (RemoteBackup) -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 這個畫面掛在 AppShell 的 Scaffold 外面（AppRoot 的 RestoreFlow 與首次開啟兩條路），
    // 系統列 inset 要自己墊：頂欄讓開狀態列、清單底部讓開導覽列（VsTopBar 的 KDoc 規則）
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        AccountSettingHeader("從 Google Drive 還原", onBack, Modifier.statusBarsPadding())
        when (step) {
            is RestoreStep.Loading, is RestoreStep.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    CircularProgressIndicator()
                    Text(if (step is RestoreStep.Restoring) "正在還原…" else "正在讀取備份清單…", color = AppTheme.colors.textDim)
                }
            }
            is RestoreStep.Picking -> if (step.backups.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(AppTheme.spacing.s5), contentAlignment = Alignment.Center) {
                    Text("Drive 上還沒有任何備份", color = AppTheme.colors.textDim)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().padding(AppTheme.spacing.s4)) {
                    items(step.backups, key = { it.id }) { backup -> BackupRow(backup, onClick = { onPick(backup) }) }
                }
            }
            is RestoreStep.Confirming -> ConfirmRestoreDialog(step.backup, onConfirm, onDismissConfirm)
            is RestoreStep.Failed -> Box(Modifier.fillMaxSize().padding(AppTheme.spacing.s5), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    Text(step.reason, color = AppTheme.colors.danger)
                    TextButton(onClick = onRetry) { Text("重試") }
                }
            }
        }
    }
}

@Composable
private fun BackupRow(backup: RemoteBackup, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.s1).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(AppTheme.spacing.s3)) {
            val date = Instant.ofEpochSecond(backup.createdAtEpochSec).atZone(ZoneId.systemDefault()).format(BACKUP_DATE_FORMAT)
            Text(date, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text)
            Text(
                "${backup.shotCount} 張 · ${backup.deviceName} · ${formatBytes(backup.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun ConfirmRestoreDialog(backup: RemoteBackup, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("確定要還原這份備份？") },
        text = { Text("本機目前的收藏會被取代，未完成的取圖草稿也會捨棄。這個動作不能復原。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("還原", color = AppTheme.colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
