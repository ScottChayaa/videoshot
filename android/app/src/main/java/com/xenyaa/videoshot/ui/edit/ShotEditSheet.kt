package com.xenyaa.videoshot.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.details.isValidEventDate
import com.xenyaa.videoshot.core.details.normalizeTags
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 就地編輯**一張**圖的圖資。Lightbox 的【編輯圖資】與階段 9 詳情頁的【編輯這張的圖資】共用
 * （主計畫第 284 行）。
 *
 * 送出的是 [DetailsPatch]：**只帶動過的欄位**，沒動過的是 null。
 * 雖然這裡只有一張圖、大可整份覆蓋，但用同一個型別才能和精靈第三步、
 * 階段 9 的批次編輯走同一條寫入路徑（`LibraryRepo.patchShots`）。
 *
 * 空字串＝清空，與 `:core` 的 `DetailsPatch` 同一個約定。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShotEditSheet(
    details: ShotDetails,
    placeSuggestions: List<String>,
    tagSuggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (DetailsPatch) -> Unit,
) {
    var eventDate by remember(details) { mutableStateOf(details.eventDate) }
    var place by remember(details) { mutableStateOf(details.place.orEmpty()) }
    var description by remember(details) { mutableStateOf(details.description.orEmpty()) }
    var tags by remember(details) { mutableStateOf(details.tags) }
    var typing by remember(details) { mutableStateOf("") }

    // 時間欄位是自由文字，沒有這一關的話「清空」或打錯格式都能一路送到 library.db ——
    // 那張圖之後就沒有任何月份篩選找得到它（跟 :core 的 eventDateOf 用同一個判斷）
    val dateValid = isValidEventDate(eventDate)

    // 「動過沒有」是拿現值跟原值比，不是記使用者按過幾次鍵 ——
    // 改完又改回來的話，那就是沒動過
    val patch = DetailsPatch(
        eventDate = eventDate.takeIf { it != details.eventDate },
        place = place.takeIf { it != details.place.orEmpty() },
        description = description.takeIf { it != details.description.orEmpty() },
        tags = normalizeTags(tags).takeIf { it != normalizeTags(details.tags) },
    )

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
            Text("編輯圖資", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
            ) {
                OutlinedTextField(
                    value = eventDate,
                    onValueChange = { eventDate = it },
                    label = { Text("時間") },
                    singleLine = true,
                    isError = !dateValid,
                    supportingText = {
                        // 格式不對才是警告；平常是中性說明 —— 預設值永遠是上傳日，這是常態不是例外
                        if (dateValid) {
                            Text("預設帶入 YouTube 的上傳日期，可以改成實際拍攝日")
                        } else {
                            Text("格式要是 YYYY-MM-DD，例如 2026-01-09")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "時間" },
                )

                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("地點") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "地點" },
                )
                Suggestions(placeSuggestions.filterNot { it == place }) { place = it }

                Text("標籤", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
                    tags.forEach { tag ->
                        InputChip(selected = true, onClick = { tags = tags - tag }, label = { Text(tag) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = typing,
                        onValueChange = { typing = it },
                        label = { Text("新增標籤") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "新增標籤" },
                    )
                    TextButton(onClick = {
                        if (typing.isNotBlank()) {
                            tags = normalizeTags(tags + typing)
                            typing = ""
                        }
                    }) { Text("＋") }
                }
                Suggestions(tagSuggestions.filterNot { it in tags }) { tags = normalizeTags(tags + it) }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    supportingText = { Text("留空，之後 AI 補") },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "描述" },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onSave(patch) },
                    enabled = !patch.isEmpty && dateValid,
                    modifier = Modifier.weight(1f),
                ) { Text("儲存") }
            }
        }
    }
}

/** 既有值建議。點一下就填進欄位。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(values: List<String>, onPick: (String) -> Unit) {
    if (values.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
        values.forEach { value -> AssistChip(onClick = { onPick(value) }, label = { Text(value) }) }
    }
}
