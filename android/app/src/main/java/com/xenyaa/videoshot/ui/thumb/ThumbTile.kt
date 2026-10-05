package com.xenyaa.videoshot.ui.thumb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 縮圖牆的格線（首頁、查詢結果、資料夾內容共用）。刻意偏離 4/8/12/16/24/32 尺標——縮圖牆要靠得緊才像一整面牆。 */
val ThumbGridGap = 1.dp

/**
 * 縮圖牆的一格：正方形、直角、`surface2` 底、`Crop`。
 * 首頁、查詢結果、資料夾內容共用（三處都是左右貼齊螢幕邊緣、格線 [ThumbGridGap]）。
 * 名稱預設走 [labelOf]（有描述唸描述，沒有就唸「片段縮圖 MM:SS」）。
 */
@Composable
fun ThumbTile(
    shot: ShotRow,
    loader: ThumbLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = labelOf(shot),
) {
    val shape = RectangleShape
    Box(
        modifier
            .aspectRatio(1f)
            .focusRing(shape)
            .clip(shape)
            .background(AppTheme.colors.surface2)
            .clickable(role = Role.Button, onClickLabel = "開啟", onClick = onClick),
    ) {
        ThumbImage(shot, loader, Modifier.fillMaxSize(), contentDescription)
    }
}
