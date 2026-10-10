package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 左上 20dp 主色圓形打勾徽章，外圈 2dp 白邊（原型 .wz-cell.sel::before）。
 * 取圖第二步、第三步與照片頁多選的「已選」共用同一個外觀；語意名稱「已選」讓輔助技術不只靠顏色辨識。
 * 整格的語意已經含選取狀態時，徽章傳 null 當純裝飾，避免唸兩次。
 */
@Composable
fun VsSelectedBadge(modifier: Modifier = Modifier, semanticLabel: String? = "已選") {
    Box(
        modifier
            .padding(2.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .padding(2.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.accent)
            .then(if (semanticLabel != null) Modifier.semantics { contentDescription = semanticLabel } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(VsIcons.Check, contentDescription = null, tint = AppTheme.colors.accentInk, modifier = Modifier.size(12.dp))
    }
}

/** 多選模式下還沒選的格子：跟 [VsSelectedBadge] 同位置、同大小的空心白圈（純裝飾），讓人看得出「點了會選」。 */
@Composable
fun VsUnselectedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .size(24.dp)
            .padding(1.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.2f))
            .border(2.dp, Color.White, CircleShape),
    )
}
