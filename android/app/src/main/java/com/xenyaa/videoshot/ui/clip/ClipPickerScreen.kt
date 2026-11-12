package com.xenyaa.videoshot.ui.clip

import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import java.time.YearMonth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.VsListRow
import com.xenyaa.videoshot.ui.common.VsSelectedBadge
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsUnselectedBadge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.edit.overLimitText
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.TopBarNav
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsEmptyState
import com.xenyaa.videoshot.ui.common.VsSkeletonArea
import com.xenyaa.videoshot.ui.common.VsSkeletonBlock
import com.xenyaa.videoshot.ui.common.VsTopBar
import com.xenyaa.videoshot.ui.common.VsUnderlineTabs
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

private val TabFilters = listOf(PickerFilter.ALL, PickerFilter.PORTRAIT_VIDEO, PickerFilter.LANDSCAPE_VIDEO, PickerFilter.PHOTO)
private val TabLabels = listOf("全部", "直影片", "橫影片", "照片")
private val GridGap = 2.dp

/**
 * 剪輯的「選素材」：手機裡的照片與影片，一格一個。點左上的圓框選取（打勾），點格子其他地方全螢幕預覽（[onPreview]）；
 * 不編號：進編輯時一律依拍攝時間排，點選順序沒有意義。頂欄右邊的【快速選取】開抽屜，選月份與方向後追加那個月的素材（[onAutoPick]）；有選取時左邊多一個【清除】（[onClear]）。
 * 底部左側是選的方向與已選總長（「1:20 / 3:00」）；總長超過 [orientation] 的上限只是變成警告色提醒，
 * 【下一步】照樣能按（進編輯再刪短；真正擋下的是導出抽屜的【開始】）。
 *
 * 只吃狀態與回呼：權限請求與開系統設定的 Intent 都在 [AppRoot] 那一層，這裡才能在 Robolectric 測。
 * [onRequestAccess] 同時給 DENIED 的【允許存取】與 PARTIAL 橫幅的【選取更多】用——
 * 兩者都是「再叫一次系統的權限視窗」。[canAskAgain] 為 false（被永久拒絕）時，DENIED 改成【前往設定】。
 */
@Composable
fun ClipPickerScreen(
    state: ClipPickerState,
    orientation: ClipOrientation,
    thumbLoader: MediaThumbLoader,
    onBack: () -> Unit,
    onFilter: (PickerFilter) -> Unit,
    onToggle: (Long) -> Unit,
    onPreview: (MediaItem) -> Unit,
    onAutoPick: (YearMonth, AutoPick) -> Unit,
    onClear: () -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    canAskAgain: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        var autoPickOpen by rememberSaveable { mutableStateOf(false) }
        VsTopBar("選素材", nav = TopBarNav.Back(onBack), divider = state.access == MediaAccess.DENIED) {
            // 【清除】只在有選取時出現，放在【快速選取】左邊
            if (state.selected.isNotEmpty()) TextButton(onClick = onClear) { Text("清除") }
            if (state.access != MediaAccess.DENIED) TextButton(onClick = { autoPickOpen = true }) { Text("快速選取") }
        }
        if (autoPickOpen) {
            AutoPickSheet(state.months, orientation, onPick = { month, pick -> autoPickOpen = false; onAutoPick(month, pick) }, onDismiss = { autoPickOpen = false })
        }

        if (state.access == MediaAccess.DENIED) {
            MediaAccessDenied(canAskAgain, onRequestAccess, onOpenSettings)
            return@Column
        }

        VsUnderlineTabs(TabLabels, selected = TabFilters.indexOf(state.filter), onSelect = { onFilter(TabFilters[it]) })
        if (state.access == MediaAccess.PARTIAL) PartialBanner(onRequestAccess)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val items = state.items
            when {
                items == null -> GridSkeleton()
                items.isEmpty() -> VsEmptyState(
                    message = when (state.filter) {
                        PickerFilter.ALL -> "手機裡還沒有照片或影片"
                        PickerFilter.PORTRAIT_VIDEO -> "手機裡還沒有直式影片"
                        PickerFilter.LANDSCAPE_VIDEO -> "手機裡還沒有橫式影片"
                        PickerFilter.PHOTO -> "手機裡還沒有照片"
                    },
                    modifier = Modifier.padding(top = AppTheme.spacing.s6),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                ) {
                    items(items, key = { it.id }) { item ->
                        MediaCell(
                            item,
                            selected = item.id in state.selected,
                            loader = thumbLoader,
                            onClick = { onPreview(item) },
                            onToggle = { onToggle(item.id) },
                        )
                    }
                }
            }
        }

        val overLimit = state.selectedMs > orientation.maxDurationMs
        VsActionDock(status = { SelectedSummary(orientation, state.selectedMs, overLimit) }) {
            val count = state.selected.size
            VsButton(
                text = if (count == 0) "下一步" else "下一步（$count）",
                onClick = onNext,
                enabled = count > 0,
            )
        }
    }
}

