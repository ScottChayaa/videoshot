package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.details.Common

/**
 * 第三步：定義圖資。
 *
 * **每列 4 張**（規格第五節第三步的線框）—— 比第二步的 3 張密，因為這一步是「確認與勾選」，
 * 不是「從畫面裡挑」；辨識得出是哪一張就夠了。
 *
 * 這個 composable 只負責畫；規則全在 [Step3Store]，互動一律往上回報。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step3DetailsScreen(
    state: Step3State,
    bitmapFor: suspend (Int) -> ImageBitmap?,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onInvert: () -> Unit,
    onSelectUnapplied: () -> Unit,
    onEditEventDate: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onEditDescription: (String) -> Unit,
    onEditTags: (List<String>) -> Unit,
    onApply: () -> Unit,
    onFinish: () -> Unit,
    placeSuggestions: List<String> = emptyList(),
    tagSuggestions: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {

        Text(
            state.headerText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // sheet 已經在本機，這一行幾乎一閃而過（規格第五節）
        state.progressText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // FlowRow：與第二步的工具列同理，**換行不截斷**（手冊 §四）
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onSelectAll) { Text("全選") }
            TextButton(onClick = onSelectNone) { Text("全不選") }
            TextButton(onClick = onInvert) { Text("反選") }
            // 收尾時用的：一鍵勾選所有沒有綠點的（規格第五節快捷列）
            TextButton(onClick = onSelectUnapplied) { Text("未填的") }
        }

        // 縮圖牆與 dock 疊在同一個 Box：dock 貼底、縮圖牆鋪滿——**不讓 dock 用掉多少
        // 縮圖牆就少多少**。兩者若照順序各佔 Column 一段，dock 內容一多（比如同時展開
        // 四個欄位＋提示行）會把縮圖牆擠到只剩 0，矮螢幕上使用者連一張縮圖都點不到；
        // 疊在一起後縮圖牆永遠拿滿剩餘空間，dock 需要多高就自己往上長，蓋住的那幾張
        // 縮圖捲動一下就露出來，不是「消失」。
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.cells, key = { it.cell }) { cell ->
                    Step3Thumb(
                        cell = cell,
                        selected = cell.cell in state.selected,
                        applied = state.details[cell.cell]?.applied == true,
                        bitmapFor = bitmapFor,
                        onToggle = { onToggle(cell.cell) },
                    )
                }
            }

            Step3Dock(
                state = state,
                onEditEventDate = onEditEventDate,
                onEditPlace = onEditPlace,
                onEditDescription = onEditDescription,
                onEditTags = onEditTags,
                onApply = onApply,
                onFinish = onFinish,
                placeSuggestions = placeSuggestions,
                tagSuggestions = tagSuggestions,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * 一格。**綠點與紅框分開表達**（規格第五節的線框）：
 * 綠點＝這張的圖資套用過了，紅框＝現在勾選中。兩件事會同時成立，用同一個記號說不清楚。
 *
 * 顏色不是唯一的訊號 —— 兩者都寫進 `contentDescription`，輔助技術也讀得到（手冊 §零）。
 */
@Composable
private fun Step3Thumb(
    cell: Step3Cell,
    selected: Boolean,
    applied: Boolean,
    bitmapFor: suspend (Int) -> ImageBitmap?,
    onToggle: () -> Unit,
) {
    val label = buildString {
        append("第三步的格子 ")
        append(formatClock(cell.atSec))
        if (applied) append("・已套用")
        if (selected) append("・已勾選")
    }
    Box(
        Modifier
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.error else Color.Transparent,
            )
            .clickable { onToggle() }
            .semantics { contentDescription = label },
    ) {
        val bitmap by produceState<ImageBitmap?>(null, cell.cell, bitmapFor) {
            value = bitmapFor(cell.cell)
        }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 時間標籤同時是測試點得到的目標，也是使用者辨認「這是哪一張」的依據
        Text(
            formatClock(cell.atSec),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
        )
        if (applied) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(8.dp)
                    .background(Color(0xFF2E7D32), CircleShape),
            )
        }
    }
}

