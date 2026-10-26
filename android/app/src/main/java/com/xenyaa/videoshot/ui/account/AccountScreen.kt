package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.format.formatBytes
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.common.VsSettingDivider
import com.xenyaa.videoshot.ui.common.VsSettingGroup
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.common.avatarInitialOf
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.shell.AccountSection
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.OnDarkColors
import com.xenyaa.videoshot.ui.theme.focusRing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale

/**
 * 帳號頁首畫面：淡色 hero ＋ 跨在下緣的三格統計卡 ＋ 六格選單（規格第九節「版面」）。
 *
 * hero 依 `state.linkedAccount` 分支：未連結時顯示「尚未設定備份」，已連結時顯示帳號
 * 名稱與 Email（階段 12）。未連結時整塊內容列可點、已連結時 Email 那行可點，開的都是同一個 `AccountSection.BACKUP`。
 */
@Composable
fun AccountScreen(
    state: AccountState,
    onOpenSection: (AccountSection) -> Unit,
    onOpenStat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        VsTopBar("帳號")
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item { AccountHero(state, onOpenSection) }
            item { StatCard(state.stats, onOpenStat) }
            item {
                VsSettingGroup(divided = true) {
                    VsListRow(
                        "備份",
                        subtitle = state.linkedAccount?.let { "已連結：${it.displayName}" } ?: "尚未設定備份",
                        icon = VsIcons.Cloud,
                        onClick = { onOpenSection(AccountSection.BACKUP) },
                    )
                    VsSettingDivider()
                    VsListRow(
                        "縮圖",
                        subtitle = "已使用 ${formatBytes(state.storageUsageBytes)}",
                        icon = VsIcons.ImagePlus,
                        onClick = { onOpenSection(AccountSection.THUMBS) },
                    )
                    VsSettingDivider()
                    VsListRow(
                        "取圖",
                        subtitle = "過濾相似強度：${filterStrengthLabel(state.filterStrength)}",
                        icon = VsIcons.Sliders,
                        onClick = { onOpenSection(AccountSection.CAPTURE) },
                    )
                    VsSettingDivider()
                    // 階段 17：描述查詢下架，「查詢 · Gemini 金鑰」這一列先藏起來——
                    // AccountSection.GEMINI 與 GeminiKeyScreen 都留著，日後重新開放時把這一列接回即可
                    VsListRow(
                        "AI 分析",
                        subtitle = "往前 ${state.aiRangeBeforeSec} 秒／往後 ${state.aiRangeAfterSec} 秒",
                        icon = VsIcons.Sparkles,
                        onClick = { onOpenSection(AccountSection.AI) },
                    )
                    VsSettingDivider()
                    VsListRow(
                        "地點管理",
                        subtitle = "${state.places.size} 個地點",
                        icon = VsIcons.MapPin,
                        onClick = { onOpenSection(AccountSection.PLACES) },
                    )
                    VsSettingDivider()
                    VsListRow(
                        "標籤管理",
                        subtitle = "${state.tags.size} 個標籤",
                        icon = VsIcons.Tag,
                        onClick = { onOpenSection(AccountSection.TAGS) },
                    )
                }
            }
        }
    }
}

/**
 * hero（原型 `.account-hero`／`.account`）：上內距 8、下 32，內容列內距 12/16、間距 12。
 * 底不染色，就是頁面底色；夕陽漸層只留在頭像圓圈上，字用一般深色字
 * （2026-10-25：原本整塊夕陽漸層太搶眼；2026-10-26：淡杏底跟中性色的膠囊不搭，拿掉）。
 * 沒連結時整塊內容列可點（進備份子畫面）；已連結時維持只有 Email 那行可點。不做登出（app 沒有登入）。
 */
