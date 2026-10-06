package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.shell.Tab
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 帳號名稱 → 導覽列／帳號頁頭像的字母。取不到字就回 null，畫人像圖示。 */
fun avatarInitialOf(displayName: String?): Char? = displayName?.trim()?.firstOrNull()?.uppercaseChar()

private fun iconOf(tab: Tab): ImageVector = when (tab) {
    Tab.HOME -> VsIcons.Home
    Tab.SEARCH -> VsIcons.Search
    Tab.CAPTURE -> VsIcons.Plus
    Tab.FOLDERS -> VsIcons.Folder
    Tab.ACCOUNT -> VsIcons.Person
}

/**
 * 底部導覽（原型 `styles.css`「底部導覽」）。
 *
 * 取圖格用實心主色方塊——它是主要動作，但**不是紅色**（手冊 §零，紅色只留給刪除）。
 * 帳號格連結 Google 帳號後換成頭像字母（設計文件決定 4），文字一律「帳號」。
 */
@Composable
fun VsBottomNav(current: Tab, onSelect: (Tab) -> Unit, accountInitial: Char?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(AppTheme.colors.surface).navigationBarsPadding()) {
        // 上緣分隔線用一個實體像素（最細）；Dp.Hairline 會讓分隔線的版面高度是 0，改成明確換算
        HorizontalDivider(thickness = (1f / LocalDensity.current.density).dp, color = AppTheme.colors.border)
        Row(Modifier.fillMaxWidth().height(AppTheme.spacing.navHeight)) {
            for (tab in Tab.entries) {
                NavItem(
                    tab = tab,
                    selected = tab == current,
                    accountInitial = accountInitial,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, accountInitial: Char?, onClick: () -> Unit, modifier: Modifier) {
    val isCapture = tab == Tab.CAPTURE
    val color = when {
        isCapture || selected -> AppTheme.colors.accent
        else -> AppTheme.colors.textDim
    }
    Column(
        modifier
            .focusRing()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            when {
                isCapture -> Box(
                    Modifier.width(30.dp).height(26.dp)
                        .clip(RoundedCornerShape(AppTheme.radii.sm))
                        .background(AppTheme.colors.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(iconOf(tab), null, tint = AppTheme.colors.accentInk, modifier = Modifier.size(20.dp)) }

                tab == Tab.ACCOUNT && accountInitial != null -> Box(
                    Modifier.size(24.dp).clip(CircleShape).background(AppTheme.colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    // 字母只是視覺頭像，從無障礙樹清掉，TalkBack 才不會多唸一個英文字母
                    Text(
                        accountInitial.toString(),
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AppTheme.colors.accentInk,
                    )
                }

                else -> Icon(iconOf(tab), null, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = color,
        )
    }
}
