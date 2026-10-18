package com.xenyaa.videoshot.wizard

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.library.dao.TagNameKind
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsDatePickerDialog
import com.xenyaa.videoshot.ui.common.VsFieldButton
import com.xenyaa.videoshot.ui.common.VsSelectedBadge
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbGridGap
import kotlinx.coroutines.launch

/** 欄位 → 圖示與名稱。底部按鈕、縮圖左下的小圖示、TalkBack 的唸法都用這一份，順序同 [DetailField]。 */
internal val DetailField.icon: ImageVector
    get() = when (this) {
        DetailField.DATE -> VsIcons.Calendar
        DetailField.PLACE -> VsIcons.MapPin
        DetailField.TAGS -> VsIcons.Tag
        DetailField.DESCRIPTION -> VsIcons.Notes
    }

internal val DetailField.label: String
    get() = when (this) {
        DetailField.DATE -> "時間"
        DetailField.PLACE -> "地點"
        DetailField.TAGS -> "標籤"
        DetailField.DESCRIPTION -> "描述"
    }

/**
 * 第三步：定義圖資（2026-10-18 scott 改版：原本的快捷列＋詳細資訊抽屜＋【套用】太難操作）。
 *
 * 畫面只有縮圖牆與底部一列：【全選】【時間】【地點】【標籤】【描述】＋【完成】。
 * 四個欄位按鈕各開自己的選擇器，**按【確定】的那一刻就寫進目前勾選的那幾張**（[onApply]），沒有另外的【套用】；
 * 「沒動過就不改」由選擇器自己判斷（見 `Step3Pickers.kt`）。
 *
 * 縮圖牆跟第二步一樣：**每列 4 張正方形、左右貼齊螢幕、格線 [ThumbGridGap]**。
 *
 * @param requireTimeAndPlace 取圖精靈是 true：每一張都有時間與地點（[Step3State.canFinish]）才完成；
 *        還不能完成時【完成】是淡化的，按了跳提示。批次編輯是 false —— 只寫回改過的那幾張，隨時可完成。
 */
@Composable
fun Step3DetailsScreen(
    state: Step3State,
    bitmapFor: suspend (Int) -> ImageBitmap?,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onApply: (DetailsPatch) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    placeSuggestions: List<String> = emptyList(),
    tagSuggestions: List<TagNameKind> = emptyList(),
    requireTimeAndPlace: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 新的提示蓋掉舊的：連按兩次不會排隊等兩輪
    fun hint(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }
    var picking by remember { mutableStateOf<DetailField?>(null) }
    fun open(field: DetailField) {
        if (state.selected.isEmpty()) hint("先勾選圖片") else picking = field
    }
    val canFinish = !requireTimeAndPlace || state.canFinish

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // sheet 已經在本機，這一行幾乎一閃而過（規格第五節）
            state.progressText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ThumbGridGap),
                verticalArrangement = Arrangement.spacedBy(ThumbGridGap),
            ) {
                items(state.cells, key = { it.cell }) { cell ->
                    Step3Thumb(
                        cell = cell,
                        details = state.details[cell.cell],
                        selected = cell.cell in state.selected,
                        bitmapFor = bitmapFor,
                        onToggle = { onToggle(cell.cell) },
                    )
                }
            }

            VsActionDock {
                // 五顆欄位按鈕彼此不留間距（各自 48dp 寬、已有留白），【完成】才拿得到夠寬的位置
                Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                    VsFieldButton(VsIcons.ImagesCheck, "全選", onSelectAll)
                    DetailField.entries.forEach { f ->
                        VsFieldButton(f.icon, f.label, onClick = { open(f) }, done = state.isComplete(f))
                    }
                }
                VsButton(
                    "完成",
                    onClick = { if (canFinish) onFinish() else hint("每一張都要先設定時間和地點") },
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .semantics { if (!canFinish) stateDescription = "還不能完成" },
                    dimmed = !canFinish,
                )
            }
        }
        // 浮在底部列正上方（同精靈外殼的「已截圖」提示）
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = AppTheme.spacing.navHeight),
        )
    }

    val count = state.selected.size
    val close = { picking = null }
    val confirm: (DetailsPatch) -> Unit = { onApply(it); picking = null }
    when (picking) {
        DetailField.DATE -> {
            val one = (state.eventDateField as? Common.One<String>)?.value
            // 選回勾選那幾張原本一致的那天＝沒動過
            VsDatePickerDialog(initial = one, onPick = { d -> if (d != one) onApply(DetailsPatch(eventDate = d)) }, onDismiss = close)
        }
        DetailField.PLACE -> PlacePickerSheet(state.placeField, count, placeSuggestions, confirm, close)
        DetailField.TAGS -> TagPickerSheet(state.tagsField, count, tagSuggestions, confirm, close)
        DetailField.DESCRIPTION -> DescriptionPickerSheet(state.descriptionField, count, confirm, close)
        null -> Unit
    }
}

/**
 * 一格（正方形、直角，同第二步）。左上打勾徽章＝勾選中；左下四個小圖示＝時間／地點／標籤／描述，有值亮白、沒有半透明。
 * （2026-10-18 拿掉右上的已套用綠勾與右下的時間：這一步的重點是替圖填資料，時間點在長按浮窗裡看。）
 *
 * 顏色不是唯一的訊號 —— 勾選與各欄位有沒有值都寫進整格的 `contentDescription`（手冊 §零）；
 * 徽章與小圖示本身是純裝飾，不另設名稱。
 */
@Composable
private fun Step3Thumb(
    cell: Step3Cell,
    details: ShotDetails?,
    selected: Boolean,
    bitmapFor: suspend (Int) -> ImageBitmap?,
    onToggle: () -> Unit,
) {
    val label = thumbLabel(cell, details, selected)
    val cellShape = RectangleShape
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(cellShape)
            .background(AppTheme.colors.surface2)
            .focusRing(cellShape)
            .clickable { onToggle() }
            .semantics { contentDescription = label },
    ) {
        val bitmap by produceState<ImageBitmap?>(null, cell.cell, bitmapFor) { value = bitmapFor(cell.cell) }
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (selected) VsSelectedBadge(Modifier.align(Alignment.TopStart), semanticLabel = null)
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(AppTheme.spacing.s1)
                .clip(RoundedCornerShape(AppTheme.radii.sm))
                .background(AppTheme.colors.scrim)
                .padding(horizontal = 3.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DetailField.entries.forEach { f ->
                val on = details?.has(f) == true
                Icon(f.icon, contentDescription = null, tint = Color.White.copy(alpha = if (on) 1f else 0.35f), modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** 「第三步的格子 01:10・已勾選・已設定：時間、地點・未設定：標籤、描述」。 */
internal fun thumbLabel(cell: Step3Cell, details: ShotDetails?, selected: Boolean): String = buildString {
    append("第三步的格子 ")
    append(formatClock(cell.atSec))
    if (selected) append("・已勾選")
    val (set, unset) = DetailField.entries.partition { details?.has(it) == true }
    if (set.isNotEmpty()) append("・已設定：").append(set.joinToString("、") { it.label })
    if (unset.isNotEmpty()) append("・未設定：").append(unset.joinToString("、") { it.label })
}
