package com.xenyaa.videoshot.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.ui.common.VsBottomNav
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * App 外殼：底部五格 ＋ 目前分頁的內容。
 *
 * **取圖是全螢幕、沒有底部導覽**（手冊 §四第一條）—— 它是一條有進有出的流程，
 * 中途切到別的分頁會讓「草稿還在不在」變得說不清楚。
 *
 * 導覽列外觀見 [VsBottomNav]；[accountInitial] 是已連結帳號的頭像字母（沒連結傳 null）。
 */
@Composable
fun AppShell(
    nav: NavState,
    onSelectTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    accountInitial: Char? = null,
    /**
     * false＝藏起底部導覽（照片頁多選時，換成頁面自己的動作列，不然兩列疊在一起很怪）。
     * 藏起來時內容不墊系統導覽列的高度，由頁面自己的動作列 `navigationBarsPadding()` 鋪到螢幕底。
     */
    showBottomNav: Boolean = true,
    content: @Composable (Tab) -> Unit,
) {
    if (nav.tab == Tab.CAPTURE) {
        // 精靈自己的提示（例如完成時的「已新增 N 張」）也走同一個 host，
        // 疊在內容上面而不是走 Scaffold —— 這裡本來就沒有 bottomBar
        Box(modifier.fillMaxSize()) {
            content(Tab.CAPTURE)
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
        return
    }
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (showBottomNav) VsBottomNav(current = nav.tab, onSelect = onSelectTab, accountInitial = accountInitial) },
        contentWindowInsets = if (showBottomNav) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        },
    ) { padding ->
        // consumeWindowInsets：Scaffold 已把系統列 inset 換成 padding 給內容，但不會標記成已消耗；
        // 不標的話內容裡的 VsActionDock（自己 navigationBarsPadding）會再墊一次導覽列高度
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content(nav.tab) }
    }
}
