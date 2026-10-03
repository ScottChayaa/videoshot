package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsHintCard
import com.xenyaa.videoshot.ui.common.VsToolbarPill
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

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
    /**
     * 第 N 格的圖。**不收 `FrameSource`** —— 手動補圖來自 `drafts/…/manual/`，
     * 不是 storyboard 的來源；畫面只需要「給我第 N 格的圖」，不必知道它從哪來
     * （與 `thumbFor(shot)` 把兩種來源收斂在一處是同一個道理，規格第三節邊界 2）。
     */
    bitmapFor: suspend (Int) -> ImageBitmap?,
    haptics: Haptics,
    onToggle: (Int) -> Unit,
    onPlayFrame: (Int) -> Unit,
    onTakenTap: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onTakeShot: () -> Unit,
    onShowAll: (Boolean) -> Unit,
    onOnlySelected: (Boolean) -> Unit,
    onDismissHint: () -> Unit,
    onNext: () -> Unit,
    /** 上一次【截圖】的失敗原因；null 代表沒有要說的。 */
    captureError: CaptureError? = null,
    onPickFromGallery: () -> Unit = {},
    onDismissCaptureError: () -> Unit = {},
    /** 微調某一格的秒數。**只有相簿來的格子會呼叫它**（規格第五節）。 */
    onNudgeManual: (Int, Double) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {

        StatusBar(state, onShowAll)

        // FlowRow：工具列**換行不截斷**，文字不會被切掉（手冊 §四第二步）
        // 順序照規格第五節的線框：全部選取│截圖│只看已選
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surface)
                .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            VsToolbarPill("全部選取", onSelectAll, icon = VsIcons.Check)
            VsToolbarPill("截圖", onTakeShot, icon = VsIcons.ImagePlus)
            // 切換型：文字固定「只看已選」，開關狀態用主色底＋語意 selected 表達
            // （原本是文字在「只看已選／看全部」間切換——行為相同，呈現改成原型的 .on）
            VsToolbarPill(
                "只看已選",
                { onOnlySelected(!state.onlySelected) },
                icon = VsIcons.Eye,
                selected = state.onlySelected,
                isToggle = true,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.border)

        captureError?.let {
            CaptureErrorBar(it, onPickFromGallery, onDismissCaptureError)
        }

        if (!state.hintSeen) {
            VsHintCard(
                "點一下就收藏；每格右上角的 ▶ 可以跳到那一段看看。",
                Modifier.semantics { contentDescription = "一次性提示" },
                onDismiss = onDismissHint,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // 原型 .wz-grid：gap 4、padding 4
            contentPadding = PaddingValues(AppTheme.spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
        ) {
            items(state.visible, key = { it }) { frameIndex ->
                FrameCell(
                    frameIndex = frameIndex,
                    atSec = state.atSecOf(frameIndex),
                    manual = state.isManual(frameIndex),
                    nudgable = state.manual.firstOrNull { it.cellIndex == frameIndex }?.fromGallery == true,
                    onNudge = { delta -> onNudgeManual(frameIndex, delta) },
                    selected = frameIndex in state.selected,
                    taken = frameIndex in state.taken,
                    playing = state.playingFrame == frameIndex,
                    bitmapFor = bitmapFor,
                    haptics = haptics,
                    onToggle = { onToggle(frameIndex) },
                    onTakenTap = { onTakenTap(frameIndex) },
                    onPlay = { onPlayFrame(frameIndex) },
                    onHintDismiss = onDismissHint,
                )
            }
        }

        // 底部**只有一列**，底下不再疊導覽列（手冊 §四第二步）
        VsActionDock(status = { Text("${state.candidateCount} 張候選 · 已選 ${state.selectedCount}") }) {
            VsButton("下一步（${state.selectedCount} 張）", onNext, enabled = state.selectedCount > 0)
        }
    }
}

