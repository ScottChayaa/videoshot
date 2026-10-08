package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.theme.AppTheme

data class MergeCandidate(val id: Long, val name: String, val count: Int, val icon: ImageVector, val iconTint: Color)

/** 合併目標挑選（16C）。地點與標籤共用；呼叫端負責把來源自己從 [candidates] 濾掉。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePickerSheet(
    sourceName: String,
    candidates: List<MergeCandidate>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(bottom = AppTheme.spacing.s5),
        ) {
            Text(
                "把「$sourceName」合併到…",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.text,
                modifier = Modifier.padding(bottom = AppTheme.spacing.s3),
            )
            // 候選是個人圖庫量級（數十到數百項），整列可捲動
            Column(Modifier.verticalScroll(rememberScrollState())) {
                candidates.forEach { c ->
                    VsListRow(
                        title = c.name,
                        subtitle = "${c.count} 張",
                        icon = c.icon,
                        iconTint = c.iconTint,
                        onClick = { onPick(c.id) },
                    )
                }
            }
        }
    }
}
