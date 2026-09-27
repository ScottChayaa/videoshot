package com.xenyaa.videoshot.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.format.formatBytes
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.shell.AccountSection
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 帳號頁首畫面：漸層 hero ＋ 跨在下緣的三格統計卡 ＋ 六格選單（規格第九節「版面」）。
 *
 * hero 依 `state.linkedAccount` 分支：未連結時顯示「尚未設定備份」，已連結時顯示帳號
 * 名稱與 Email（階段 12）。兩種狀態底下那一行都可點，開的都是同一個 `AccountSection.BACKUP`。
 */
@Composable
fun AccountScreen(
    state: AccountState,
    onOpenSection: (AccountSection) -> Unit,
    onOpenStat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(AppTheme.colors.hero1, AppTheme.colors.hero2)))
                    .padding(AppTheme.spacing.s5),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(AppTheme.colors.overlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 已連結就把帳號名稱的第一個字（大寫）當頭像，沒連結才用通用人形圖示
                        // （手冊 §八）。名稱理論上不會是空字串，但 Drive 的顯示名稱不是我們
                        // 產生的資料，取不到字就退回「?」，不讓一個空字串把圓圈畫成空白。
                        val linked = state.linkedAccount
                        if (linked != null) {
                            Text(
                                linked.displayName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleLarge,
                                color = AppTheme.colors.accentInk,
                            )
                        } else {
                            Icon(VsIcons.Person, contentDescription = null, tint = AppTheme.colors.accentInk)
                        }
                    }
                    Column(Modifier.padding(start = AppTheme.spacing.s3)) {
                        if (state.linkedAccount == null) {
                            Text(
                                "尚未設定備份",
                                style = MaterialTheme.typography.titleMedium,
                                color = AppTheme.colors.accentInk,
                            )
                            Text(
                                "連結 Google 帳號以啟用備份",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.accentInk,
                                modifier = Modifier
                                    .focusRing()
                                    .clickable { onOpenSection(AccountSection.BACKUP) }
                                    .padding(top = AppTheme.spacing.s1),
                            )
                        } else {
                            Text(
                                state.linkedAccount.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = AppTheme.colors.accentInk,
                            )
                            Text(
                                state.linkedAccount.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.accentInk,
                                modifier = Modifier
                                    .focusRing()
                                    .clickable { onOpenSection(AccountSection.BACKUP) }
                                    .padding(top = AppTheme.spacing.s1),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .padding(horizontal = AppTheme.spacing.s4),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
            ) {
                StatCard("收藏片段", state.stats.totalShots, Modifier.weight(1f), onOpenStat)
                StatCard("本月新增", state.stats.thisMonthShots, Modifier.weight(1f), onOpenStat)
                StatCard("來源影片", state.stats.distinctVideos, Modifier.weight(1f), onOpenStat)
            }
        }
        item {
            AccountMenuRow(
                VsIcons.Cloud, "備份",
                state.linkedAccount?.let { "已連結：${it.displayName}" } ?: "尚未設定備份",
                onClick = { onOpenSection(AccountSection.BACKUP) },
            )
        }
        item {
            AccountMenuRow(
                VsIcons.ImagePlus, "縮圖", "已使用 ${formatBytes(state.storageUsageBytes)}",
                onClick = { onOpenSection(AccountSection.THUMBS) },
            )
        }
        item {
            AccountMenuRow(
                VsIcons.Filter, "取圖", "過濾相似強度：${filterStrengthLabel(state.filterStrength)}",
                onClick = { onOpenSection(AccountSection.CAPTURE) },
            )
        }
        item {
            AccountMenuRow(
                VsIcons.Search, "查詢", if (state.geminiKeySet) "Gemini 金鑰：已設定" else "Gemini 金鑰：尚未設定",
                onClick = { onOpenSection(AccountSection.GEMINI) },
            )
        }
        item {
            AccountMenuRow(
                VsIcons.Sparkles, "AI 分析", "往前 ${state.aiRangeBeforeSec} 秒／往後 ${state.aiRangeAfterSec} 秒",
                onClick = { onOpenSection(AccountSection.AI) },
            )
        }
        item {
            AccountMenuRow(
                VsIcons.Tag, "標籤管理", "${state.tags.size} 個標籤",
                onClick = { onOpenSection(AccountSection.TAGS) },
            )
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.focusRing(RoundedCornerShape(AppTheme.spacing.s2)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
    ) {
        Column(
            Modifier.padding(AppTheme.spacing.s3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
        ) {
            Text("$count", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.text)
            Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textDim)
        }
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
