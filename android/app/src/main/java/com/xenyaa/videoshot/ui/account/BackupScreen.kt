package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.backup.lastBackupLabel
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 帳號頁「備份」子畫面（規格第九節）。【中斷連結】放在這個子畫面最底，不是整個帳號頁最底——
 * 規格第九節的版面表把它畫在「備份」那一列裡，跟這個子畫面對得上。
 */
@Composable
fun BackupScreen(
    linkedAccount: LinkedGoogleAccount?,
    lastBackupAtEpochSec: Long,
    backingUp: Boolean,
    backupError: String?,
    onBack: () -> Unit,
    onLinkClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    onBackupNowClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowEpochSec: Long = System.currentTimeMillis() / 1000,
) {
    var confirmingUnlink by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s4)) {
        AccountSettingHeader("備份", onBack)
        Column(
            Modifier.padding(horizontal = AppTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            if (linkedAccount == null) {
                Text("尚未設定備份", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.text)
                Button(onClick = onLinkClick) { Text("連結 Google 帳號以啟用備份") }
            } else {
                Text(
                    "已連結：${linkedAccount.displayName}（${linkedAccount.email}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.text,
                )
                Text(
                    "上次備份：${lastBackupLabel(lastBackupAtEpochSec, nowEpochSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    Button(onClick = onBackupNowClick, enabled = !backingUp, modifier = Modifier.weight(1f)) {
                        Text(if (backingUp) "備份中…" else "立即備份")
                    }
                    TextButton(onClick = onRestoreClick, modifier = Modifier.weight(1f)) { Text("從 Drive 還原") }
                }
            }
            // 錯誤訊息擺在 if/else **外面**：連結失敗（`AccountViewModel.beginLink`／
            // `finishLink`）發生時 `linkedAccount` 還是 null，畫在「已連結」那一支裡的話
            // 那條路徑最需要看到的錯誤永遠不會被畫出來（最終審查 Important 4）。
            if (backupError != null) {
                Text(backupError, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger)
            }
        }
        if (linkedAccount != null) {
            Spacer(Modifier.weight(1f, fill = false))
            TextButton(
                onClick = { confirmingUnlink = true },
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s4),
            ) { Text("中斷連結", color = AppTheme.colors.danger) }
        }
    }

    if (confirmingUnlink) {
        AlertDialog(
            onDismissRequest = { confirmingUnlink = false },
            title = { Text("中斷連結？") },
            text = { Text("中斷後不會刪除 Drive 上已經備份的檔案，但這台裝置不會再自動備份。") },
            confirmButton = {
                TextButton(onClick = { confirmingUnlink = false; onUnlinkClick() }) {
                    Text("中斷連結", color = AppTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingUnlink = false }) { Text("取消") } },
        )
    }
}
