package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 拖曳把手上下的留白——**全 app 抽屜頂部的距離只在這裡調**（2026-10-09 scott 定的範本：上 12、下 4dp，
 * Material 預設是上下各 22dp）。
 */
val SheetDragHandleTop = 12.dp
val SheetDragHandleBottom = 4.dp

/**
 * 全 app 的底部抽屜一律用它，不直接用 Material 的 `ModalBottomSheet`：底色、拖曳把手（頂部距離）
 * 統一在這裡，要調整只改一處。
 *
 * 預設 `skipPartiallyExpanded = true`：抽屜都不長，半開狀態只會多一次滑動，也讓 Robolectric 不必等展開動畫。
 * 要讀抽屜狀態（例如等它停穩）的呼叫端自己建 [sheetState] 傳進來。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VsBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = AppTheme.colors.surface,
        dragHandle = { VsSheetDragHandle() },
        content = content,
    )
}

/**
 * 拖曳把手。自己畫——`BottomSheetDefaults.DragHandle` 的上下 22dp 是寫死的，傳進去的 padding 只會疊加上去。
 * 外觀（32×4、圓角、onSurfaceVariant 40%）照 Material 的預設；展開／收合／關閉的無障礙動作是
 * `ModalBottomSheet` 包在把手外面那層加的，不受影響。
 */
@Composable
private fun VsSheetDragHandle() {
    Box(
        Modifier
            .padding(top = SheetDragHandleTop, bottom = SheetDragHandleBottom)
            .size(width = 32.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
            .semantics { contentDescription = "拖曳控點" },
    )
}
