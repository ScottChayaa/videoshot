package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 取圖步驟條（原型 `.wz-steps`）。[current] 從 0 起算；已完成的步驟可以點回去（有傳 [onStepClick] 時）。
 */
@Composable
fun VsStepIndicator(steps: List<String>, current: Int, modifier: Modifier = Modifier, onStepClick: ((Int) -> Unit)? = null) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = AppTheme.spacing.s4, end = AppTheme.spacing.s4,
                top = AppTheme.spacing.s2, bottom = AppTheme.spacing.s3,
            ),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            steps.forEachIndexed { i, label ->
                val done = i < current
                val now = i == current
                val canGoBack = done && onStepClick != null
                // 每段至少 44dp 高（可點的步驟觸控區達標，也讓各段的條高一致）；放在 clickable 之前才會被點擊區涵蓋
                var m = Modifier.weight(1f).defaultMinSize(minHeight = AppTheme.spacing.tap)
                if (canGoBack) m = m.focusRing().clickable(role = Role.Button) { onStepClick?.invoke(i) }
                Column(
                    m.semantics(mergeDescendants = true) { selected = now },
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(AppTheme.radii.full))
                            .background(if (done || now) AppTheme.colors.accent else AppTheme.colors.border),
                    )
                    Text(
                        (if (canGoBack) "‹ " else "") + "${i + 1}. $label",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (now) FontWeight.Bold else FontWeight.Normal),
                        color = when {
                            now || canGoBack -> AppTheme.colors.accent
                            done -> AppTheme.colors.textDim
                            else -> AppTheme.colors.textFaint
                        },
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
    }
}
