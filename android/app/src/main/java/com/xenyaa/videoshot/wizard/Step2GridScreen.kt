package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.FlowRow
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
import com.xenyaa.videoshot.wizard.frames.FrameSource

/**
 * 第二步：從縮圖牆挑圖。
 *
 * **每列 3 張**（規格第五節「畫質與排列」）—— L2 每列 5 張曾評估並否決，
 * 單格辨識度不足以支撐「挑圖」這個動作本身。
 *
 * 這個 composable 只負責畫；規則全在 [Step2Store]，互動一律往上回報。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Step2GridScreen(
    state: Step2State,
    source: FrameSource,
    onToggle: (Int) -> Unit,
    onPlayFrame: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onShowAll: (Boolean) -> Unit,
    onOnlySelected: (Boolean) -> Unit,
    onDismissHint: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {

        StatusBar(state, onShowAll)

        if (state.plan.lowQuality) {
            Text(
                "這支影片只有較低畫質的縮圖",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // FlowRow：工具列**換行不截斷**，文字不會被切掉（手冊 §四第二步）
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onSelectAll) { Text("全部選取") }
            TextButton(onClick = { onOnlySelected(!state.onlySelected) }) {
                Text(if (state.onlySelected) "看全部" else "只看已選")
            }
        }

        if (!state.hintSeen) {
            Text(
                "點一下收藏・長按看看那一段",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .semantics { contentDescription = "一次性提示" },
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.visible, key = { it }) { frameIndex ->
                FrameCell(
                    frameIndex = frameIndex,
                    atSec = state.plan.atSec.getOrElse(frameIndex) { 0.0 },
                    selected = frameIndex in state.selected,
                    taken = frameIndex in state.taken,
                    playing = state.playingFrame == frameIndex,
                    source = source,
                    onToggle = { onToggle(frameIndex) },
                    onPlay = { onPlayFrame(frameIndex) },
                    onHintDismiss = onDismissHint,
                )
            }
        }

        BottomBar(state, onNext)
    }
}

/** 頂部狀態列。手冊 §四要求【顯示全部】與【重新過濾】可以來回切。 */
@Composable
private fun StatusBar(state: Step2State, onShowAll: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(state.statusText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (state.hiddenCount > 0 && !state.converging) {
            TextButton(onClick = { onShowAll(!state.showAll) }) {
                Text(if (state.showAll) "重新過濾" else "顯示全部")
            }
        }
    }
}

/** 底部**只有一列**，底下不再疊導覽列（手冊 §四第二步）。 */
@Composable
private fun BottomBar(state: Step2State, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${state.kept.size} 張候選 · 已選 ${state.selectedCount}",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onNext, enabled = state.selectedCount > 0) {
            Text("下一步（${state.selectedCount} 張）")
        }
    }
}

/**
 * 一格。整格都是點擊區（規格第五節：挑圖是主要動作，必須拿到最順的手勢）。
 * 長按與右上 ▶ 的手勢在 Task 4b.5 接上。
 */
@Composable
private fun FrameCell(
    frameIndex: Int,
    atSec: Double,
    selected: Boolean,
    taken: Boolean,
    playing: Boolean,
    source: FrameSource,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    onHintDismiss: () -> Unit,
) {
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.error      // 紅框＝已選
        playing -> MaterialTheme.colorScheme.primary     // 藍框＝播放器停在這格
        else -> Color.Transparent
    }

    Box(
        Modifier
            .aspectRatio(16f / 9f)
            .border(2.dp, borderColor)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        FrameImage(source, frameIndex, Modifier.fillMaxSize())

        if (taken) {
            // 灰＋鎖：先前已收藏過（規格第五節互動表）
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Text("🔒", modifier = Modifier.align(Alignment.Center))
        }

        Text(
            formatClock(atSec),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp),
        )

        TextButton(
            onClick = onPlay,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { contentDescription = "跳到這一段" },
        ) {
            Text("▶", color = Color.White)
        }
    }
}

/** 圖是**要畫的時候才去拿**（規格第二節第 6 點：148 格全部留在記憶體要 34 MB）。 */
@Composable
private fun FrameImage(source: FrameSource, frameIndex: Int, modifier: Modifier) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, frameIndex) {
        value = source.bitmapOf(frameIndex)
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

/** 影片時間標籤。超過一小時就讓分鐘數長過 60（`61:01`）—— 取圖用不到「時」這一位。 */
fun formatClock(sec: Double): String {
    val total = sec.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
