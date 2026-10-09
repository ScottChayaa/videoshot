package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 底部抽屜標題列的固定高度：右邊有沒有按鈕都一樣高（剛好容得下 44dp 的觸控區）。 */
val SheetHeaderHeight = 48.dp

/**
 * 文字樣式的動作鈕（抽屜標題列右邊的【清除】）：能按時主色＋底線，不能按時灰字、不可點。
 * 觸控區至少 44dp，有焦點框。
 */
@Composable
fun VsTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Box(
        modifier
            .defaultMinSize(minWidth = AppTheme.spacing.tap, minHeight = AppTheme.spacing.tap)
            .focusRing(shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AppTheme.spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (enabled) TextDecoration.Underline else TextDecoration.None,
            ),
            color = if (enabled) AppTheme.colors.accent else AppTheme.colors.textDim,
        )
    }
}

/**
 * 底部抽屜的標題列（首頁的篩選抽屜、月份選擇器）：固定高度、左邊標題、右邊可放一顆動作鈕，
 * 下面一條一個實體像素的分隔線（跟 [VsBottomNav] 上緣同一種寫法）。
 *
 * 高度固定是因為右邊的按鈕有 44dp 觸控區：原本標題列跟著內容長，有按鈕的抽屜（篩選）比沒有按鈕的
 * （月份選擇器）高一截，月份選擇器選了月份、出現【清除】時自己也會變高。
 */
@Composable
fun VsSheetHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(SheetHeaderHeight).padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            action?.invoke(this)
        }
        HorizontalDivider(thickness = (1f / LocalDensity.current.density).dp, color = AppTheme.colors.border)
    }
}
