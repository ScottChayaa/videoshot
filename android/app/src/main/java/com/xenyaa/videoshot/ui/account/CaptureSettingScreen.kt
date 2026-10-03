package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.ui.common.VsRadioRow
import com.xenyaa.videoshot.ui.common.VsSettingDivider
import com.xenyaa.videoshot.ui.common.VsSettingGroup
import com.xenyaa.videoshot.ui.common.VsSettingNote

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
        VsSettingGroup(title = "過濾相似強度") {
            // 單選列整列可點、Role.RadioButton、selected 進語意(VsRadioRow);
            // 外層 selectableGroup 讓 TalkBack 把三列當成同一組單選。
            Column(Modifier.selectableGroup()) {
                Options.forEachIndexed { i, option ->
                    if (i > 0) VsSettingDivider()
                    VsRadioRow(
                        title = option.label,
                        selected = option.value == current,
                        onSelect = { onSelect(option.value) },
                        subtitle = option.note,
                    )
                }
            }
        }
        VsSettingNote("進入「挑畫面」時會先用這個強度收斂候選畫面，隨時可以在該頁按【顯示全部】看完整的候選。")
    }
}
