package com.xenyaa.videoshot.ui.thumb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 縮圖牆的一格：正方形、圓角 8、`surface2` 底、`Crop`（設計文件決定 5；原型 `.tiles` 的格子）。
 * 首頁與資料夾內容共用。名稱預設走 [labelOf]（有描述唸描述，沒有就唸「片段縮圖 MM:SS」）。
 */
@Composable
fun ThumbTile(
    shot: ShotRow,
    loader: ThumbLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = labelOf(shot),
) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
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
