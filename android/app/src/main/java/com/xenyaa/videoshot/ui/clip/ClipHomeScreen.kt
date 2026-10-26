package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 剪輯分頁（底部導覽第三格）。這個階段只有空狀態；任務清單是階段 20 起的事。
 *
 * 【新增剪輯】只通知呼叫端，「選方向」的抽屜由 [AppRoot] 掛（見 [OrientationSheet]）。
 */
@Composable
fun ClipHomeScreen(onNewClip: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        VsTopBar("剪輯")
        VsEmptyState(
            message = "把手機裡的照片和影片剪成一支片",
            modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.s6),
            icon = VsIcons.Clapperboard,
            secondary = "上傳到你的 YouTube（非公開）後，就能用取圖精靈挑畫面",
            actionText = "新增剪輯",
            onAction = onNewClip,
        )
    }
}