/**
 * 底部左側：方向圖示＋「直式」，下一行是已選總長／上限。超過上限時第二行變警告色（黃）並寫超過多少；
 * 總長跟導出抽屜一樣進位到整秒，才對得起「超過 N 秒」。
 */
@Composable
private fun SelectedSummary(orientation: ClipOrientation, selectedMs: Long, overLimit: Boolean) {
    val portrait = orientation == ClipOrientation.PORTRAIT
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
        Icon(if (portrait) VsIcons.Smartphone else VsIcons.SmartphoneLandscape, contentDescription = null)
        Column {
            Text(if (portrait) "直式" else "橫式", color = AppTheme.colors.text)
            val limit = formatDuration(orientation.maxDurationMs)
            Text(
                if (overLimit) {
                    "${formatDuration((selectedMs + 999) / 1000 * 1000)} / $limit・超過 ${overLimitText(selectedMs - orientation.maxDurationMs)}"
                } else {
                    "${formatDuration(selectedMs)} / $limit"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overLimit) AppTheme.colors.warn else AppTheme.colors.textDim,
            )
        }
    }
}

/** 清單還在讀：4 欄正方形灰塊，間距同真的格線，排滿整塊高度（超出的被裁掉），換成真內容時版面不跳。 */
@Composable
private fun GridSkeleton() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = (maxWidth - GridGap * 3) / 4
        val rows = (maxHeight / (cell + GridGap)).toInt() + 1
        VsSkeletonArea(Modifier.fillMaxSize()) {
            Column(verticalArrangement = Arrangement.spacedBy(GridGap)) {
                repeat(rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                        repeat(4) { VsSkeletonBlock(Modifier.weight(1f).aspectRatio(1f), shape = RectangleShape) }
                    }
                }
            }
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
 * 一格素材。整格是一個語意節點：已選唸「已選第 N 個」，沒選唸種類（影片再加上方向與長度那段文字）。
 * 影片右下角是方向圖示＋長度，幫忙挑跟剪輯方向一樣的素材；正方形或量不到尺寸的影片不畫方向。
 * 縮圖以 [item] 當 key：格子捲出畫面再回來會重新要一次，載入被別人取消而得到 null 的格子因此還有機會補上。
 */
/**
 * 一格素材：縮圖，影片右下角標方向與長度。點格子＝[onClick]（選素材是預覽）。
 * [selected] 不是 null 時左上有圓框（沒選空心、選了打勾），點圓框＝[onToggle]；觸控範圍比圓框大一圈，好點。
 */
