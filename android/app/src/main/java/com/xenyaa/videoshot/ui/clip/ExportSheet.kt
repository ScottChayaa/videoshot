package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.data.repo.model.UploadPrivacy
import com.xenyaa.videoshot.edit.exportSummary
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsRadioRow
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 編輯畫面【導出】的確認抽屜：一行摘要（長度・方向・會不會成為 Shorts，超過上限時紅字說明）、
 * 標題欄（預設標題由呼叫端給）、公開狀態（非公開／公開／私人，預設非公開；選私人時提醒接不回取圖）、【開始】。
 *
 * 【開始】在超過上限或標題（修剪空白後）是空的時候停用；回報的標題已修剪過空白。
 * 抽屜由呼叫端關（[onStart] 之後把開關狀態設回 false），這裡不自己關——建立任務可能失敗，失敗時抽屜要留著。
 * [snackbarHostState]：失敗訊息要顯示在抽屜裡面（編輯畫面底下的 snackbar 會被抽屜蓋住）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    orientation: ClipOrientation,
    durationMs: Long,
    defaultTitle: String,
    onStart: (title: String, privacy: UploadPrivacy) -> Unit,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var privacy by rememberSaveable { mutableStateOf(UploadPrivacy.UNLISTED) }
    val (summary, overLimit) = exportSummary(orientation, durationMs)
    val trimmed = title.trim()

    VsBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
            VsSheetHeader("導出")
            Column(Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3)) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overLimit) AppTheme.colors.danger else AppTheme.colors.textDim,
                )
                VsTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "標題",
                    modifier = Modifier.padding(top = AppTheme.spacing.s3),
                )
                Text(
                    "公開狀態",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(top = AppTheme.spacing.s4),
                )
            }
            // 單選列自己帶左右內距，放在內距那一層外面才對齊
            UploadPrivacy.entries.forEach { option ->
                VsRadioRow(option.label, selected = privacy == option, onSelect = { privacy = option })
            }
            Column(Modifier.padding(horizontal = AppTheme.spacing.s4)) {
                if (privacy == UploadPrivacy.PRIVATE) {
                    Text(
                        "私人影片無法在 app 裡取圖",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textDim,
                    )
                }
                VsButton(
                    text = "開始",
                    onClick = { onStart(trimmed, privacy) },
                    enabled = !overLimit && trimmed.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s4),
                )
            }
            if (snackbarHostState != null) SnackbarHost(snackbarHostState)
        }
    }
}
