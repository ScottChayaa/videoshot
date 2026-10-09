package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.TextFieldSize
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsChipSkeleton
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import kotlinx.coroutines.flow.first

/**
 * 首頁的地點與標籤篩選抽屜（階段 17 設計決議 3）。純顯示元件——勾選、搜尋字、展開都在
 * [FilterDraft]（`HomeViewModel` 管），這裡只畫 [FilterLists.visible] 算出來的結果。
 *
 * 【清除篩選】只清草稿的勾選，要按【套用】才生效；關掉抽屜（滑掉、點背景、返回）＝草稿作廢。
 * 小膠囊不帶張數、是切換型（核取方塊語意，TalkBack 唸「已選取」）。
 *
 * `skipPartiallyExpanded = true`：理由同 [MonthPickerSheet]，也讓 Robolectric 不必等展開動畫。
 * 【套用】固定在底部不跟著捲，候選很多時內容區自己捲。
 *
 * @param options 候選（地點與標籤混在一起，這裡依 `isPlace` 分兩區）
 * **抽屜滑出來的期間只畫骨架**（[VsChipSkeleton]），停穩了才換成小膠囊。幾十顆小膠囊組起來要一整幀
 * （開發測試版實測 31 顆約 0.14 秒）：跟抽屜外框擠在第一幀，抽屜要等它畫完才開始滑（按下去頓一下，
 * 比月份選擇器慢約 0.1 秒）；放在滑動途中，滑到一半會卡一下。停穩後才畫，那一幀沒有東西在動，看不出來。
 *
 * @param status 候選讀取中：兩區各顯示骨架（已勾選的照樣列出，骨架接在後面）；讀取失敗：捲動區最上面
 *        「讀取失敗」＋【重試】（[onRetry] 只重讀候選，草稿不動）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    options: List<FilterOption>,
    draft: FilterDraft,
    onToggle: (FilterOption) -> Unit,
    onQuery: (isPlace: Boolean, text: String) -> Unit,
    onExpand: (isPlace: Boolean) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    status: FilterOptionsStatus = FilterOptionsStatus.READY,
    onRetry: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chipsReady by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState) {
        // currentValue 在展開動畫結束、停穩時才變成 Expanded
        snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
        chipsReady = true
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(bottom = AppTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "篩選",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                VsButton("清除篩選", onClear, variant = ButtonVariant.Quiet)
            }
            // fill = false：內容少時抽屜只長到內容那麼高，多時才被限制在可用高度內、區塊自己捲；
            // 【套用】在捲動區外面，一直看得到
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s4),
            ) {
                if (status == FilterOptionsStatus.FAILED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "讀取失敗",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textDim,
                            modifier = Modifier.weight(1f),
                        )
                        VsButton("重試", onRetry, variant = ButtonVariant.Quiet)
                    }
                }
                FilterSectionBlock("地點", true, options, draft, status, chipsReady, onToggle, onQuery, onExpand)
                FilterSectionBlock("標籤", false, options, draft, status, chipsReady, onToggle, onQuery, onExpand)
            }
            VsButton("套用", onApply, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSectionBlock(
    title: String,
    isPlace: Boolean,
    options: List<FilterOption>,
    draft: FilterDraft,
    status: FilterOptionsStatus,
    chipsReady: Boolean,
    onToggle: (FilterOption) -> Unit,
    onQuery: (Boolean, String) -> Unit,
    onExpand: (Boolean) -> Unit,
) {
    val noun = if (isPlace) "地點" else "標籤"
    val section = FilterLists.visible(options, isPlace, draft)
    val query = draft.query[isPlace].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.text)
            VsTextField(
                value = query,
                onValueChange = { onQuery(isPlace, it) },
                modifier = Modifier.weight(1f),
                placeholder = "搜尋",
                size = TextFieldSize.Dense,
                semanticLabel = "搜尋$noun",
                leadingIcon = VsIcons.Search,
            )
        }
        if (!chipsReady) {
            // 滑動中：骨架的塊數跟等一下要畫的小膠囊差不多（有上限），換上小膠囊時抽屜高度不會差太多
            VsChipSkeleton(section.shown.size.coerceIn(SKELETON_MIN, SKELETON_MAX))
        } else if (section.shown.isEmpty() && status == FilterOptionsStatus.READY) {
            Text(
                if (query.isBlank()) "這段時間沒有$noun" else "找不到符合的$noun",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
            )
        } else {
            if (section.shown.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    for (option in section.shown) {
                        VsTagChip(
                            name = option.name,
                            kind = if (isPlace) ChipKind.PLACE else ChipKind.ofTagKind(option.tagKind),
                            selected = draft.isSelected(option),
                            isToggle = true,
                            onClick = { onToggle(option) },
                        )
                    }
                }
            }
            // 讀取中：已勾選的在上面照樣列出，骨架接在後面，不說「這段時間沒有…」（其實還沒讀到）。
            // 失敗時說明與【重試】在捲動區最上面，這裡什麼都不加
            if (status == FilterOptionsStatus.LOADING) VsChipSkeleton(SKELETON_MIN)
        }
        if (chipsReady && section.hiddenCount > 0) {
            VsButton(
                "顯示全部（${section.shown.size + section.hiddenCount}）",
                { onExpand(isPlace) },
                variant = ButtonVariant.Quiet,
            )
        }
    }
}

/** 骨架的塊數：讀取中不知道會有幾個，放一排的量；滑動中依目前的候選數，有上限。 */
private const val SKELETON_MIN = 6
private const val SKELETON_MAX = 10
