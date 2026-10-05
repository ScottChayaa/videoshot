package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.backfill.BackfillProgress
import com.xenyaa.videoshot.core.format.formatBytes
import com.xenyaa.videoshot.core.home.THUMB_COLUMN_CHOICES
import com.xenyaa.videoshot.core.home.DEFAULT_THUMB_COLUMNS
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsRadioRow
import com.xenyaa.videoshot.ui.common.VsSettingDivider
import com.xenyaa.videoshot.ui.common.VsSettingGroup
import com.xenyaa.videoshot.ui.common.VsSettingNote
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 帳號頁「縮圖」子畫面：縮圖每列張數 ＋ 儲存用量 ＋ 回填進度 ＋「無法取回」的處理（規格第九節版面表；
 * 手冊 §一「回填看得到進度」「抓不回來的縮圖」）。
 */
@Composable
fun ThumbsUsageScreen(
    usageBytes: Long,
    /** 縮圖牆手機寬度每列張數（2／3／4） */
    thumbColumns: Int,
    onSelectThumbColumns: (Int) -> Unit,
    backfillProgress: BackfillProgress,
    backfillActionError: String?,
    onBack: () -> Unit,
    onRetryLost: () -> Unit,
    onDeleteLost: () -> Unit,
    onContinueOnMobileData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        AccountSettingHeader("縮圖", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            VsSettingGroup(title = "縮圖每列張數") {
                // 同取圖子畫面：外層 selectableGroup 讓 TalkBack 把這幾列當成同一組單選
                Column(Modifier.selectableGroup()) {
                    THUMB_COLUMN_CHOICES.forEachIndexed { i, n ->
                        if (i > 0) VsSettingDivider()
                        VsRadioRow(
                            title = if (n == DEFAULT_THUMB_COLUMNS) "$n 張（預設）" else "$n 張",
                            selected = n == thumbColumns,
                            onSelect = { onSelectThumbColumns(n) },
                        )
                    }
                }
            }
            VsSettingNote("套用到首頁、查詢結果、資料夾內容。張數越多縮圖越小；平板會依寬度自動多 2～3 欄。")

            VsSettingGroup(title = "儲存用量") {
                Row(
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = AppTheme.spacing.tap)
                        .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
                ) {
                    Text("已使用", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
                    Text(formatBytes(usageBytes), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textDim)
                }
            }
            VsSettingNote("已收藏的縮圖沒有容量上限；這裡只顯示目前佔用的空間。")

            // 條件是「還有沒做完的」，不是「total > 0」——`BackfillProgress.total` 是
            // ok ＋ missing，而 ok 會一直累積（取圖精靈每裁成功一張也寫一列 ok），所以
            // 一般裝置上 total > 0 是永久成立的，用它當條件會讓「縮圖回填中 N / N」永遠
            // 掛在畫面上（全分支最終審查 Important 發現）。
            if (backfillProgress.total > backfillProgress.done) {
                VsSettingGroup {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                    ) {
                        Text(
                            "縮圖回填中 ${backfillProgress.done} / ${backfillProgress.total}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.text,
                        )
                        BackfillBar(backfillProgress.done, backfillProgress.total)
                        // 規格第四節：回填預設只在 Wi-Fi 下跑，要用行動網路得使用者每次明確同意。
                        VsButton(
                            text = "用行動網路繼續",
                            onClick = onContinueOnMobileData,
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Secondary,
                        )
                    }
                }
            }

            if (backfillProgress.lostCount > 0) {
                VsSettingGroup {
                    Text(
                        "無法取回 ${backfillProgress.lostCount} 張",
                        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.warn,
                    )
                    VsSettingDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                    ) {
                        VsButton("稍後重試", onRetryLost, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                        // 刪除收藏是破壞性動作:DangerQuiet(確認框另跳)
                        VsButton("刪除這些收藏", { confirmingDelete = true }, Modifier.weight(1f), variant = ButtonVariant.DangerQuiet)
                    }
                }
            }

            if (backfillActionError != null) {
                VsSettingNote(backfillActionError)
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("刪除這些收藏？") },
            text = { Text("這 ${backfillProgress.lostCount} 張已經確定抓不回原始畫面，刪除後圖資與標籤都會一併移除。") },
            confirmButton = {
                VsButton("刪除", { confirmingDelete = false; onDeleteLost() }, variant = ButtonVariant.Danger)
            },
            dismissButton = { VsButton("取消", { confirmingDelete = false }, variant = ButtonVariant.Quiet) },
        )
    }
}

/**
 * 細進度條(原型回填進度):4dp 高、`accentWeak` 軌道、`accent` 進度、全圓角。
 * 純裝飾——旁邊的「縮圖回填中 N / M」文字已經把進度唸出來了,所以不另外加語意(只留測試用 tag)。
 */
@Composable
private fun BackfillBar(done: Int, total: Int) {
    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AppTheme.colors.accentWeak).testTag("backfillProgressBar"),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(AppTheme.colors.accent, CircleShape))
    }
}
