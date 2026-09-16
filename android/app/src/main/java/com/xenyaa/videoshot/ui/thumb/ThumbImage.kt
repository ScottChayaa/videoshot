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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 一張縮圖。拿不到圖時畫**中性的預留圖並標影片秒數**（手冊 §一：不是破圖）。
 *
 * `contentDescription` 掛在外層 [Box]、不掛在 [Image] 上 ——
 * 圖解不出來時畫面畫的是預留文字（不是 `Image`），如果說明只綁在 `Image` 上，
 * 這張縮圖在預留圖的狀態下就會對 TalkBack 完全沒有名字，永久解不出來的壞圖會變成無法唸出來的按鈕。
 *
 * @param showTimeOnPlaceholder 預留圖要不要疊秒數文字。首頁縮圖牆預設 `true`——
 *        格子小，秒數是唯一能認出這是哪一張的線索。Lightbox 傳 `false`：
 *        它自己在大圖下方已經固定畫一條秒數列，兩份秒數疊在一起只是重複、會蓋在畫面上。
 */
@Composable
fun ThumbImage(
    shot: ShotRow,
    loader: ThumbLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    showTimeOnPlaceholder: Boolean = true,
) {
    val bitmap by produceState<ImageBitmap?>(null, shot.id, loader) {
        value = loader.load(shot)
    }
    val described = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(
        modifier.background(AppTheme.colors.surface2).then(described),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                // 說明已經掛在外層 Box，這裡給 null 才不會讓輔助技術唸兩次
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showTimeOnPlaceholder) {
            Text(
                formatClock(shot.atSec),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textDim,
            )
        }
    }
}
