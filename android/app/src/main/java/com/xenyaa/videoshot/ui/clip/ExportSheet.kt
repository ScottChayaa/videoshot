package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.data.repo.model.UploadPrivacy
import com.xenyaa.videoshot.edit.exportSummary
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsDateField
import com.xenyaa.videoshot.ui.common.VsSelectField
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.common.YOUTUBE_TERMS_URL
import com.xenyaa.videoshot.ui.common.openSafely
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 編輯畫面【導出】的確認抽屜：一行摘要（長度・方向・會不會成為 Shorts，超過上限時紅字說明）、
 * 標題欄（預設標題由呼叫端給）、錄影日期（預設由呼叫端給，點了開月曆；沒有預設就不顯示）、
 * 公開狀態（下拉清單：跟欄位等寬、接在正下方，非公開／公開／私人，預設非公開；選私人時提醒接不回取圖）、
 * 「上傳即表示同意 YouTube 服務條款」（點了開條款）、【開始】。
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
    onStart: (title: String, privacy: UploadPrivacy, recordingDate: String?) -> Unit,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    defaultRecordingDate: String? = null,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var recordingDate by rememberSaveable { mutableStateOf(defaultRecordingDate) }
    var privacy by rememberSaveable { mutableStateOf(UploadPrivacy.UNLISTED) }
    var privacyMenuOpen by remember { mutableStateOf(false) }
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
                recordingDate?.let { date ->
                    VsDateField(
                        label = "錄影日期",
                        value = date,
                        onPick = { recordingDate = it },
                        modifier = Modifier.padding(top = AppTheme.spacing.s3),
                    )
                }
                // 下拉清單接在欄位正下方、跟欄位一樣寬，目前的那項有底色和勾（scott 2026-11-10 從三種做法裡選的）。
                // 開關由欄位自己的點擊負責：錨點只用來定位（enabled = false），兩邊都處理點擊會一開一關互相抵消
                ExposedDropdownMenuBox(
                    expanded = privacyMenuOpen,
                    onExpandedChange = { privacyMenuOpen = it },
                    modifier = Modifier.padding(top = AppTheme.spacing.s3),
                ) {
                    VsSelectField(
                        label = "公開狀態",
                        value = privacy.label,
                        onClick = { privacyMenuOpen = !privacyMenuOpen },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = false),
                    )
                    val shape = RoundedCornerShape(AppTheme.radii.sm)
                    ExposedDropdownMenu(
                        expanded = privacyMenuOpen,
                        onDismissRequest = { privacyMenuOpen = false },
                        shape = shape,
                        containerColor = AppTheme.colors.surface2,
                        border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
                    ) {
                        UploadPrivacy.entries.forEach { option ->
                            val chosen = option == privacy
                            DropdownMenuItem(
                                text = { Text(option.label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text) },
                                onClick = { privacy = option; privacyMenuOpen = false },
                                modifier = Modifier
                                    .background(if (chosen) AppTheme.colors.accentWeak else Color.Transparent)
                                    .semantics { selected = chosen },
                                trailingIcon = if (chosen) ({ Icon(VsIcons.Check, contentDescription = null, tint = AppTheme.colors.text) }) else null,
                            )
                        }
                    }
                }
                if (privacy == UploadPrivacy.PRIVATE) {
                    Text(
                        "私人影片無法在 app 裡取圖",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textDim,
                        modifier = Modifier.padding(top = AppTheme.spacing.s2),
                    )
                }
                // YouTube API 開發者政策：上傳的 app 要告知使用者同意 YouTube 服務條款、並給連結
                val uriHandler = LocalUriHandler.current
                Text(
                    buildAnnotatedString {
                        append("上傳即表示同意 ")
                        withStyle(SpanStyle(color = AppTheme.colors.accent)) { append("YouTube 服務條款") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier
                        .clickable(role = Role.Button) { uriHandler.openSafely(YOUTUBE_TERMS_URL) }
                        .padding(vertical = AppTheme.spacing.s2),
                )
                VsButton(
                    text = "開始",
                    onClick = { onStart(trimmed, privacy, recordingDate) },
                    enabled = !overLimit && trimmed.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s4),
                )
            }
            if (snackbarHostState != null) SnackbarHost(snackbarHostState)
        }
    }
}
