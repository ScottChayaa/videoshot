package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.core.home.monthLabel
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsTextAction
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 月份清單的高度上限——月份一多，選擇器要停在「底部工作表」的樣子，
 * 不能一路長成全螢幕清單。只有這一個用到，不是間距刻度（`AppTheme.spacing` 管的是
 * 4/8/12/16/24/32 的間距），所以就地宣告，不升格成主題裡的正式 token。
 */
private val MonthListMaxHeight = 360.dp

/**
 * 「只顯示這個月以前」的月份選擇器（手冊 §二第四條）。
 *
 * 選項是**實際有收藏的月份**，不帶張數（階段 17 設計決議 5）—— 列出沒有資料的月份只會讓人選到空畫面。
 * 首頁有地點／標籤篩選時，[months] 只含「選取項目在那個月有圖」的月份。
 *
 * @param filtering 首頁正套用地點／標籤篩選；[months] 是空的時候據此說明「是篩選濾掉的」，
 *        而不是「圖庫沒有任何月份」
 *
 * `skipPartiallyExpanded = true`：這張清單不長，半開狀態只會多一次滑動；
 * 順帶也讓 Robolectric 不必等那段展開動畫。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerSheet(
    months: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    filtering: Boolean = false,
) {
    VsBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
            VsSheetHeader("只顯示這個月以前") {
                if (selected != null) VsTextAction("清除", onClear)
            }
            if (months.isEmpty()) {
                // 沒有月份可選，給一句話收尾就好，不必另做一套空狀態設計；
                // 篩選中說成「沒有符合篩選的月份」，不要讓人以為圖庫是空的
                Text(
                    if (filtering) "沒有符合篩選的月份" else "還沒有任何照片的月份",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = MonthListMaxHeight)) {
                    items(months, key = { it }) { month ->
                        Text(
                            monthLabel(month),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (month == selected) AppTheme.colors.accent else AppTheme.colors.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                // 焦點框（手冊 §零）——實機上用 Tab 走進選擇器時看得到
                                .focusRing(RoundedCornerShape(AppTheme.radii.sm))
                                .clickable { onPick(month) }
                                .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                        )
                    }
                }
            }
        }
    }
}
