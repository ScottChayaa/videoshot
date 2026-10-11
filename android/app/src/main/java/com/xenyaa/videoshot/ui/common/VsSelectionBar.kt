package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 多選動作列的一格。[enabled] 為 false 時變淡、不能按，TalkBack 唸「已停用」。 */
data class SelectionBarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * 多選時取代底部導覽的動作列（照片頁、相簿畫面、封存相簿畫面共用）。外殼這時藏起底部導覽，
 * 所以這一列自己鋪到系統導覽列底下。圖示在上、文字在下，平分寬度。
 * 高度、分隔線、字級都跟底部導覽一樣，兩者互換時畫面不跳。
 * 刪除是破壞性動作，但按下去還有確認框，這裡不用 danger 色（跟 Lightbox 的 ⋯ 選單一樣）。
 */
@Composable
fun VsSelectionBar(actions: List<SelectionBarAction>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.surface).navigationBarsPadding()) {
        HorizontalDivider(thickness = (1f / LocalDensity.current.density).dp, color = AppTheme.colors.border)
        Row(Modifier.fillMaxWidth().height(AppTheme.spacing.navHeight)) {
            for (action in actions) {
                SelectionBarCell(action, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SelectionBarCell(action: SelectionBarAction, modifier: Modifier) {
    // 停用時用 30% 的文字色：textFaint 跟 text 太接近，看不出不能按
    val color = if (action.enabled) AppTheme.colors.text else AppTheme.colors.text.copy(alpha = 0.3f)
    Column(
        modifier
            .focusRing()
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(action.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = color)
    }
}
