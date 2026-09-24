package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 新增與改名共用。名稱的規則（同層不重名、上限 50 字）由 repo 判斷，
 * 失敗訊息回到 [FolderEditor.error] 顯示在欄位下面 —— 對話框不關，使用者才改得動。
 *
 * **用 `ModalBottomSheet`，不是 Material3 的 `AlertDialog`**：`AlertDialog` 底層是
 * `androidx.compose.ui.window.Dialog`——開一個真正的第二個 Android 視窗，裡面放一個可輸入的
 * 文字欄位時，Robolectric 的 idling 判斷會卡死成 `AppNotIdleException`（只在這組
 * compose-bom／Robolectric 版本重現，跟游標閃爍、`isError`／`supportingText`、Material3
 * 本身都無關）。但真正的理由不是「測試過不了」，是 `ModalBottomSheet` 本來就是這個 repo
 * 處理「文字輸入＋彈出層」的既有解法——`ShotEditSheet.kt` 已經把 `OutlinedTextField` 放進
 * `ModalBottomSheet`，保有 `Dialog` 少不了的東西：TalkBack 的模態範圍、IME 的 inset
 * 處理（小螢幕鍵盤彈起時輸入欄會自動讓位）。這一頁的排序抽屜本來就也是 `ModalBottomSheet`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderNameDialog(
    editor: FolderEditor,
    onName: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(bottom = AppTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
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
                // 顏色不寫死：isError=true 讓 Material3 自己解到 error token
                // （Theme.kt 已經把 error 對到 danger），跟 ShotEditSheet 的時間欄位同一個作法
                supportingText = editor.error?.let { { Text(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "資料夾名稱" },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = onConfirm, enabled = editor.name.isNotBlank()) {
                    Text(if (editor.target == null) "建立" else "儲存")
                }
            }
        }
    }
}
