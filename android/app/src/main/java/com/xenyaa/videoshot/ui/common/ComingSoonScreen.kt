package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 還沒做的分頁。
 *
 * **格子照留**（手冊 §零 要驗「五個項目都是圖示＋文字」），但點進來要說得出
 * 這一區是什麼、什麼時候會有 —— 空白畫面會被當成壞掉。
 */
@Composable
fun ComingSoonScreen(title: String, note: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppTheme.spacing.s5),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
    }
}
