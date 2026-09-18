package com.xenyaa.videoshot.ui.folders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 新增與改名共用。名稱的規則（同層不重名、上限 50 字）由 repo 判斷，
 * 失敗訊息回到 [FolderEditor.error] 顯示在欄位下面 —— 對話框不關，使用者才改得動。
 *
 * **刻意不用 Material3 的 `AlertDialog`**：那個底層是 `androidx.compose.ui.window.Dialog`
 * ——開一個真正的第二個 Android 視窗。只要裡面放一個可輸入的文字欄位，
 * Robolectric 的 idling 判斷就會卡死成 `AppNotIdleException`（跑到逾時才報錯，不是編譯或邏輯錯誤）。
 * 排除過游標閃爍、`isError`／`supportingText`、Material3 本身、`GraphicsMode`——
 * 連拿掉 Material3、只用最原始的 `androidx.compose.ui.window.Dialog` ＋ `BasicTextField(readOnly = true)`
 * 都一樣卡死，唯一的共同點是「文字輸入節點＋另一個視窗」。改成疊在**同一個視窗**裡的
 * scrim ＋ 卡片，視覺效果一樣（置中卡片、背景變暗、點背景關閉），系統返回鍵改用 [BackHandler] 接住。
 */
@Composable
fun FolderNameDialog(
    editor: FolderEditor,
    onName: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(AppTheme.spacing.s5)
                .widthIn(min = 280.dp)
                // 吃掉點擊，不要冒泡到背景那層把對話框自己關掉
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(AppTheme.radii.md),
            color = AppTheme.colors.surface,
        ) {
            Column(Modifier.padding(AppTheme.spacing.s4)) {
                Text(
                    if (editor.target == null) "新增資料夾" else "資料夾改名",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.text,
                )
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onName,
                    singleLine = true,
                    isError = editor.error != null,
                    supportingText = editor.error?.let { { Text(it, color = AppTheme.colors.danger) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppTheme.spacing.s3)
                        .semantics { contentDescription = "資料夾名稱" },
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s4),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onConfirm, enabled = editor.name.isNotBlank()) {
                        Text(if (editor.target == null) "建立" else "儲存")
                    }
                }
            }
        }
    }
}
