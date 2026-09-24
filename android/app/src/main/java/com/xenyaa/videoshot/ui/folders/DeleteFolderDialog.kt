package com.xenyaa.videoshot.ui.folders

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 刪除確認。**明示範圍**（規格第六節）：子資料夾一起刪、圖不會被刪 ——
 * 不寫清楚的話，「刪掉分類會不會連圖一起沒了」是沒有人敢按下去的問題。
 */
@Composable
fun DeleteFolderDialog(card: FolderCard, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("刪除「${card.name}」") },
        text = { Text("子資料夾會一起刪除，圖片不會被刪除。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("刪除", color = AppTheme.colors.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
