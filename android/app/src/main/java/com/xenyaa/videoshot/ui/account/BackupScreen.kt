package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.backup.lastBackupLabel
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.common.VsSettingDivider
import com.xenyaa.videoshot.ui.common.VsSettingGroup
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

    Column(modifier.fillMaxSize()) {
        AccountSettingHeader("備份", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (linkedAccount == null) {
                VsSettingGroup {
                    Column(
                        Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
                    ) {
                        Text("尚未設定備份", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
                        VsButton(
                            "連結 Google 帳號以啟用備份",
                            onLinkClick,
                            Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Primary,
                        )
                    }
                }
            } else {
                VsSettingGroup {
                    VsListRow(
                        title = "已連結：${linkedAccount.displayName}（${linkedAccount.email}）",
                        subtitle = "上次備份：${lastBackupLabel(lastBackupAtEpochSec, nowEpochSec)}",
                    )
                    VsSettingDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                    ) {
                        VsButton(
                            if (backingUp) "備份中…" else "立即備份",
                            onBackupNowClick,
                            Modifier.weight(1f),
                            variant = ButtonVariant.Primary,
                            enabled = !backingUp,
                        )
                        VsButton("從 Drive 還原", onRestoreClick, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                    }
                }
            }
            // 錯誤訊息擺在 if/else **外面**：連結失敗（`AccountViewModel.beginLink`／
            // `finishLink`）發生時 `linkedAccount` 還是 null，畫在「已連結」那一支裡的話
            // 那條路徑最需要看到的錯誤永遠不會被畫出來（最終審查 Important 4）。
            // 錯誤不是破壞性動作，用 warn 色提示框，不用紅。
            if (backupError != null) BackupErrorBox(backupError)
        }
        if (linkedAccount != null) {
            // 破壞性動作（中斷連結）固定在最底、滿版、DangerQuiet（手冊 §一、§八）
            VsButton(
                "中斷連結",
                { confirmingUnlink = true },
                Modifier.fillMaxWidth().padding(AppTheme.spacing.s4),
                variant = ButtonVariant.DangerQuiet,
            )
        }
    }

    if (confirmingUnlink) {
        AlertDialog(
            onDismissRequest = { confirmingUnlink = false },
            title = { Text("中斷連結？") },
            text = { Text("中斷後不會刪除 Drive 上已經備份的檔案，但這台裝置不會再自動備份。") },
            confirmButton = {
                VsButton("中斷連結", { confirmingUnlink = false; onUnlinkClick() }, variant = ButtonVariant.Danger)
            },
            dismissButton = { VsButton("取消", { confirmingUnlink = false }, variant = ButtonVariant.Quiet) },
        )
    }
}

/** 備份錯誤提示框：`warnWeak` 底、`warn` 色字與細框（原型的警示底色；紅色只留給破壞性動作）。 */
@Composable
private fun BackupErrorBox(message: String) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Text(
        message,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.s4)
            .padding(bottom = AppTheme.spacing.s4)
            .clip(shape)
            .background(AppTheme.colors.warnWeak)
            .border(1.dp, AppTheme.colors.warn, shape)
            .padding(AppTheme.spacing.s3),
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.warn,
    )
}
