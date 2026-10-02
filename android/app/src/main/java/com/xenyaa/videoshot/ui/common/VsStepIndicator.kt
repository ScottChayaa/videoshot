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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 取圖步驟條（原型 `.wz-steps`）。[current] 從 0 起算；有傳 [onStepClick] 時，
 * 除了目前步驟，`i <= furthest` 的步驟都可以點（[furthest] 預設等於 [current]＝只能往回點）：
 * 往回的前面加「‹ 」（只是視覺），往後（已到達過）的不加前綴、文字用 `textDim`。
 * 每段用 `clearAndSetSemantics` 自己決定 TalkBack 名稱，讓「‹」不被唸出來。
 */
@Composable
fun VsStepIndicator(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
    furthest: Int = current,
    onStepClick: ((Int) -> Unit)? = null,
) {
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
                val clickable = onStepClick != null && !now && i <= furthest
                val canGoBack = clickable && done
                val canGoForward = clickable && !done
                val name = "${i + 1}. $label"
                val spoken = when {
                    canGoBack -> "回到 $name"
                    canGoForward -> "前往 $name"
                    else -> name
                }
                // 每段至少 44dp 高（可點的步驟觸控區達標，也讓各段的條高一致）；放在 clickable 之前才會被點擊區涵蓋
                var m = Modifier.weight(1f).defaultMinSize(minHeight = AppTheme.spacing.tap)
                if (clickable) m = m.focusRing().clickable(role = Role.Button) { onStepClick?.invoke(i) }
                Column(
                    m.clearAndSetSemantics {
                        contentDescription = spoken
                        // 保留文字節點，讓以文字定位的測試與 UI 自動化仍找得到這一段
                        text = AnnotatedString(name)
                        selected = now
                        if (clickable) onClick(label = spoken) { onStepClick?.invoke(i); true }
                    },
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(AppTheme.radii.full))
                            .background(if (done || now) AppTheme.colors.accent else AppTheme.colors.border),
                    )
                    Text(
                        (if (canGoBack) "‹ " else "") + name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (now) FontWeight.Bold else FontWeight.Normal),
                        color = when {
                            now || canGoBack -> AppTheme.colors.accent
                            done || canGoForward -> AppTheme.colors.textDim
                            else -> AppTheme.colors.textFaint
                        },
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
    }
}
