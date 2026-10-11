package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 下拉提示的文字：沒過門檻「下拉…」，過了門檻「放開…」（規格第六節「進入封存相簿」）。 */
fun archivePullLabel(ready: Boolean): String = if (ready) "放開進入封存相簿" else "下拉進入封存相簿"

/**
 * 取代 `PullToRefreshBox` 預設的轉圈：膠囊（封存圖示＋文字）跟著手指往下移，
 * 到門檻時剛好完整露出、圖示與文字變主色。沒在拉的時候整個不畫。
 *
 * 放手後不顯示轉圈：呼叫端的 `isRefreshing` 永遠是 false，`onRefresh` 直接換頁。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePullIndicator(state: PullToRefreshState, modifier: Modifier = Modifier) {
    val fraction = state.distanceFraction
    if (fraction <= 0f) return
    val ready = fraction >= 1f
    val tint = if (ready) AppTheme.colors.accent else AppTheme.colors.textDim
    Row(
        modifier
            .graphicsLayer {
                val threshold = PullToRefreshDefaults.PositionalThreshold.toPx()
                // 0：整顆藏在頂端外面；1：膠囊底緣剛好到門檻位置；超過門檻再往下多跟一點
                translationY = fraction.coerceAtMost(1.5f) * threshold - size.height
                alpha = fraction.coerceIn(0f, 1f)
            }
            .shadow(2.dp, CircleShape)
            .background(AppTheme.colors.surface, CircleShape)
            .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
    ) {
        Icon(VsIcons.Archive, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            archivePullLabel(ready),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ready) AppTheme.colors.accent else AppTheme.colors.text,
        )
    }
}