/**
 * 截不到時的提示（規格第五節、手冊 §四第二步）。
 *
 * **廣告與黑畫面分開講。** 廣告是暫時的（等廣告播完再截一次就好），
 * 黑畫面與解不出圖則是這一格真的拿不到，只能從相簿補。講成同一句話會讓使用者
 * 對廣告那種情況做錯處置 —— 跑去翻相簿找一張根本不存在的截圖。
 *
 * 存檔失敗也不給相簿退路：存不進去的話，從相簿選一張同樣存不進去。
 */
@Composable
private fun CaptureErrorBar(
    error: CaptureError,
    onPickFromGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when (error) {
        CaptureError.AD_PLAYING -> "廣告播放中，等廣告結束再截一次"
        CaptureError.NOT_READY -> "播放器還沒準備好，稍等一下再截"
        CaptureError.SAVE_FAILED -> "存不進手機，請確認儲存空間還夠"
        CaptureError.BLACK_FRAME, CaptureError.NOT_DECODABLE -> "這一格截不到"
    }
    val offerGallery = error == CaptureError.BLACK_FRAME || error == CaptureError.NOT_DECODABLE
    // 警示用 warn 色，不是 danger／errorContainer（紅色只留給破壞性動作，手冊 §零）
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.warnWeak)
            .padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.warn,
            modifier = Modifier.weight(1f),
        )
        if (offerGallery) {
            VsButton("從相簿選", onPickFromGallery, variant = ButtonVariant.Secondary)
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(AppTheme.spacing.tap).focusRing(RoundedCornerShape(AppTheme.radii.sm)),
        ) {
            Icon(VsIcons.Close, contentDescription = "關閉", tint = AppTheme.colors.warn, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * 頂部狀態列（原型 `.wz-filter`）。手冊 §四要求【顯示全部】與【重新過濾】可以來回切。
 * 一行放不下就截字、不撐成兩行把縮圖擠下去；【顯示全部】觸控區 44dp。
 */
@Composable
private fun StatusBar(state: Step2State, onShowAll: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.accentWeak)
                .defaultMinSize(minHeight = AppTheme.spacing.tap)
                .padding(horizontal = AppTheme.spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        ) {
            Text(
                state.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.hiddenCount > 0 && !state.converging) {
                val shape = RoundedCornerShape(AppTheme.radii.sm)
                Box(
                    Modifier
                        .defaultMinSize(minWidth = AppTheme.spacing.tap, minHeight = AppTheme.spacing.tap)
                        .focusRing(shape)
                        .clip(shape)
                        .clickable(role = Role.Button) { onShowAll(!state.showAll) }
                        .padding(horizontal = AppTheme.spacing.s1),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.showAll) "重新過濾" else "顯示全部",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.accent,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.accentLine)
        if (state.plan.lowQuality) {
            Text(
                "這支影片只有較低畫質的縮圖",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textDim,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s1),
            )
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
    manual: Boolean,
    nudgable: Boolean,
    onNudge: (Double) -> Unit,
    selected: Boolean,
    taken: Boolean,
    playing: Boolean,
    bitmapFor: suspend (Int) -> ImageBitmap?,
    haptics: Haptics,
    onToggle: () -> Unit,
    onTakenTap: () -> Unit,
    onPlay: () -> Unit,
    onHintDismiss: () -> Unit,
) {
    val clock = formatClock(atSec)
    val accent = AppTheme.colors.accent
    val cellShape = RoundedCornerShape(AppTheme.radii.sm)
    val scrimPill = RoundedCornerShape(AppTheme.radii.sm)

    // 外層 Box **不掛點擊語意**，只負責圓角／背景／排版。
    // ▶ 按鈕不能是「整格點擊區」那個語意節點的子孫 —— combinedClickable 會把子孫
    // 合併進自己（無障礙用途），子孫若也是可點擊節點，合併時它的 OnClick 會蓋掉
    // 整格自己的 OnClick，點下去就變成播放而不是勾選。讓 ▶ 當外層 Box 的手足、
    // 不落在「整格」的合併子樹裡，兩個語意節點才不會互相吃掉。
    BoxWithConstraints(
        Modifier
            .aspectRatio(16f / 9f)
            // 已收藏：整格半透明（原型 .wz-cell.locked），不再蓋黑色遮罩與鎖頭
            .alpha(if (taken) 0.5f else 1f)
            .clip(cellShape)
            .background(AppTheme.colors.surface2)
    ) {
        val compact = maxWidth < NUDGE_COMPACT_WIDTH
        Box(
            Modifier
                .fillMaxSize()
                .focusRing(cellShape)
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
            FrameImage(bitmapFor, frameIndex, Modifier.fillMaxSize())

            // 外框畫在格子內緣、疊在圖上。已選（實線）優先於播放中（虛線），
            // 兩者用形狀區分而不是色相（原型 .wz-cell.sel／.now；手冊 §零：已選不能用紅色）
            if (selected) {
                Box(Modifier.fillMaxSize().border(3.dp, accent, cellShape))
            } else if (playing) {
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val w = 3.dp.toPx()
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(AppTheme.radii.sm.toPx() - w / 2),
                            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                        )
                    },
                )
            }

            if (selected) {
                SelectedBadge(Modifier.align(Alignment.TopStart))
            }

            if (taken) {
                // 先前已收藏過（規格第五節互動表）：左上「已收藏」小標
                Text(
                    "已收藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(AppTheme.spacing.s1)
                        .clip(scrimPill)
                        .background(AppTheme.colors.scrim)
                        .padding(horizontal = AppTheme.spacing.s1),
                )
            }

            Text(
                // 手動格前面加「截圖」，取代原本的 📷 標記（這一格是使用者自己補的，不是 YouTube 的
                // storyboard，規格第五節）；文字本身就是無障礙名稱（不另設 contentDescription，
                // 否則輔助技術會唸兩次）。放右下角 —— 右上角是 ▶，左上角是徽章。
                // 相簿來的手動格左下角還有 ±1 秒微調鈕，格子窄（< NUDGE_COMPACT_WIDTH）時兩者放不下，
                // 這時只留時間 —— 有微調鈕就已經看得出這是自己補的圖。
                if (manual && !(nudgable && compact)) "截圖 $clock" else clock,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppTheme.spacing.s1)
                    .clip(scrimPill)
                    .background(AppTheme.colors.scrim)
                    .padding(horizontal = AppTheme.spacing.s1),
            )
        }

        if (nudgable) {
            // ±1 秒微調：**只有相簿選來的圖有**。截圖的圖與秒數是同一瞬間取的，
            // 給了微調鈕等於承諾一件做不到的事（規格第五節、手冊第 93 行）。
            // 跟 ▶ 一樣是外層 Box 的手足，不在整格點擊的合併子樹裡；而且**實際尺寸（24×22dp）
            // 不蓋到格子中心**：Compose 會把小於 48dp 的觸控目標往外擴，但「實際範圍內的命中」
            // 一律贏過「擴張範圍內的命中」，所以只要實際範圍避開中心，點中心就是整格的勾選。
            // 格高最矮約 57dp（320dp 寬），鈕頂在 57−2−22＝33dp，中心在 28.5dp，留 4dp 餘裕。
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = AppTheme.spacing.s1, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NudgeButton("−", "往前 1 秒", scrimPill) { onNudge(-1.0) }
                NudgeButton("＋", "往後 1 秒", scrimPill) { onNudge(1.0) }
            }
        }

        // ▶ 與長按是同一件事。**長按不是唯一入口** —— 鍵盤與輔助技術到不了長按（規格第五節）
        // .size(40.dp)：觸控區維持 40dp，視覺是裡面 30dp 的 scrim 方塊（原型 .peek，離邊 4dp）。
        // 不圈住的話，點擊區會逼近格子中心，蓋過整格點擊。
        // 40dp 錨在 TopEnd 時佔據 [格寬-40dp, 格寬] 的水平範圍。格寬實測約 99～101dp
        // （Step2InteractionTest 量出來的，LazyVerticalGrid 三欄＋4dp 左右 padding＋
        // 4dp 欄距），格子中心（約 50dp）不落在 [約 60dp, 約 100dp] 內，所以整格點擊仍然
        // 命中內層 Box 的 combinedClickable，同時保住 40dp 的無障礙最小觸控區。
        // （若換成 58dp 寬，範圍會是 [約 41dp, 約 100dp]，反而蓋住中心 ——
        // 這正是原本沒設 .size() 時會撞到的情況。）
        val playShape = RoundedCornerShape(AppTheme.radii.sm)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .focusRing(playShape)
                .semantics { contentDescription = "跳到這一段" }
                .clickable(role = Role.Button) { onHintDismiss(); onPlay() },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                Modifier
                    .padding(top = AppTheme.spacing.s1, end = AppTheme.spacing.s1)
                    .size(30.dp)
                    .clip(playShape)
                    .background(AppTheme.colors.scrim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VsIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/**
 * 相簿手動格的格寬低於這個值時，右下角時間標籤不加「截圖 」前綴：
 * 左下角微調鈕（約 54dp）＋「截圖 MM:SS」標籤（約 60dp）＋邊距放不下。
 */
private val NUDGE_COMPACT_WIDTH = 122.dp

/** ±1 秒微調的小鈕：scrim 底白字，實際尺寸 24×22dp（觸控區由 Compose 擴到 48dp）。名稱由呼叫端給（測試與無障礙靠它定位）。 */
@Composable
private fun NudgeButton(glyph: String, label: String, shape: RoundedCornerShape, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 24.dp, height = 22.dp)
            .clip(shape)
            .background(AppTheme.colors.scrim)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/**
 * 圖是**要畫的時候才去拿**（規格第二節第 6 點：148 格全部留在記憶體要 34 MB）。
 *
 * [bitmapFor] 也是 key，**不能只用 frameIndex**：`LazyVerticalGrid` 的 item 同樣以 frameIndex 當 key，
 * 所以換一支影片、`Step2Store` 被換掉之後，item 的槽位會存活下來；producer 只看 frameIndex
 * 的話不會重跑，那些格子會繼續畫**前一支影片**的 bitmap，直到使用者把它們捲出畫面再捲回來。
 *
 * 因此呼叫端**必須把 [bitmapFor] `remember` 起來、並以 `Step2Store` 當 key**：
 * 每次重組都給一個新的 lambda 的話，這裡會每次重組都重新解一次圖。
 */
@Composable
private fun FrameImage(
    bitmapFor: suspend (Int) -> ImageBitmap?,
    frameIndex: Int,
    modifier: Modifier,
) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, bitmapFor, frameIndex) {
        value = bitmapFor(frameIndex)
    }
    bitmap?.let {
        Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}


/**
 * 左上 20dp 主色圓形打勾徽章，外圈 2dp 白邊（原型 .wz-cell.sel::before）。
 * 第二步與第三步的「已選」共用同一個外觀；語意名稱「已選」讓輔助技術不只靠顏色辨識。
 * 第三步整格的名稱已經含「已勾選」，徽章傳 null 當純裝飾，避免唸兩次。
 */
@Composable
internal fun SelectedBadge(modifier: Modifier = Modifier, semanticLabel: String? = "已選") {
    Box(
        modifier
            .padding(2.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .padding(2.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.accent)
            .then(if (semanticLabel != null) Modifier.semantics { contentDescription = semanticLabel } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(VsIcons.Check, contentDescription = null, tint = AppTheme.colors.accentInk, modifier = Modifier.size(12.dp))
    }
}
