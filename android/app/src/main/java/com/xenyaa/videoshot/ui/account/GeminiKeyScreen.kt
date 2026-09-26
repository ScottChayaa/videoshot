package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 帳號頁「查詢」子畫面：Gemini 金鑰輸入／清除(規格第九節；加密邏輯在 `AppSettings`，
 * 這裡只負責 UI)。**輸入框只用來輸入新金鑰，不會把已存的明文金鑰讀回來顯示**——
 * `AccountDeps.geminiKeySet` 本來就只回傳布林值，避免把明文金鑰握在 Compose 狀態裡。
 */
@Composable
fun GeminiKeyScreen(
    keySet: Boolean,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }

    Column(modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s4)) {
        AccountSettingHeader("查詢", onBack)
        Column(
            Modifier.padding(horizontal = AppTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            Row {
                Text(
                    "目前狀態：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.text,
                )
                Text(
                    if (keySet) "已設定" else "尚未設定",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.text,
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Gemini 金鑰") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Button(
                    onClick = { onSave(input); input = "" },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("儲存") }
                if (keySet) {
                    TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text("清除", color = AppTheme.colors.danger)
                    }
                }
            }
            Text(
                "只用於查詢解析、只存在這台手機；換手機要重新輸入。建議在 Google Cloud 把這把金鑰限縮為只能呼叫 Generative Language API。",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}
