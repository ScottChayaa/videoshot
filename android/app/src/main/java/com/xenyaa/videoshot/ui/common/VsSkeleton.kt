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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 骨架色塊的寬度輪流取這幾個，看起來像長短不一的名字，而不是一排一樣的方塊。 */
private val SkeletonChipWidths = listOf(56.dp, 84.dp, 64.dp, 100.dp, 48.dp, 76.dp, 92.dp, 60.dp)

/**
 * 小膠囊的骨架屏：資料還沒畫上去之前，先放一排長短不一的灰色圓角色塊，明暗緩緩閃動。
 *
 * 每塊佔一個 [VsTagChip] 的高度（44dp 觸控區、色塊置中），換成真的小膠囊時列高不會跳。
 * 閃動只改 `graphicsLayer` 的透明度、在繪製階段讀值，不會每一幀重組。
 * TalkBack 把整塊當成一個節點，唸 [description]。
 *
 * @param count 色塊數量
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VsChipSkeleton(count: Int, modifier: Modifier = Modifier, description: String = "載入中…") {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha = pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    val color = AppTheme.colors.border
    FlowRow(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .graphicsLayer { this.alpha = alpha.value },
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
    ) {
        repeat(count) { i ->
            Box(Modifier.height(AppTheme.spacing.tap), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(SkeletonChipWidths[i % SkeletonChipWidths.size])
                        .height(26.dp)
                        .background(color, shape),
                )
            }
        }
    }
}
