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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
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
 * [divider] = false 拿掉分隔線（首頁：吸頂的月份標題自己有陰影，再多一條線多餘）。
 * **不處理系統列 inset**：在 `AppShell` 的 `Scaffold` 裡已經拿到了；不在裡面的呼叫端
 * （例如批次編輯、取圖精靈）自己在 [modifier] 上加 `statusBarsPadding()`。
 */
@Composable
fun VsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    nav: TopBarNav? = null,
    titleStyle: TopBarTitle = TopBarTitle.Large,
    divider: Boolean = true,
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
        if (divider) HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)
    }
}

/**
 * 頂欄上的 44×44 圖示鈕。
 *
 * [active]＝這顆鈕代表的條件正在生效（例如首頁的時間篩選）：主色圖示＋主色圓底，
 * 圓底外圈有慢慢呼吸的光暈，提醒畫面目前是篩選過的；這時 [tint] 不使用。
 */
@Composable
fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.textDim,
    active: Boolean = false,
) {
    val shape = if (active) CircleShape else RoundedCornerShape(AppTheme.radii.sm)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(AppTheme.spacing.tap)
            .focusRing(shape)
            .then(if (active) Modifier.breathingHalo() else Modifier),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (active) AppTheme.colors.accent else tint)
    }
}

/** 外圈呼吸一次漸亮（或漸暗）的時間；一亮一暗合計 3.2 秒，慢慢的、不像警示在閃。 */
private const val HaloHalfMillis = 1600

/** 圓底半徑：比 44dp 觸控區小一圈（觸控區不變），圖示外面留一點邊就好。 */
private val HaloBaseRadius = 18.dp

/** 外圈光暈在圓底外面的寬度範圍。 */
private val HaloMinSpread = 1.dp
private val HaloMaxSpread = 4.dp
/** 光暈最亮時也比圓底淡，只是柔柔地亮起來，不要像一圈深色外框。 */
private const val HaloMaxAlpha = 0.18f

/**
 * 固定的主色圓底（`accentWeak` 與 `accentLine` 的中間色）＋圓底外面一圈慢慢呼吸的主色光暈（變亮時稍微往外撐、變暗時收回）。
 * 進度只在繪製階段讀取，動畫期間不會觸發重組。系統關閉動畫時光暈停在最暗（透明），只剩圓底。
 */
@Composable
private fun Modifier.breathingHalo(): Modifier {
    val base = lerp(AppTheme.colors.accentWeak, AppTheme.colors.accentLine, 0.5f)
    val halo = AppTheme.colors.accent
    val breath by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(HaloHalfMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloBreath",
    )
    return drawBehind {
        val r = HaloBaseRadius.toPx()
        val spread = HaloMinSpread.toPx() + (HaloMaxSpread - HaloMinSpread).toPx() * breath
        // 光暈先畫、圓底蓋在上面，露出來的只有圓底外面那一圈
        drawCircle(halo.copy(alpha = HaloMaxAlpha * breath), radius = r + spread)
        drawCircle(base, radius = r)
    }
}