@Composable
private fun AccountHero(state: AccountState, onOpenSection: (AccountSection) -> Unit) {
    val linked = state.linkedAccount
    val openBackup = { onOpenSection(AccountSection.BACKUP) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.spacing.s2, bottom = AppTheme.spacing.s6),
    ) {
        val rowModifier = Modifier.fillMaxWidth().let {
            if (linked == null) it.focusRing(RoundedCornerShape(AppTheme.radii.md)).clickable(role = Role.Button, onClick = openBackup) else it
        }
        Row(
            rowModifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AppTheme.colors.hero1, AppTheme.colors.hero2))),
                contentAlignment = Alignment.Center,
            ) {
                // 已連結就用 Google 頭像；還沒抓到（或帳號沒設頭像）時把帳號名稱的第一個字（大寫）
                // 當頭像，沒連結才用通用人形圖示（手冊 §八）。名稱理論上不會是空字串，但 Drive 的
                // 顯示名稱不是我們產生的資料，取不到字就退回「?」，不讓一個空字串把圓圈畫成空白。
                if (linked != null && state.avatar != null) {
                    Image(state.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else if (linked != null) {
                    Text(
                        avatarInitialOf(linked.displayName)?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnDarkColors.primary,
                    )
                } else {
                    Icon(VsIcons.Person, contentDescription = null, tint = OnDarkColors.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    linked?.displayName ?: "尚未設定備份",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    linked?.email ?: "連結 Google 帳號以啟用備份",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (linked != null) {
                        Modifier
                            .focusRing()
                            .clickable(role = Role.Button, onClick = openBackup)
                            // 觸控區至少 44dp；文字在這個高度裡垂直置中
                            .defaultMinSize(minHeight = AppTheme.spacing.tap)
                            .wrapContentHeight(Alignment.CenterVertically)
                    } else {
                        Modifier.padding(top = AppTheme.spacing.s1)
                    },
                )
            }
        }
    }
}

/**
 * 跨在 hero 下緣的單一統計卡（原型 `.stat-card`）：上外距 −24、左右 16、下 24；三等分，
 * 項目之間 1dp 直線（上下各縮 4）。負外距用自訂 layout 做，才不會像 `offset` 那樣在卡片下方留一塊空白。
 */
@Composable
private fun StatCard(stats: AccountStats, onOpenStat: () -> Unit) {
    val shape = RoundedCornerShape(AppTheme.radii.md)
    val pullUp = AppTheme.spacing.s5
    Row(
        Modifier
            .layout { measurable, constraints ->
                val up = pullUp.roundToPx()
                val p = measurable.measure(constraints)
                layout(p.width, p.height - up) { p.place(0, -up) }
            }
            .padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, bottom = AppTheme.spacing.s5)
            .shadow(8.dp, shape)
            .background(AppTheme.colors.surface, shape)
            .border(1.dp, AppTheme.colors.border, shape)
            .padding(vertical = AppTheme.spacing.s3),
    ) {
        StatItem("收藏片段", stats.totalShots, VsIcons.ImagePlus, first = true, Modifier.weight(1f), onOpenStat)
        StatItem("本月新增", stats.thisMonthShots, VsIcons.Plus, first = false, Modifier.weight(1f), onOpenStat)
        StatItem("來源影片", stats.distinctVideos, VsIcons.Film, first = false, Modifier.weight(1f), onOpenStat)
    }
}

@Composable
private fun StatItem(
    label: String,
    count: Int,
    icon: ImageVector,
    first: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val border = AppTheme.colors.border
    Column(
        modifier
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .focusRing(RoundedCornerShape(AppTheme.radii.sm))
            .clickable(role = Role.Button, onClick = onClick)
            // TalkBack 名稱「{標籤} {數字}」；圖示、數字、標籤都是視覺，不再各唸一次
            .clearAndSetSemantics { contentDescription = "$label $count" }
            .drawBehind {
                if (!first) {
                    val inset = 4.dp.toPx()
                    drawLine(border, Offset(0f, inset), Offset(0f, size.height - inset), strokeWidth = 1.dp.toPx())
                }
            }
            .padding(horizontal = AppTheme.spacing.s2, vertical = AppTheme.spacing.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
    ) {
        Box(
            Modifier.size(34.dp).background(AppTheme.colors.accentWeak, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(20.dp))
        }
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = AppTheme.colors.text,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textDim)
    }
}

/** 過濾強度的顯示文字（原型 `SIM_LABEL`）。只有帳號頁用得到，不升格成 `FilterStrength` 的欄位——
 * `:core` 不該認得 UI 顯示字串（跟 `FolderSort.label` 不同，那個本來就是給畫面顯示的欄位）。 */
internal fun filterStrengthLabel(value: FilterStrength): String =
    when (value) {
        FilterStrength.HIGH -> "高"
        FilterStrength.MEDIUM -> "中"
        FilterStrength.LOW -> "低"
    }
