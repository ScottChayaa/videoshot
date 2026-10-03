package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 群組內是否畫列與列之間的分隔線（[VsSettingGroup] 的 `divided`）。 */
private val LocalSettingDivided = compositionLocalOf { true }

/**
 * 設定頁的列群組（原型 `.set-group`）：`surface` 底、上下各 1dp `border`、群組下方留 24。
 * [title] 是群組小標（13 Medium `textDim`，內距 12/16/4）。
 *
 * 列與列之間的分隔線由呼叫端在列之間放 [VsSettingDivider]；[divided] 為 `false` 時
 * 群組裡的 [VsSettingDivider] 全部不畫（原型沒有 `.split` 的群組）。
 */
@Composable
fun VsSettingGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    divided: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface)) {
            HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
            if (title != null) {
                Text(
                    title,
                    Modifier.semantics { heading() }
                        .padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, top = AppTheme.spacing.s3, bottom = AppTheme.spacing.s1),
                    style = MaterialTheme.typography.labelMedium, // 13 Medium
                    color = AppTheme.colors.textDim,
                )
            }
            CompositionLocalProvider(LocalSettingDivided provides divided) { content() }
            HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
        }
    }
}

/** 群組裡兩列之間的 1dp 分隔線（原型 `.set-group.split .set-row + .set-row`）。 */
@Composable
fun VsSettingDivider(modifier: Modifier = Modifier) {
    if (LocalSettingDivided.current) HorizontalDivider(modifier, thickness = 1.dp, color = AppTheme.colors.border)
}

/** 設定群組下方的說明文字（原型 `.set-note`）：15 `textDim`、內距 0/16/16。 */
@Composable
fun VsSettingNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(PaddingValues(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, bottom = AppTheme.spacing.s4)),
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textDim,
    )
}

/**
 * 單選列（原型 `.set-row.pick`）：整列可點、`Role.RadioButton`、`selected` 進語意，
 * 右側 22dp 圓形選取記號（主色）。[subtitle] 是主標下方的 15 `textDim` 說明。
 */
@Composable
fun VsRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .focusRing()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            if (subtitle != null) {
                Text(
                    subtitle,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textDim,
                )
            }
        }
        // 22dp 圓形記號：選中＝主色環＋中心實心點，沒選＝灰環
        Box(
            Modifier.size(22.dp).border(2.dp, if (selected) AppTheme.colors.accent else AppTheme.colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(AppTheme.colors.accent, CircleShape))
        }
    }
}

/**
 * 步進器（原型 `.stepper`）：−／＋ 兩顆 44×44 `surface2` 底、1dp `border`、圓角 8、字 20 `accent`；
 * 中間數值最小寬 48、Bold、等寬數字。到 [range] 邊界時對應的鈕停用。
 * TalkBack：−「減少 {label}」、＋「增加 {label}」，數值節點唸「{label} {value}」。
 */
@Composable
fun VsStepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
        StepperButton("−", "減少 $label", enabled = value > range.first) { onChange(value - 1) }
        Text(
            value.toString(),
            Modifier.widthIn(min = 48.dp).clearAndSetSemantics { contentDescription = "$label $value" },
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = AppTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        StepperButton("＋", "增加 $label", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Box(
        Modifier.size(AppTheme.spacing.tap)
            .alpha(if (enabled) 1f else 0.4f)
            .focusRing(shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .background(AppTheme.colors.surface2, shape)
            .border(1.dp, AppTheme.colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        // 符號不進語意：TalkBack 只唸 contentDescription，不要再多唸一個「減號」
        Text(
            symbol,
            Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.titleMedium, // 20
            color = AppTheme.colors.accent,
        )
    }
}
