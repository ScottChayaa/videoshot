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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
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
import com.xenyaa.videoshot.ui.thumb.ThumbGridGap
import com.xenyaa.videoshot.ui.common.VsSelectedBadge
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
@OptIn(ExperimentalFoundationApi::class)
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
    onDismissHint: () -> Unit,
    onNext: () -> Unit,
    /** 上一次【截圖】的失敗原因；null 代表沒有要說的。 */
    captureError: CaptureError? = null,
    onPickFromGallery: () -> Unit = {},
    onDismissCaptureError: () -> Unit = {},
    /** 微調某一格的秒數。**只有相簿來的格子會呼叫它**（規格第五節）。 */
    onNudgeManual: (Int, Double) -> Unit = { _, _ -> },
    /** 捲動狀態由外面持有：全螢幕時整個畫面離開組合，狀態要留在外面才不會跳回頂端。 */
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {

        StatusBar(state, onShowAll)

        captureError?.let {
            CaptureErrorBar(it, onPickFromGallery, onDismissCaptureError)
        }

        LazyVerticalGrid(
            state = gridState,
            // 跟照片頁的縮圖牆一樣：每列 4 張正方形、左右貼齊螢幕、格線 ThumbGridGap
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThumbGridGap),
            verticalArrangement = Arrangement.spacedBy(ThumbGridGap),
        ) {
            if (!state.hintSeen) {
                item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                    VsHintCard(
                        "點一下收藏・長按看看那一段",
                        Modifier.semantics { contentDescription = "一次性提示" },
                        onDismiss = onDismissHint,
                    )
                }
            }
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

        // 底部**只有一列**：【全選】【截圖】【下一步】，底下不再疊導覽列（手冊 §四第二步）；
        // 張數在頂部狀態列、已選數在按鈕上，不另外寫（2026-10-17 拿掉「N 張候選 · 已選 k」）。
        // 全選與截圖原本是縮圖牆第一列的小膠囊，捲下去就得捲回頂端才按得到（2026-10-17 移到這裡）；
        // 用次要按鈕、不帶圖示：跟【下一步】同高，主色只留給【下一步】，也省下寬度給「下一步（123 張）」
        VsActionDock {
            VsButton("全選", onSelectAll, variant = ButtonVariant.Secondary)
            VsButton("截圖", onTakeShot, variant = ButtonVariant.Secondary)
            VsButton(
                "下一步（${state.selectedCount} 張）",
                onNext,
                modifier = Modifier.weight(1f),
                enabled = state.selectedCount > 0,
            )
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
        // 一個實體像素（最細）；Dp.Hairline 會讓分隔線的版面高度是 0，改成明確換算
        HorizontalDivider(thickness = (1f / LocalDensity.current.density).dp, color = AppTheme.colors.accentLine)
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
 * 一格（正方形、直角，同照片頁）。**整格都是點擊區**，點＝勾選（只打勾，不畫框）、長按 0.5 秒＝跳播。
 * 主色外框表示播放器目前播到這一格（跟著播放進度走），不是選取。
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
    val cellShape = RectangleShape
    val scrimPill = RoundedCornerShape(AppTheme.radii.sm)

    // 外層 Box **不掛點擊語意**，只負責背景／排版。
    // 微調鈕不能是「整格點擊區」那個語意節點的子孫 —— combinedClickable 會把子孫
    // 合併進自己（無障礙用途），子孫若也是可點擊節點，合併時它的 OnClick 會蓋掉
    // 整格自己的 OnClick。讓微調鈕當外層 Box 的手足，兩個語意節點才不會互相吃掉。
    BoxWithConstraints(
        Modifier
            .aspectRatio(1f)
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
                    // 長按是跳播的唯一手勢；有了這個標籤，TalkBack 的動作選單也找得到
                    onLongClickLabel = "跳到這一段",
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

            // 主色外框＝播放器目前播到這一格（畫在格子內緣、疊在圖上）；選取只靠左上角打勾
            if (playing) {
                Box(Modifier.fillMaxSize().border(3.dp, accent, cellShape))
            }

            if (selected) {
                VsSelectedBadge(Modifier.align(Alignment.TopStart))
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
                // 否則輔助技術會唸兩次）。放右下角 —— 左上角是徽章。
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
            // 是外層 Box 的手足，不在整格點擊的合併子樹裡；而且**實際尺寸（24×22dp）
            // 不蓋到格子中心**：Compose 會把小於 48dp 的觸控目標往外擴，但「實際範圍內的命中」
            // 一律贏過「擴張範圍內的命中」，所以只要實際範圍避開中心，點中心就是整格的勾選。
            // 格子是正方形，320dp 寬時約 80dp 高，鈕頂在 80−2−22＝56dp，中心在 40dp，留餘裕。
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = AppTheme.spacing.s1, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NudgeButton("−", "往前 1 秒", scrimPill) { onNudge(-1.0) }
                NudgeButton("＋", "往後 1 秒", scrimPill) { onNudge(1.0) }
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


