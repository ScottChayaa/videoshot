package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
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

        // 任務 4 會把這顆按鈕換成完整的 dock（抽屜＋提示行＋按鈕）
        Button(
            onClick = { if (state.patch.isEmpty) onFinish() else onApply() },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(state.mainButtonLabel) }
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
