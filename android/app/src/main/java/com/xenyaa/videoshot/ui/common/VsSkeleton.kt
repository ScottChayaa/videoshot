package com.xenyaa.videoshot.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 亮光掃一趟的時間與亮帶寬度（中心最亮、往兩側各 [ShimmerBandWidth] 淡出）。 */
private const val ShimmerDurationMs = 1200
private val ShimmerBandWidth = 80.dp

/** 骨架色塊的寬度輪流取這幾個，看起來像長短不一的名字，而不是一排一樣的方塊。 */
private val SkeletonChipWidths = listOf(56.dp, 84.dp, 64.dp, 100.dp, 48.dp, 76.dp, 92.dp, 60.dp)

/**
 * 小膠囊的骨架屏：資料還沒畫上去之前，先放一排長短不一的灰色圓角色塊，一道亮光由左往右掃過
 * （shimmer，跟 YouTube 載入時一樣）。
 *
 * 每塊佔一個 [VsTagChip] 的高度（44dp 觸控區、色塊置中），換成真的小膠囊時列高不會跳。
 * 亮光是**整區一道**、連續掃過每一塊（不是每塊各自閃）：整個 FlowRow 畫在離屏圖層上，色塊畫完後疊一條
 * 漸層、用 `SrcAtop` 只留下落在色塊上的部分。位置在繪製階段讀值，不會每一幀重組。
 * TalkBack 把整塊當成一個節點，唸 [description]。
 *
 * @param count 色塊數量
 * @param chipGap 色塊左右間距、[rowSpacing] 列與列的間距（可以是負的：44dp 觸控區彼此疊一點），
 *        跟著真的小膠囊排法走，換上小膠囊時位置不會跳
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VsChipSkeleton(
    count: Int,
    modifier: Modifier = Modifier,
    description: String = "載入中…",
    chipGap: Dp = AppTheme.spacing.s2,
    rowSpacing: Dp = 0.dp,
) {
    val sweep = rememberInfiniteTransition(label = "skeleton")
    // 0 → 1：亮光從整區左邊外面掃到右邊外面
    val progress = sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ShimmerDurationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonSweep",
    )
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    val base = AppTheme.colors.border
    val highlight = lerp(base, AppTheme.colors.surface, 0.75f)
    val bandWidthPx = with(LocalDensity.current) { ShimmerBandWidth.toPx() }
    FlowRow(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val x = -bandWidthPx + (size.width + bandWidthPx * 2) * progress.value
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        startX = x - bandWidthPx,
                        endX = x + bandWidthPx,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(chipGap),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        repeat(count) { i ->
            Box(Modifier.height(AppTheme.spacing.tap), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(SkeletonChipWidths[i % SkeletonChipWidths.size])
                        .height(26.dp)
                        .background(base, shape),
                )
            }
        }
    }
}
