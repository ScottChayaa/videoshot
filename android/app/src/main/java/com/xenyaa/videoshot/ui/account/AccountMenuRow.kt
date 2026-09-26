package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 帳號頁選單的一列：圖示 ＋ 名稱 ＋ 一行說明 ＋ 箭頭。名稱與說明對得上規格第九節的版面表。 */
@Composable
fun AccountMenuRow(icon: ImageVector, name: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .focusRing(RoundedCornerShape(AppTheme.spacing.s2))
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.spacing.s3, horizontal = AppTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.textDim)
        Column(Modifier.weight(1f).padding(horizontal = AppTheme.spacing.s3)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            Text(value, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textDim)
        }
        Icon(VsIcons.ChevronRight, contentDescription = null, tint = AppTheme.colors.textFaint)
    }
}
