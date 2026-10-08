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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.TextFieldSize
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme

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
 * @param status 候選讀取中：兩區各顯示一行「載入中…」（已勾選的照樣列出）；讀取失敗：捲動區最上面
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                FilterSectionBlock("地點", true, options, draft, status, onToggle, onQuery, onExpand)
                FilterSectionBlock("標籤", false, options, draft, status, onToggle, onQuery, onExpand)
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
        if (section.shown.isEmpty() && status == FilterOptionsStatus.READY) {
            Text(
                if (query.isBlank()) "這段時間沒有$noun" else "找不到符合的$noun",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textDim,
            )
        } else {
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
        // 讀取中：中性的一行，不說「這段時間沒有…」（其實還沒讀到）；已勾選的在上面照樣列出。
        // 失敗時說明與【重試】在捲動區最上面，這裡什麼都不加
        if (status == FilterOptionsStatus.LOADING) {
            Text("載入中…", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textDim)
        }
        if (section.hiddenCount > 0) {
            VsButton(
                "顯示全部（${section.shown.size + section.hiddenCount}）",
                { onExpand(isPlace) },
                variant = ButtonVariant.Quiet,
            )
        }
    }
}
