package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

private data class StrengthOption(val value: FilterStrength, val label: String, val note: String)

private val Options = listOf(
    StrengthOption(FilterStrength.HIGH, "高", "砍得最多；連續的相似畫面幾乎只留一張"),
    StrengthOption(FilterStrength.MEDIUM, "中", "預設值；多數影片的平衡點"),
    StrengthOption(FilterStrength.LOW, "低", "只砍幾乎一模一樣的，寧可留多"),
)

/** 帳號頁「取圖」子畫面：過濾相似強度（規格第九節；取圖精靈第二步沿用這個值）。 */
@Composable
fun CaptureSettingScreen(
    current: FilterStrength,
    onBack: () -> Unit,
    onSelect: (FilterStrength) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AccountSettingHeader("取圖", onBack)
        Options.forEach { option ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .focusRing()
                    .clickable { onSelect(option.value) }
                    .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option.value == current, onClick = { onSelect(option.value) })
                    Text(option.label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
                }
                Text(
                    option.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(start = AppTheme.spacing.s5 + AppTheme.spacing.s2),
                )
            }
        }
        Text(
            "進入「挑畫面」時會先用這個強度收斂候選畫面，隨時可以在該頁按【顯示全部】看完整的候選。",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textDim,
            modifier = Modifier.padding(AppTheme.spacing.s4),
        )
    }
}
