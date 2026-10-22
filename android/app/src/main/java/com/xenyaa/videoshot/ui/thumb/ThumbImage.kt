package com.xenyaa.videoshot.ui.thumb

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.common.VsSkeletonBlock
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 一張縮圖。三種狀態：
 * - **載入中**：灰色骨架塊（[VsSkeletonBlock]），不畫圖示與秒數 —— 通常幾幀後就換成真圖，
 *   秒數文字閃一下體感不好（2026-10-22 scott）。不加亮光掃過：每格一個動畫圖層，捲動時反而拖慢每一幀
 * - **有圖**：畫圖
 * - **拿不到圖**：**中性的預留圖並標影片秒數**（手冊 §一：不是破圖）
 *
 * `contentDescription` 掛在外層 [Box]、不掛在 [Image] 上 ——
 * 圖解不出來時畫面畫的是預留文字（不是 `Image`），如果說明只綁在 `Image` 上，
 * 這張縮圖在預留圖（與載入中）的狀態下就會對 TalkBack 完全沒有名字，永久解不出來的壞圖會變成無法唸出來的按鈕。
 *
 * @param showTimeOnPlaceholder 預留圖要不要疊秒數文字。首頁縮圖牆預設 `true`——
 *        格子小，秒數是唯一能認出這是哪一張的線索。Lightbox 傳 `false`：
 *        它自己在大圖下方已經固定畫一條秒數列，兩份秒數疊在一起只是重複、會蓋在畫面上。
 * @param fitImageAspect 框跟著圖本身的寬高比、整張圖完整顯示不裁切（Lightbox 用：直式影片的圖是直的）。
 *        在可用空間裡等比放到最大——橫的撐滿寬、直的撐滿高。載入中與拿不到圖時框是 16:9。
 *        預設 `false`：框的大小由呼叫端決定，圖裁切填滿（縮圖牆的正方形格子）。
 */
@Composable
fun ThumbImage(
    shot: ShotRow,
    loader: ThumbLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    showTimeOnPlaceholder: Boolean = true,
    fitImageAspect: Boolean = false,
) {
    // 初始值先查記憶體快取：快速捲動時，已經解碼過的圖第一幀就畫出來，不先畫一幀骨架再換掉。
    // 快取沒有才是「載入中」；load() 回 null 才是「拿不到圖」
    val cached = remember(shot.id, loader) { loader.peek(shot)?.let(ThumbState::Ready) ?: ThumbState.Loading }
    val state by produceState(cached, shot.id, loader) {
        value = loader.load(shot)?.let(ThumbState::Ready) ?: ThumbState.Missing
    }
    val bitmap = (state as? ThumbState.Ready)?.image
    val described = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    val sized = if (fitImageAspect) {
        val image = bitmap
        modifier.aspectRatio(if (image != null) image.width.toFloat() / image.height else 16f / 9f)
    } else {
        modifier
    }
    Box(
        sized.background(AppTheme.colors.surface2).then(described),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            ThumbState.Loading -> VsSkeletonBlock(Modifier.fillMaxSize().testTag(SKELETON_TAG), shape = RectangleShape)
            is ThumbState.Ready -> Image(
                bitmap = current.image,
                // 說明已經掛在外層 Box，這裡給 null 才不會讓輔助技術唸兩次
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 預留圖：中性圖示＋影片秒數（手冊 §一，不是破圖）。showTimeOnPlaceholder = false 時只畫圖示
            ThumbState.Missing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    VsIcons.ImagePlus,
                    contentDescription = null,
                    tint = AppTheme.colors.textFaint,
                    modifier = Modifier.size(20.dp).testTag(PLACEHOLDER_ICON_TAG),
                )
                if (showTimeOnPlaceholder) {
                    Text(
                        formatClock(shot.atSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textDim,
                    )
                }
            }
        }
    }
}

private sealed interface ThumbState {
    data object Loading : ThumbState
    data class Ready(val image: ImageBitmap) : ThumbState
    data object Missing : ThumbState
}

/** 載入中骨架塊的測試標籤。 */
internal const val SKELETON_TAG = "thumb-skeleton"

/** 預留圖圖示的測試標籤。 */
internal const val PLACEHOLDER_ICON_TAG = "thumb-placeholder-icon"
