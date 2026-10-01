package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 底部動作列（原型 `.sticky-actions`／`.wz-foot`／`.wz-dock .dock-foot`）。鍵盤打開時浮在鍵盤上方。
 * [status] 放左側（佔剩餘寬度）、[content] 放右側；沒有 [status] 時 [content] 撐滿，
 * 呼叫端用 `VsButton(…, Modifier.weight(1f))` 撐開按鈕。
 * 自己處理 `navigationBarsPadding()`；放在導覽列上方的頁面（查詢）由呼叫端決定擺哪，元件不知道有沒有導覽列。
 */
@Composable
fun VsActionDock(
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            if (status != null) {
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalContentColor provides AppTheme.colors.textDim) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { status() }
                    }
                }
                content()
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3)) { content() }
            }
        }
    }
}
