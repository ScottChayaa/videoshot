package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 光暈中心的不透明度（單純的透明黑）：越拉越深，過門檻後固定 20%。 */
fun archiveGlowAlpha(fraction: Float): Float = 0.20f * fraction.coerceIn(0f, 1f)

/** 內容縱向放大倍率：門檻前線性到 1.09，門檻後到 1.5 倍下拉距離再多 3%，最多 1.12。 */
fun archiveStretchScale(fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1.5f)
    return 1f + 0.09f * f.coerceAtMost(1f) + 0.06f * (f - 1f).coerceAtLeast(0f)
}

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
    // 每幀變動的 distanceFraction 只在 graphicsLayer（繪製階段）讀；組合階段只看跨門檻的布林，
    // 拖動途中不重組（AGENTS.md 慣例：抽屜／頁面每幀的工作要壓低）
    val visible by remember(state) { derivedStateOf { state.distanceFraction > 0f } }
    val ready by remember(state) { derivedStateOf { state.distanceFraction >= 1f } }
    if (!visible) return
    val tint = if (ready) AppTheme.colors.accent else AppTheme.colors.textDim
    Row(
        modifier
            .graphicsLayer {
                val threshold = PullToRefreshDefaults.PositionalThreshold.toPx()
                // 0：整顆藏在頂端外面；1：膠囊底緣剛好到門檻位置；超過門檻再往下多跟一點
                val fraction = state.distanceFraction
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

/**
 * 下拉時頂端的半橢圓光暈（Android 早期 overscroll glow 的樣子）：高度跟著下拉距離變長，
 * 過門檻後變亮。下拉距離只在繪製階段讀，拖動途中只重畫、不重組。
 * 呼叫端要放在有裁切的容器裡（相簿畫面的下拉容器有 `clipToBounds`），上半個橢圓才會被切掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePullGlow(state: PullToRefreshState, modifier: Modifier = Modifier) {
    Spacer(
        modifier.fillMaxSize().drawBehind {
            val fraction = state.distanceFraction
            if (fraction <= 0f) return@drawBehind
            val threshold = PullToRefreshDefaults.PositionalThreshold.toPx()
            // 半徑：門檻時約 0.9 個門檻高，超過門檻再慢慢長，最多 1.35 倍
            val radiusY = fraction.coerceAtMost(1.5f) * threshold * 0.9f
            val radiusX = size.width * 0.75f
            val peak = archiveGlowAlpha(fraction)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = peak), Color.Black.copy(alpha = peak * 0.4f), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = radiusX,
                ),
                topLeft = Offset(size.width / 2f - radiusX, -radiusY),
                size = Size(radiusX * 2f, radiusY * 2f),
            )
        },
    )
}

/**
 * 下拉時內容被往下拉長（Android 12 起 overscroll stretch 的樣子）：以頂端為基準縱向放大，
 * 門檻時拉長 9%，超過門檻再多一點，最多 12%；放手時跟著下拉距離一起彈回。
 * 只在 graphicsLayer（繪製階段）讀下拉距離，拖動途中不重組。
 */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.archivePullStretch(state: PullToRefreshState): Modifier = graphicsLayer {
    scaleY = archiveStretchScale(state.distanceFraction)
    transformOrigin = TransformOrigin(0.5f, 0f)
}
