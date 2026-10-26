package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 【新增剪輯】的第一步：選影片方向。兩列各帶一句長度的說明（直式 3 分鐘內會成為 Shorts、橫式最長 10 分鐘）。
 *
 * 點一列＝回報該方向；抽屜由呼叫端關（[onPick] 裡把開關狀態設回 false），這裡不自己關。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientationSheet(onPick: (ClipOrientation) -> Unit, onDismiss: () -> Unit) {
    VsBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
            VsSheetHeader("影片方向")
            VsListRow(
                title = "直式",
                subtitle = "9:16・3 分鐘內會成為 Shorts",
                icon = VsIcons.Smartphone,
                onClick = { onPick(ClipOrientation.PORTRAIT) },
            )
            VsListRow(
                title = "橫式",
                subtitle = "16:9・最長 10 分鐘",
                icon = VsIcons.SmartphoneLandscape,
                onClick = { onPick(ClipOrientation.LANDSCAPE) },
            )
        }
    }
}
