package com.xenyaa.videoshot.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 全新安裝的第一個畫面（規格第十節「還原」入口；手冊 §一：可選【從 Google Drive 還原】或【全新開始】）。 */
@Composable
fun FirstRunChooserScreen(
    onRestoreClick: () -> Unit,
    onStartFreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(AppTheme.spacing.s5),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("歡迎使用 videoshot", style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.text)
        Text(
            "如果你之前備份過圖庫，可以直接還原；也可以先略過，之後隨時能在帳號頁連結。",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRestoreClick, modifier = Modifier.fillMaxWidth()) { Text("從 Google Drive 還原") }
        TextButton(onClick = onStartFreshClick, modifier = Modifier.fillMaxWidth()) { Text("全新開始") }
    }
}
