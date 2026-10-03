package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.common.VsSettingDivider
import com.xenyaa.videoshot.ui.common.VsSettingGroup
import com.xenyaa.videoshot.ui.common.VsSettingNote
import com.xenyaa.videoshot.ui.common.VsStepper
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 帳號頁「AI 分析」子畫面：往前／往後秒數（規格第九節；顯示但第四步上線後才生效）。
 * `onChange` 一次帶完整組值——`AccountViewModel.setAiRange` 只有一個方法同時存兩個數字。
 */
@Composable
fun AiRangeScreen(
    beforeSec: Int,
    afterSec: Int,
    onBack: () -> Unit,
    onChange: (beforeSec: Int, afterSec: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        AccountSettingHeader("AI 分析", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            VsSettingGroup(title = "分析區間") {
                StepperRow("往前秒數", "送 AI 分析時，從時間點往前涵蓋的秒數", beforeSec) { onChange(it, afterSec) }
                VsSettingDivider()
                StepperRow("往後秒數", "往後涵蓋的秒數", afterSec) { onChange(beforeSec, it) }
            }
            VsSettingNote("AI 補充功能還沒上線，這裡的設定會先存著，上線後才會送出分析。")
        }
    }
}

/**
 * 一列「名稱／說明 ＋ 步進器」。下限 0、沒有上限，跟 `AppSettings.setAiRange`／
 * `AccountViewModel.setAiRange` 的 `coerceAtLeast(0)` 一致；每按一下就回呼新值（存檔時機不變）。
 * 步進器鈕的 TalkBack 名稱沿用舊的「{名稱}減少／{名稱}增加」。
 * 這裡不用 `VsListRow`:它的副標限一行會把較長的說明截掉。
 */
@Composable
private fun StepperRow(label: String, note: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            Text(
                note,
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
            )
        }
        VsStepper(
            value = value,
            onChange = onChange,
            range = 0..Int.MAX_VALUE,
            label = label,
            decreaseDescription = "${label}減少",
            increaseDescription = "${label}增加",
        )
    }
}
