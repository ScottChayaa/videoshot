package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 頂欄左側的導覽鈕。`label` 是 TalkBack 唸的名稱，呼叫端可改（例如查詢結果頁的「改條件」）。 */
sealed interface TopBarNav {
    val onClick: () -> Unit
    val label: String
    data class Back(override val onClick: () -> Unit, override val label: String = "返回") : TopBarNav
    data class Close(override val onClick: () -> Unit, override val label: String = "關閉") : TopBarNav
}

/** 標題字級：[Large]＝20（原型 `.topbar .title`），[Small]＝17（`.title.sm`，長標題用）。 */
enum class TopBarTitle { Large, Small }

/**
 * 全 app 共用的頂欄（原型 `styles.css`「頂部列」）。
 *
 * 高度固定 60＋1dp 分隔線，有沒有按鈕都一樣——各頁頂欄同高，切分頁時內容才不會上下跳。
 * **不處理系統列 inset**：在 `AppShell` 的 `Scaffold` 裡已經拿到了；不在裡面的呼叫端
 * （例如批次編輯、取圖精靈）自己在 [modifier] 上加 `statusBarsPadding()`。
 */
@Composable
fun VsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    nav: TopBarNav? = null,
    titleStyle: TopBarTitle = TopBarTitle.Large,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(AppTheme.spacing.topBarHeight)
                .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            if (nav != null) {
                val icon = when (nav) {
                    is TopBarNav.Back -> VsIcons.Back
                    is TopBarNav.Close -> VsIcons.Close
                }
                TopBarIconButton(icon, nav.label, nav.onClick, tint = AppTheme.colors.text)
            }
            val base = MaterialTheme.typography.titleMedium // 20
            val style = when (titleStyle) {
                TopBarTitle.Large -> base.copy(fontWeight = FontWeight.Bold)
                TopBarTitle.Small -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) // 17
            }
            Text(
                title,
                style = style,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            actions()
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
    }
}

/** 頂欄上的 44×44 圖示鈕。 */
@Composable
fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.textDim,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(AppTheme.spacing.tap).focusRing(RoundedCornerShape(AppTheme.radii.sm)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
