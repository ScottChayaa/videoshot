package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
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
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Step2GridScreen(
    state: Step2State,
    source: FrameSource,
    haptics: Haptics,
    onToggle: (Int) -> Unit,
    onPlayFrame: (Int) -> Unit,
    onTakenTap: (Int) -> Unit,
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
                    haptics = haptics,
                    onToggle = { onToggle(frameIndex) },
                    onTakenTap = { onTakenTap(frameIndex) },
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
 * 一格。**整格都是點擊區**，點＝勾選、長按 0.5 秒＝跳播（規格第五節互動表）。
 *
 * 「點＝播放、小圓圈＝勾選」曾評估並否決：96px 寬的格子上小圓圈只有約 20px，
 * 選 30 張要精準點 30 次。挑圖是主要動作，它該拿到最順、不需要瞄準的手勢。
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FrameCell(
    frameIndex: Int,
    atSec: Double,
    selected: Boolean,
    taken: Boolean,
    playing: Boolean,
    source: FrameSource,
    haptics: Haptics,
    onToggle: () -> Unit,
    onTakenTap: () -> Unit,
    onPlay: () -> Unit,
    onHintDismiss: () -> Unit,
) {
    val clock = formatClock(atSec)
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.error      // 紅框＝已選
        playing -> MaterialTheme.colorScheme.primary     // 藍框＝播放器停在這格
        else -> Color.Transparent
    }

    // 外層 Box **不掛點擊語意**，只負責邊框／背景／排版。
    // ▶ 按鈕不能是「整格點擊區」那個語意節點的子孫 —— combinedClickable 會把子孫
    // 合併進自己（無障礙用途），子孫若也是可點擊節點，合併時它的 OnClick 會蓋掉
    // 整格自己的 OnClick，點下去就變成播放而不是勾選。讓 ▶ 當外層 Box 的手足、
    // 不落在「整格」的合併子樹裡，兩個語意節點才不會互相吃掉。
    Box(
        Modifier
            .aspectRatio(16f / 9f)
            .border(2.dp, borderColor)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        onHintDismiss()
                        // 已收藏的格子點下去**明確提示**，不是靜靜沒反應（規格第五節）
                        if (taken) onTakenTap() else onToggle()
                    },
                    onLongClick = {
                        onHintDismiss()
                        haptics.tick()
                        onPlay()
                    },
                )
                // 一格一個語意節點，測試與輔助技術都靠它定位
                .semantics { contentDescription = "第 ${frameIndex + 1} 格 $clock" }
        ) {
            FrameImage(source, frameIndex, Modifier.fillMaxSize())

            if (taken) {
                // 灰＋鎖：先前已收藏過（規格第五節互動表）
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                Text("🔒", modifier = Modifier.align(Alignment.Center))
            }

            Text(
                clock,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp),
            )
        }

        // ▶ 與長按是同一件事。**長按不是唯一入口** —— 鍵盤與輔助技術到不了長按（規格第五節）
        // .size(40.dp)：`ButtonDefaults.MinHeight` 是 40dp（`MinWidth` 是 58dp，這裡用不到）。
        // 不圈住的話，TextButton 預設點擊區（58dp 寬）會逼近格子中心，蓋過整格點擊。
        // 40dp 錨在 TopEnd 時佔據 [格寬-40dp, 格寬] 的水平範圍。格寬實測 99dp
        // （Step2InteractionTest 量出來的，LazyVerticalGrid 三欄＋8dp 左右 padding＋
        // 4dp 欄距），格子中心（49.5dp）不落在 [59dp, 99dp] 內，所以整格點擊仍然
        // 命中內層 Box 的 combinedClickable，同時保住 40dp 的無障礙最小觸控區。
        // （若換成 `ButtonDefaults.MinWidth` 58dp，範圍會是 [41dp, 99dp]，
        // 反而蓋住中心 —— 這正是原本沒設 .size() 時會撞到的情況。）
        TextButton(
            onClick = { onHintDismiss(); onPlay() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .semantics { contentDescription = "跳到這一段" },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("▶", color = Color.White)
        }
    }
}

/**
 * 圖是**要畫的時候才去拿**（規格第二節第 6 點：148 格全部留在記憶體要 34 MB）。
 *
 * `source` 也是 key，**不能只用 frameIndex**：`LazyVerticalGrid` 的 item 同樣以 frameIndex 當 key，
 * 所以換一支影片、`Step2Store` 被換掉之後，item 的槽位會存活下來；producer 只看 frameIndex
 * 的話不會重跑，那些格子會繼續畫**前一支影片**的 bitmap，直到使用者把它們捲出畫面再捲回來。
 */
@Composable
private fun FrameImage(source: FrameSource, frameIndex: Int, modifier: Modifier) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, source, frameIndex) {
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
