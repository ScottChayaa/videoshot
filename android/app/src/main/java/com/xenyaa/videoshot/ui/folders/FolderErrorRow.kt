package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 讀取失敗的提示列＋重試。清單頁與資料夾頁共用同一個元件——兩邊原本各自判斷空狀態，
 * 讀取失敗時 `cards`／`items` 都是空的，不接住 `error` 就會被誤判成「真的是空的」，
 * 對使用者主動說錯話（見階段 8 全盤覆查 N2；資料夾頁那邊先修過一次，這裡補齊清單頁）。
 *
 * 提示＋重試不是破壞性動作，不用 danger 色。
 */
@Composable
fun FolderErrorRow(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("重試") }
    }
}
