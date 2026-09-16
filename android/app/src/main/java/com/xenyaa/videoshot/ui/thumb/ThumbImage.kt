package com.xenyaa.videoshot.ui.thumb

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 一張縮圖。拿不到圖時畫**中性的預留圖並標影片秒數**（手冊 §一：不是破圖）。
 */
@Composable
fun ThumbImage(
    shot: ShotRow,
    loader: ThumbLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap by produceState<ImageBitmap?>(null, shot.id, loader) {
        value = loader.load(shot)
    }
    Box(modifier.background(AppTheme.colors.surface2), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                formatClock(shot.atSec),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}
