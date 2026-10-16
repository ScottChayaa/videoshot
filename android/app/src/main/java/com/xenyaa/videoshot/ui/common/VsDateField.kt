package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.details.eventDateFromUtcMillis
import com.xenyaa.videoshot.core.details.eventDateToUtcMillis
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 多張的值不一致時欄位裡的字（同 [VsTextField] 的 mixedPlaceholder）。 */
private const val MIXED_TEXT = "〈多個值〉"

/**
 * 日期欄位（`event_date`）：外觀跟 [VsTextField] 一樣（標籤在上、同樣的框與尺寸），值顯示成「2026年8月3日」，
 * 右側月曆圖示。點了跳 Material 3 月曆對話框，【確定】才 [onPick]（`YYYY-MM-DD`），【取消】或點外面不變。
 * 不能打字，所以不會有格式錯誤的日期。
 *
 * @param value `YYYY-MM-DD`；[mixed] 時忽略
 * @param mixed 多張的日期不一致（取圖第三步、批次編輯）：顯示〈多個值〉、月曆停在今天且沒有預選，選了一天才能按【確定】
 * @param labelAccent 見 [VsTextField]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VsDateField(
    label: String,
    value: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    mixed: Boolean = false,
    labelAccent: Boolean? = null,
    size: TextFieldSize = TextFieldSize.Regular,
) {
    val colors = AppTheme.colors
    val dense = size == TextFieldSize.Dense
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    val shown = if (mixed) MIXED_TEXT else chineseDate(value)
    var picking by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        VsFieldLabel(label, labelAccent)
        Row(
            Modifier.fillMaxWidth()
                .focusRing(shape)
                .clip(shape)
                .clickable(role = Role.Button) { picking = true }
                // 上方的標籤文字已在這裡唸過，整格唸「時間：2026年8月3日」
                .semantics { contentDescription = "$label：$shown" }
                .background(if (dense) colors.surface2 else colors.surface, shape)
                .border(1.dp, if (dense) colors.border else colors.borderStrong, shape)
                .defaultMinSize(minHeight = AppTheme.spacing.tap)
                .padding(horizontal = AppTheme.spacing.s3, vertical = if (dense) AppTheme.spacing.s2 else AppTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            val textStyle = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
            Text(
                shown,
                Modifier.weight(1f).clearAndSetSemantics {},
                style = textStyle.copy(
                    color = if (mixed) colors.textDim else colors.text,
                    fontStyle = if (mixed) FontStyle.Italic else FontStyle.Normal,
                ),
                maxLines = 1,
            )
            Icon(VsIcons.Calendar, null, tint = colors.textDim, modifier = Modifier.size(20.dp))
        }
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = if (mixed) null else eventDateToUtcMillis(value))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onPick(eventDateFromUtcMillis(it)) }
                        picking = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } },
        ) {
            DatePicker(state)
        }
    }
}
