package com.xenyaa.videoshot.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

private fun iconOf(tab: Tab): ImageVector = when (tab) {
    Tab.HOME -> VsIcons.Home
    Tab.SEARCH -> VsIcons.Search
    Tab.CAPTURE -> VsIcons.Plus
    Tab.FOLDERS -> VsIcons.Folder
    Tab.ACCOUNT -> VsIcons.Person
}

/**
 * App 外殼：底部五格 ＋ 目前分頁的內容。
 *
 * **取圖是全螢幕、沒有底部導覽**（手冊 §四第一條）—— 它是一條有進有出的流程，
 * 中途切到別的分頁會讓「草稿還在不在」變得說不清楚。
 */
@Composable
fun AppShell(
    nav: NavState,
    onSelectTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Tab) -> Unit,
) {
    if (nav.tab == Tab.CAPTURE) {
        Box(modifier.fillMaxSize()) { content(Tab.CAPTURE) }
        return
    }
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.bg,
        bottomBar = {
            NavigationBar(containerColor = AppTheme.colors.surface) {
                for (tab in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == nav.tab,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(iconOf(tab), contentDescription = null) },
                        // 文字一律顯示，不用 alwaysShowLabel = false ——
                        // 手冊 §零 第一條要的就是「圖示＋文字」，只有選取那一格有字不算數
                        label = { Text(tab.label) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppTheme.colors.accent,
                            selectedTextColor = AppTheme.colors.accent,
                            indicatorColor = AppTheme.colors.accentWeak,
                            unselectedIconColor = AppTheme.colors.textDim,
                            unselectedTextColor = AppTheme.colors.textDim,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content(nav.tab) }
    }
}
