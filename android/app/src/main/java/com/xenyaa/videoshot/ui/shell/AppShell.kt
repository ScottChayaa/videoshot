package com.xenyaa.videoshot.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
        bottomBar = { VsBottomNav(current = nav.tab, onSelect = onSelectTab, accountInitial = accountInitial) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content(nav.tab) }
    }
}