@Composable
internal fun MediaCell(
    item: MediaItem,
    selected: Boolean?,
    loader: MediaThumbLoader,
    onClick: () -> Unit,
    onToggle: () -> Unit = {},
) {
    val thumb by produceState<ImageBitmap?>(loader.peek(item.id), item.id, loader) {
        if (value == null) value = loader.load(item)
    }
    val videoOrientation = item.videoOrientation()
    val description = when {
        item.kind == MediaKind.VIDEO -> when (videoOrientation) {
            ClipOrientation.PORTRAIT -> "直式影片"
            ClipOrientation.LANDSCAPE -> "橫式影片"
            null -> "影片"
        }
        else -> "照片"
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(AppTheme.colors.surface2)
            .focusRing(RectangleShape)
            .clickable(onClickLabel = "預覽", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        thumb?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        val duration = item.durationMs
        if (item.kind == MediaKind.VIDEO && duration != null) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppTheme.spacing.s1)
                    .clip(RoundedCornerShape(AppTheme.radii.sm / 2))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (videoOrientation != null) {
                    Icon(
                        if (videoOrientation == ClipOrientation.PORTRAIT) VsIcons.Smartphone else VsIcons.SmartphoneLandscape,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        if (selected != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(SelectTouch)
                    .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
                    .semantics { contentDescription = "選取" }
                    .padding(AppTheme.spacing.s1),
            ) {
                if (selected) VsSelectedBadge(semanticLabel = null) else VsUnselectedBadge()
            }
        }
    }
}

/** 左上選取圓框的觸控範圍。 */
private val SelectTouch = 44.dp

/**
 * 快速選取的抽屜，兩步：先選月份（[months]，有素材的月份由新到舊），再選方向，追加那個月符合方向的全部素材；選完就關。
 * 方向三列裡跟影片同方向（[orientation]）的放第一個（最常按），不分方向居中，另一個方向墊底；頂部【換月份】回第一步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoPickSheet(
    months: List<YearMonth>,
    orientation: ClipOrientation,
    onPick: (YearMonth, AutoPick) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    VsBottomSheet(onDismissRequest = onDismiss) {
        val chosen = month
        if (chosen == null) {
            VsSheetHeader("快速選取")
            if (months.isEmpty()) {
                Text(
                    "手機裡還沒有照片或影片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(AppTheme.spacing.s4),
                )
            }
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = AppTheme.spacing.s5)) {
                items(months, key = { it.toString() }) { m ->
                    VsListRow(title = monthLabel(m), onClick = { month = m })
                }
            }
        } else {
            val portrait: @Composable () -> Unit = {
                VsListRow(title = "直式素材", icon = VsIcons.Smartphone, onClick = { onPick(chosen, AutoPick.PORTRAIT) })
            }
            val landscape: @Composable () -> Unit = {
                VsListRow(title = "橫式素材", icon = VsIcons.SmartphoneLandscape, onClick = { onPick(chosen, AutoPick.LANDSCAPE) })
            }
            Column(Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.s5)) {
                VsSheetHeader(monthLabel(chosen)) {
                    TextButton(onClick = { month = null }) { Text("換月份") }
                }
                if (orientation == ClipOrientation.PORTRAIT) portrait() else landscape()
                VsListRow(title = "不分方向", icon = VsIcons.Image, onClick = { onPick(chosen, AutoPick.ANY) })
                if (orientation == ClipOrientation.PORTRAIT) landscape() else portrait()
            }
        }
    }
}

/** 「2026 年 11 月」。 */
internal fun monthLabel(month: YearMonth): String = "${month.year} 年 ${month.monthValue} 月"

/** 影片畫面的方向（同 [frameOrientation]）；照片回 null。 */
internal fun MediaItem.videoOrientation(): ClipOrientation? = if (kind == MediaKind.VIDEO) frameOrientation() else null

/** 照片或影片畫面的方向（套用旋轉角度後）：高大於寬是直式、寬大於高是橫式；正方形或量不到尺寸回 null。 */
internal fun MediaItem.frameOrientation(): ClipOrientation? {
    if (width <= 0 || height <= 0) return null
    val (w, h) = if (rotation % 180 == 0) width to height else height to width
    return when {
        h > w -> ClipOrientation.PORTRAIT
        w > h -> ClipOrientation.LANDSCAPE
        else -> null
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
