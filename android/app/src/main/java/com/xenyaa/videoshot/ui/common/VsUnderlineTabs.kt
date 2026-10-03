package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 底線分頁（原型 `.qtabs`）。選中那頁字體加粗、下方 3dp 主色底線。
 * 每個分頁最小寬 44dp（兩個字的標籤也有夠大的觸控區）；外層是 `selectableGroup`，TalkBack 會唸「第 N 個，共 M 個」。
 */
@Composable
fun VsUnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = AppTheme.colors.accent
    Column(modifier.fillMaxWidth().background(AppTheme.colors.bg)) {
        Row(Modifier.selectableGroup().padding(horizontal = AppTheme.spacing.s4), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s5)) {
            tabs.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .focusRing()
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                        .defaultMinSize(minWidth = AppTheme.spacing.tap, minHeight = AppTheme.spacing.tap)
                        .drawBehind {
                            if (on) {
                                val h = 3.dp.toPx()
                                drawRect(accent, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                        color = if (on) AppTheme.colors.text else AppTheme.colors.textDim,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
    }
}
