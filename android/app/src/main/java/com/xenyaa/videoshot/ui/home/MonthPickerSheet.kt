package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.home.monthLabel
import com.xenyaa.videoshot.data.repo.model.MonthCount
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 「只顯示這個月以前」的月份選擇器（手冊 §二第四條）。
 *
 * 選項是**實際有收藏的月份**加上張數 —— 列出沒有資料的月份只會讓人選到空畫面。
 *
 * `skipPartiallyExpanded = true`：這張清單不長，半開狀態只會多一次滑動；
 * 順帶也讓 Robolectric 不必等那段展開動畫。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerSheet(
    months: List<MonthCount>,
    selected: String?,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "只顯示這個月以前",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                if (selected != null) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(months, key = { it.month }) { month ->
                    Text(
                        "${monthLabel(month.month)} · ${month.count} 張",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (month.month == selected) AppTheme.colors.accent else AppTheme.colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(month.month) }
                            .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                    )
                }
            }
        }
    }
}
