package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.common.VsUnderlineTabs
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

private val TabFilters = listOf(MediaFilter.ALL, MediaFilter.VIDEO, MediaFilter.PHOTO)
private val TabLabels = listOf("全部", "影片", "照片")

/**
 * 剪輯的「選素材」：手機裡的照片與影片，一格一個，依點選順序編號。
 *
 * 只吃狀態與回呼：權限請求與開系統設定的 Intent 都在 [AppRoot] 那一層，這裡才能在 Robolectric 測。
 * [onRequestAccess] 同時給 DENIED 的【允許存取】與 PARTIAL 橫幅的【選取更多】用——
 * 兩者都是「再叫一次系統的權限視窗」。[canAskAgain] 為 false（被永久拒絕）時，DENIED 改成【前往設定】。
 */
@Composable
fun ClipPickerScreen(
    state: ClipPickerState,
    thumbLoader: MediaThumbLoader,
    orderOf: (Long) -> Int?,
    onBack: () -> Unit,
    onFilter: (MediaFilter) -> Unit,
    onToggle: (Long) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    canAskAgain: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        VsTopBar("選素材", nav = TopBarNav.Back(onBack), divider = state.access == MediaAccess.DENIED)

        if (state.access == MediaAccess.DENIED) {
            MediaAccessDenied(canAskAgain, onRequestAccess, onOpenSettings)
            return@Column
        }

        VsUnderlineTabs(TabLabels, selected = TabFilters.indexOf(state.filter), onSelect = { onFilter(TabFilters[it]) })
        if (state.access == MediaAccess.PARTIAL) PartialBanner(onRequestAccess)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val items = state.items
            when {
                items == null -> Unit
                items.isEmpty() -> VsEmptyState(
                    message = when (state.filter) {
                        MediaFilter.ALL -> "手機裡還沒有照片或影片"
                        MediaFilter.VIDEO -> "手機裡還沒有影片"
                        MediaFilter.PHOTO -> "手機裡還沒有照片"
                    },
                    modifier = Modifier.padding(top = AppTheme.spacing.s6),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        // 把 selected 當 key：選取改變時這格要重算序號（orderOf 讀的不是 Compose 的狀態）
                        val order = remember(item.id, state.selected) { orderOf(item.id) }
                        MediaCell(item, order, thumbLoader, onClick = { onToggle(item.id) })
                    }
                }
            }
        }

        VsActionDock {
            val count = state.selected.size
            VsButton(
                text = if (count == 0) "下一步" else "下一步（$count）",
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = count > 0,
            )
        }
    }
}

/**
 * 沒有相片和影片存取權時的空狀態：選素材與編輯畫面共用。
 * [canAskAgain] 為 false（被永久拒絕）時，按鈕改成【前往設定】。
 */
@Composable
internal fun MediaAccessDenied(
    canAskAgain: Boolean,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VsEmptyState(
        message = "需要相片和影片的存取權",
        modifier = modifier.padding(top = AppTheme.spacing.s6),
        secondary = "videoshot 只會讀取你選的素材；上傳完成後，也可以選擇刪除它們",
        actionText = if (canAskAgain) "允許存取" else "前往設定",
        onAction = if (canAskAgain) onRequestAccess else onOpenSettings,
    )
}

@Composable
private fun PartialBanner(onSelectMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.surface2).padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
    ) {
        Text(
            "你只允許存取部分相片和影片",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textDim,
            modifier = Modifier.weight(1f).padding(vertical = AppTheme.spacing.s2),
        )
        VsButton("選取更多", onSelectMore, variant = ButtonVariant.Quiet)
    }
}

/**
 * 一格素材。整格是一個語意節點：已選唸「已選第 N 個」，沒選唸種類（影片再加上長度那段文字）。
 * 縮圖以 [item] 當 key：格子捲出畫面再回來會重新要一次，載入被別人取消而得到 null 的格子因此還有機會補上。
 */
@Composable
private fun MediaCell(item: MediaItem, order: Int?, loader: MediaThumbLoader, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(loader.peek(item.id), item.id, loader) {
        if (value == null) value = loader.load(item)
    }
    val description = when {
        order != null -> "已選第 $order 個"
        item.kind == MediaKind.VIDEO -> "影片"
        else -> "照片"
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(AppTheme.colors.surface2)
            .focusRing(RectangleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = order != null
            },
    ) {
        thumb?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        val duration = item.durationMs
        if (item.kind == MediaKind.VIDEO && duration != null) {
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppTheme.spacing.s1)
                    .clip(RoundedCornerShape(AppTheme.radii.sm / 2))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (order != null) OrderBadge(order, Modifier.align(Alignment.TopStart))
    }
}

/**
 * 左上的序號圓。外觀比照 `VsSelectedBadge`（白邊、主色底），裡面的打勾換成數字。
 * 純裝飾：語意由整格的「已選第 N 個」負責，這裡清掉才不會唸兩次。
 */
@Composable
private fun OrderBadge(order: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .padding(2.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.accent)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            order.toString(),
            color = AppTheme.colors.accentInk,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 11.sp),
        )
    }
}

/** 影片長度：不到一小時 `m:ss`，一小時以上 `h:mm:ss`。 */
internal fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
