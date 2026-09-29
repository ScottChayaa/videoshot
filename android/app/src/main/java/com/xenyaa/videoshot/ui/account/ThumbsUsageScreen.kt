package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.xenyaa.videoshot.backfill.BackfillProgress
import com.xenyaa.videoshot.core.format.formatBytes
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 帳號頁「縮圖」子畫面：儲存用量 ＋ 回填進度 ＋「無法取回」的處理（規格第九節版面表；
 * 手冊 §一「回填看得到進度」「抓不回來的縮圖」）。
 */
@Composable
fun ThumbsUsageScreen(
    usageBytes: Long,
    backfillProgress: BackfillProgress,
    backfillActionError: String?,
    onBack: () -> Unit,
    onRetryLost: () -> Unit,
    onDeleteLost: () -> Unit,
    onContinueOnMobileData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        AccountSettingHeader("縮圖", onBack)
        Column(
            Modifier.padding(AppTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            Text("儲存用量", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            Text(formatBytes(usageBytes), style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.text)
            Text(
                "已收藏的縮圖沒有容量上限；這裡只顯示目前佔用的空間。",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
            )

            // 條件是「還有沒做完的」，不是「total > 0」——`BackfillProgress.total` 是
            // ok ＋ missing，而 ok 會一直累積（取圖精靈每裁成功一張也寫一列 ok），所以
            // 一般裝置上 total > 0 是永久成立的，用它當條件會讓「縮圖回填中 N / N」永遠
            // 掛在畫面上（全分支最終審查 Important 發現）。
            if (backfillProgress.total > backfillProgress.done) {
                Text(
                    "縮圖回填中 ${backfillProgress.done} / ${backfillProgress.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.text,
                    modifier = Modifier.padding(top = AppTheme.spacing.s2),
                )
                // 規格第四節：回填預設只在 Wi-Fi 下跑，要用行動網路得使用者每次明確同意。
                TextButton(onClick = onContinueOnMobileData) { Text("用行動網路繼續") }
            }

            if (backfillProgress.lostCount > 0) {
                Text(
                    "無法取回 ${backfillProgress.lostCount} 張",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.danger,
                    modifier = Modifier.padding(top = AppTheme.spacing.s2),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    Button(onClick = onRetryLost, modifier = Modifier.weight(1f)) { Text("稍後重試") }
                    TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.weight(1f)) {
                        Text("刪除這些收藏", color = AppTheme.colors.danger)
                    }
                }
            }

            if (backfillActionError != null) {
                Text(backfillActionError, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger)
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("刪除這些收藏？") },
            text = { Text("這 ${backfillProgress.lostCount} 張已經確定抓不回原始畫面，刪除後圖資與標籤都會一併移除。") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDeleteLost() }) {
                    Text("刪除", color = AppTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } },
        )
    }
}