/**
 * 底部的**單一 dock**：抽屜、提示行、主按鈕**收成同一塊**（手冊 §四第三步第一條）。
 *
 * 之所以不是三層：抽屜／按鈕列／導覽列各佔一條，縮圖區會被壓到剩四成 —— 而縮圖區
 * 正是使用者用來判斷「現在勾的是哪幾張」的地方。精靈本來就沒有導覽列（規格第五節），
 * 抽屜與按鈕再合起來，就只剩一塊。
 *
 * 沒有勾選時**抽屜收合、主按鈕留著** —— 收掉按鈕會讓使用者在「全不選」之後無路可走。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step3Dock(
    state: Step3State,
    onEditEventDate: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onEditDescription: (String) -> Unit,
    onEditTags: (List<String>) -> Unit,
    onApply: () -> Unit,
    onFinish: () -> Unit,
    placeSuggestions: List<String>,
    tagSuggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.selected.isNotEmpty()) {
            Text(state.drawerTitle, style = MaterialTheme.typography.titleSmall)

            // 欄位本身可以滾動，**提示行與主按鈕不行** —— 欄位一多，矮螢幕上剩下的高度就不夠
            // 同時擠下所有東西；輸入框可以捲動找，但「按下去會發生什麼事」永遠要摸得到，
            // 不能因為欄位太多就被擠出畫面外（規格第五節、手冊 §四第三步）。
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DrawerTextField(
                    label = "時間",
                    field = state.eventDateField,
                    edited = state.patch.eventDate,
                    // 中性說明，不是警告色 —— 預設值永遠是上傳日，這是常態不是例外（規格第五節欄位表）
                    supporting = "預設帶入 YouTube 的上傳日期，可以改成實際拍攝日",
                    onEdit = onEditEventDate,
                )

                DrawerTextField(
                    label = "地點",
                    field = state.placeField,
                    edited = state.patch.place,
                    supporting = null,
                    onEdit = onEditPlace,
                )
                Suggestions(placeSuggestions, onPick = onEditPlace)

                TagField(
                    field = state.tagsField,
                    edited = state.patch.tags,
                    suggestions = tagSuggestions,
                    onEdit = onEditTags,
                )

                DrawerTextField(
                    label = "描述",
                    field = state.descriptionField,
                    edited = state.patch.description,
                    // 描述允許留空：18 張每張打一段字沒有人會做完（規格第五節欄位表）
                    supporting = "留空，之後 AI 補",
                    onEdit = onEditDescription,
                )
            }
        }

        // 這條規則**要看得見**，不能只存在於腦袋裡（規格第五節、手冊 §四第三步第三條）
        state.hintLine?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { contentDescription = "將更新的欄位" },
            )
        }

        Button(
            onClick = { if (state.mainActionIsFinish) onFinish() else onApply() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(state.mainButtonLabel) }
    }
}

/**
 * 抽屜裡的一個文字欄位。
 *
 * 顯示的值有三種來源，優先序就是這個順序：
 * 1. `edited` —— 使用者在這一輪打過的字（含空字串＝要清空）
 * 2. `Common.One` —— 勾選中的張數值一致，直接顯示它
 * 3. `Common.Mixed` —— 顯示〈多個值〉佔位字樣，**不動它就不會變**
 */
@Composable
private fun DrawerTextField(
    label: String,
    field: Common<out Any?>,
    edited: String?,
    /**
     * 欄位下方那一行說明。**不要用 `placeholder`** —— M3 的 placeholder 只在欄位聚焦且空白時出現，
     * 而「預設帶入上傳日期」與「留空，之後 AI 補」正是使用者**還沒碰這個欄位**時要看到的話。
     */
    supporting: String?,
    onEdit: (String) -> Unit,
) {
    val shown = edited ?: (field as? Common.One<*>)?.value?.toString() ?: ""
    val mixed = edited == null && field is Common.Mixed
    OutlinedTextField(
        value = shown,
        onValueChange = onEdit,
        label = { Text(label) },
        singleLine = true,
        supportingText = when {
            // 〈多個值〉：不動它就不會變（規格第五節套用語意表）。同理不能放 placeholder
            // 紫色斜體（規格第五節）—— 斜體才是主要訊號，顏色是輔助；
            // 只靠顏色的話色覺障礙的使用者分不出「大家都空著」與「各不相同」
            mixed -> {
                {
                    Text(
                        "〈多個值〉",
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            supporting != null -> { { Text(supporting) } }
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
    )
}

/** 既有值建議（`SELECT DISTINCT place` 等）。點一下就填進欄位。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(values: List<String>, onPick: (String) -> Unit) {
    if (values.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            AssistChip(onClick = { onPick(value) }, label = { Text(value) })
        }
    }
}

/**
 * 標籤欄位。chip 形式（規格第五節欄位表），**整組覆蓋**：
 * 每一次增刪都把完整的一組往上送，不送差異 —— 差異在「整組覆蓋」的語意下沒有意義。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagField(
    field: Common<List<String>>,
    edited: List<String>?,
    suggestions: List<String>,
    onEdit: (List<String>) -> Unit,
) {
    val shown = edited ?: (field as? Common.One<List<String>>)?.value ?: emptyList()
    var typing by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("標籤", style = MaterialTheme.typography.labelMedium)
        if (edited == null && field is Common.Mixed) {
            Text(
                "〈多個值〉",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { onEdit(shown - tag) },
                    label = { Text(tag) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typing,
                onValueChange = { typing = it },
                singleLine = true,
                label = { Text("新增標籤") },
                modifier = Modifier.weight(1f).semantics { contentDescription = "新增標籤" },
            )
            TextButton(onClick = {
                if (typing.isNotBlank()) { onEdit(shown + typing); typing = "" }
            }) { Text("＋") }
        }
        Suggestions(suggestions.filterNot { it in shown }, onPick = { onEdit(shown + it) })
    }
}
