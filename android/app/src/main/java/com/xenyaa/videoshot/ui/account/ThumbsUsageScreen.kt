package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.core.format.formatBytes
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 帳號頁「縮圖」子畫面。**本階段只有儲存用量**——回填進度是階段 13 的範圍
 * （規格第九節版面表：這一格同時要顯示回填進度，尚未實作的部分先不畫，不假裝已經有）。
 */
@Composable
fun ThumbsUsageScreen(usageBytes: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AccountSettingHeader("縮圖", onBack)
        Column(Modifier.padding(AppTheme.spacing.s4)) {
            Text("儲存用量", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
            Text(formatBytes(usageBytes), style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.text)
            Text(
                "已收藏的縮圖沒有容量上限；這裡只顯示目前佔用的空間。回填功能會在階段 13 上線。",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
                modifier = Modifier.padding(top = AppTheme.spacing.s2),
            )
        }
    }
}
