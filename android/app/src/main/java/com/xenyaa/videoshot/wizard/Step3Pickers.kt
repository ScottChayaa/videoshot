package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.normalizeTags
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsChipPager
import com.xenyaa.videoshot.ui.common.VsRemovableTag
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.theme.AppTheme

/** 各張的值不一致時的佔位字樣（同 `VsTextField` 的 mixedPlaceholder）。 */
private const val MIXED = "〈多個值〉"

/**
 * 取圖第三步的地點選擇器（規格第五節第三步的欄位表）。版面同照片編輯抽屜的地點區：
 * 輸入框＋用過的地點（[VsChipPager]，打字即時篩選），點小膠囊填進輸入框。
 *
 * **動過才送**：輸入框的值跟起始值一樣就當沒動過，按【確定】只關抽屜 —— 全選後打開看一眼就按確定，不能把各張不同的地點洗成一樣。
 * 輸入框空著時【確定】不能按：多個值時起始是空的，空著按確定沒有意義；要清掉地點按【清除】。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlacePickerSheet(
    field: Common<String?>,
    count: Int,
    suggestions: List<String>,
    onConfirm: (DetailsPatch) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = (field as? Common.One<String?>)?.value.orEmpty()
    var input by remember { mutableStateOf(initial) }
    val query = input.trim()
    PickerSheet(
        title = "地點",
        count = count,
        canClear = field.hasAnyValue(),
        canConfirm = query.isNotEmpty(),
        onClear = { onConfirm(DetailsPatch(place = "")) },
        onConfirm = { if (query == initial) onDismiss() else onConfirm(DetailsPatch(place = query)) },
        onDismiss = onDismiss,
    ) {
        VsTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = if (field is Common.Mixed) MIXED else "輸入或從下面挑",
            mixedPlaceholder = field is Common.Mixed,
            semanticLabel = "地點名稱",
        )
        // 輸入框裡已經是一個既有地點就列出其他全部，方便換一個；打的是部分的字才過濾（同照片編輯抽屜）
        val places = remember(suggestions, query) {
            val filtering = query.isNotEmpty() && query !in suggestions
            suggestions.filter { it != query && (!filtering || it.contains(query, ignoreCase = true)) }
        }
        VsChipPager(names = places, kind = ChipKind.PLACE, onClick = { i -> input = places[i] }, resetKey = query)
    }
}

/**
 * 標籤選擇器。由上到下：已選的標籤（可點 ✕ 移除）→ 輸入框＋【加入】（鍵盤 Enter 同【加入】）→ 用過的標籤。
 * 【確定】＝**整組取代**勾選那幾張的標籤；全部移除再確定＝清除。新打的標籤當成「其他」（不另外問種類）。
 * 多個值時起始是空的、顯示〈多個值〉；沒加任何標籤就按確定＝沒動過。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerSheet(
    field: Common<List<String>>,
    count: Int,
    suggestions: List<String>,
    onConfirm: (DetailsPatch) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = (field as? Common.One<List<String>>)?.value.orEmpty()
    var tags by remember { mutableStateOf(initial) }
    var typing by remember { mutableStateOf("") }
    fun add(raw: String) {
        normalizeTags(listOf(raw)).firstOrNull()?.let { tags = normalizeTags(tags + it) }
        typing = ""
    }
    PickerSheet(
        title = "標籤",
        count = count,
        canClear = field.hasAnyTag(),
        canConfirm = true,
        onClear = { onConfirm(DetailsPatch(tags = emptyList())) },
        onConfirm = {
            // 「動過」是拿現值跟起始值比：加了又拿掉＝沒動過
            if (normalizeTags(tags) == normalizeTags(initial)) onDismiss() else onConfirm(DetailsPatch(tags = normalizeTags(tags)))
        },
        onDismiss = onDismiss,
    ) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                tags.forEach { tag -> VsRemovableTag(tag, onRemove = { tags = tags - tag }) }
            }
        } else if (field is Common.Mixed) {
            Text(MIXED, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = AppTheme.colors.textDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2), verticalAlignment = Alignment.Bottom) {
            VsTextField(
                value = typing,
                onValueChange = { typing = it },
                modifier = Modifier.weight(1f),
                placeholder = "新增標籤",
                semanticLabel = "新增標籤",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(typing) }),
            )
            VsButton("加入", onClick = { add(typing) }, variant = ButtonVariant.Secondary)
        }
        val q = typing.trim()
        val options = remember(suggestions, tags, q) {
            suggestions.filter { it !in tags && (q.isEmpty() || it.contains(q, ignoreCase = true)) }
        }
        VsChipPager(
            names = options,
            kind = ChipKind.TAG,
            onClick = { i -> add(options[i]) },
            resetKey = q,
        )
    }
}

/**
 * 描述選擇器：多行輸入框。改過才送（清空也算改過＝清除）；多個值時起始是空的、顯示〈多個值〉，沒打字就按確定＝沒動過。
 */
@Composable
internal fun DescriptionPickerSheet(
    field: Common<String?>,
    count: Int,
    onConfirm: (DetailsPatch) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = (field as? Common.One<String?>)?.value.orEmpty()
    var input by remember { mutableStateOf(initial) }
    PickerSheet(
        title = "描述",
        count = count,
        canClear = field.hasAnyValue(),
        canConfirm = true,
        onClear = { onConfirm(DetailsPatch(description = "")) },
        // 比對 trim 後的：多個值時只打空白會被當成清除，但「沒動它就不會變」，所以視同沒動過
        onConfirm = { if (input.trim() == initial.trim()) onDismiss() else onConfirm(DetailsPatch(description = input)) },
        onDismiss = onDismiss,
    ) {
        VsTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = if (field is Common.Mixed) MIXED else null,
            mixedPlaceholder = field is Common.Mixed,
            semanticLabel = "描述",
            lines = 3,
        )
    }
}

/**
 * 三個選擇器共用的外框：標題「地點 · 套用到已勾選的 N 張」、可捲動的內容、底部【清除】【確定】。
 * 鍵盤開著時整塊被頂上去（`imePadding`），按鈕不會被蓋住。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    count: Int,
    canClear: Boolean,
    canConfirm: Boolean,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    VsBottomSheet(onDismissRequest = onDismiss) {
        VsSheetHeader("$title · 套用到已勾選的 $count 張")
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(top = AppTheme.spacing.s3, bottom = AppTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
                content = content,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                VsButton("清除", onClick = onClear, modifier = Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = canClear)
                VsButton("確定", onClick = onConfirm, modifier = Modifier.weight(2f), enabled = canConfirm)
            }
        }
    }
}

/** 勾選的那幾張至少一張有值（【清除】才有東西可清）。 */
private fun Common<String?>.hasAnyValue(): Boolean = when (this) {
    is Common.One -> !value.isNullOrBlank()
    Common.Mixed -> true
    Common.None -> false
}

private fun Common<List<String>>.hasAnyTag(): Boolean = when (this) {
    is Common.One -> value.isNotEmpty()
    Common.Mixed -> true
    Common.None -> false
}
