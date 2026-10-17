package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.PillStyle
import com.xenyaa.videoshot.ui.common.TextFieldSize
import com.xenyaa.videoshot.ui.common.VsActionDock
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsFieldLabel
import com.xenyaa.videoshot.ui.common.VsRemovableTag
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.thumb.ThumbGridGap
import com.xenyaa.videoshot.data.library.dao.TagNameKind
import androidx.compose.ui.graphics.RectangleShape
import com.xenyaa.videoshot.ui.common.VsDateField
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.common.VsToolbarPill
import com.xenyaa.videoshot.ui.common.VsSelectedBadge
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.time.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 第三步：定義圖資。
 *
 * 縮圖牆跟第二步（與照片頁）一樣：**每列 4 張正方形、左右貼齊螢幕、格線 [ThumbGridGap]**（2026-10-17）。
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
    onInvert: () -> Unit,
    onSelectUnapplied: () -> Unit,
    onEditEventDate: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onEditDescription: (String) -> Unit,
    onEditTags: (List<String>) -> Unit,
    onApply: () -> Unit,
    onFinish: () -> Unit,
    placeSuggestions: List<String> = emptyList(),
    tagSuggestions: List<TagNameKind> = emptyList(),
    /**
     * 【完成】要每一張都套用過才按得下去（[Step3State.allFilled]）。取圖精靈是 true；
     * 批次編輯是 false —— 它只寫回套用過的那幾張，本來就允許只改其中幾張。
     */
    requireAllFilled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 外層高度 H：edge-to-edge 下視窗不會因鍵盤縮小，鍵盤佔掉的高度由 dock 的 imePadding 吃掉，
    // 所以 H 含鍵盤；抽屜要用「H 扣掉鍵盤」來算（見 [Step3Dock] 的 maxDrawer）
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columnHeight = maxHeight
        Column(Modifier.fillMaxSize()) {

            // sheet 已經在本機，這一行幾乎一閃而過（規格第五節）
            state.progressText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 快速選取列（原型 .wz-tools.pick）。FlowRow：**換行不截斷**（手冊 §四）
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                VsToolbarPill("全選", onSelectAll, style = PillStyle.Quiet)
                VsToolbarPill("反選", onInvert, style = PillStyle.Quiet)
                // 收尾時用的：一鍵勾選所有還沒套用過的（規格第五節快捷列）；全都套用過就沒有東西可勾
                val unapplied = state.unappliedCells.size
                VsToolbarPill("未填 $unapplied", onSelectUnapplied, enabled = unapplied > 0, style = PillStyle.Quiet)
            }

            LazyVerticalGrid(
                // 跟第二步一樣：每列 4 張正方形、左右貼齊螢幕、格線 ThumbGridGap
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ThumbGridGap),
                verticalArrangement = Arrangement.spacedBy(ThumbGridGap),
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
                requireAllFilled = requireAllFilled,
                availableHeight = columnHeight,
            )
        }
    }
}

/**
 * 一格（正方形、直角，同第二步）。**「已選」與「已套用」分開表達**：
 * 已選＝左上打勾圓徽章（只打勾、不畫框，同第二步），已套用＝右上綠色打勾方塊。
 * 兩件事會同時成立，用同一個記號說不清楚。紅色只留給破壞性操作（手冊 §零），這裡不用。
 * （2026-10-17 前還沒套用的整格壓暗、已選另畫主色內框，都拿掉了。）
 *
 * 顏色不是唯一的訊號 —— 兩者都寫進整格的 `contentDescription`，輔助技術也讀得到（手冊 §零）；
 * 徽章與打勾方塊本身是純裝飾，不另設名稱，免得 TalkBack 把同一件事唸兩次。
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
    val cellShape = RectangleShape
    val pill = RoundedCornerShape(AppTheme.radii.sm)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(cellShape)
            .background(AppTheme.colors.surface2)
            .focusRing(cellShape)
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
        // 時間標籤同時是測試點得到的目標，也是使用者辨認「這是哪一張」的依據（樣式同第二步）
        Text(
            formatClock(cell.atSec),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppTheme.spacing.s1)
                .clip(pill)
                .background(AppTheme.colors.scrim)
                .padding(horizontal = AppTheme.spacing.s1),
        )
        if (selected) {
            VsSelectedBadge(Modifier.align(Alignment.TopStart), semanticLabel = null)
        }
        if (applied) {
            // 右上 22dp ok 色圓角方塊、白色打勾、外圈 2dp 白邊（原型 .done-mark）
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(pill)
                    .background(AppTheme.colors.ok)
                    .border(2.dp, Color.White, pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VsIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * 抽屜以外、鍵盤開著時仍要留住的高度。[Step3DetailsScreen] 的外層高度已經扣掉頂欄與步驟條，
 * 所以只算本體裡的：快速選取列（約 60）＋一排縮圖（約 56）＋標題列（約 52，含【套用】）
 * ＋ dock 的狀態文字與按鈕列（約 84）（粗估，寧可略大）。
 */
private val DOCK_RESERVED = 260.dp

/**
 * 底部的**單一 dock**：抽屜、提示行、主按鈕**收成同一塊**（手冊 §四第三步第一條）。
 *
 * 之所以不是三層：抽屜／按鈕列／導覽列各佔一條，縮圖區會被壓到剩四成 —— 而縮圖區
 * 正是使用者用來判斷「現在勾的是哪幾張」的地方。精靈本來就沒有導覽列（規格第五節），
 * 抽屜與按鈕再合起來，就只剩一塊。
 *
 * 沒有勾選時**抽屜收合、主按鈕留著** —— 收掉按鈕會讓使用者在取消全部勾選之後無路可走。
 *
 * **【套用】在抽屜標題列右邊，底部主按鈕永遠是【完成】**（2026-10-17，原本主按鈕在【完成】與【套用到 N 張】之間切換）。
 * 還有沒套用的修改時按【完成】，先套用再結束 —— 畫面上看到的值就是存下去的值。
 *
 * **鍵盤**：[VsActionDock] 自己吃 `imePadding()`，dock 整塊被鍵盤頂上去，縮圖網格（`weight(1f)`）
 * 讓出高度；抽屜的最大高度改用「扣掉鍵盤後的可用高度」的 46%，鍵盤開著時抽屜才不會比剩下的空間還高；
 * 抽屜裡的欄位聚焦時再自己捲到可見（[revealOnFocus]）。
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
    tagSuggestions: List<TagNameKind>,
    requireAllFilled: Boolean,
    availableHeight: Dp,
) {
    val topShape = RoundedCornerShape(topStart = AppTheme.radii.md, topEnd = AppTheme.radii.md)
    val borderColor = AppTheme.colors.border
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, topShape)
            .background(AppTheme.colors.surface, topShape)
            // 原型 .wz-dock 只有上緣一條線；四邊都描會在左右下緣多出細框。
            // 先依上圓角裁切，這條線在圓角處就會順著弧線收掉，不會伸出背景之外
            .clip(topShape)
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(borderColor, Offset(0f, w / 2), Offset(size.width, w / 2), strokeWidth = w)
            },
    ) {
        if (state.selected.isNotEmpty()) {
            // 標題列不跟著捲動：捲到下面的欄位時，還是看得到現在在編輯哪幾張
            val one = state.selected.singleOrNull()
            val clock = one?.let { c -> state.cells.firstOrNull { it.cell == c } }?.let { formatClock(it.atSec) }
            // 【套用】用小鈕（視覺 30dp，觸控區仍 44dp）：大按鈕把整列撐到快 70dp，太佔位（2026-10-17 scott）；
            // 觸控區上下各多出約 7dp 的透明空間，標題列與下面的欄位因此不會貼在一起
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "照片詳細資訊",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.text,
                )
                // 只勾一張＝單張編輯，時間讓使用者看得出現在編的是哪一張（規格第五節的勾選數表）
                if (clock != null) {
                    Text(clock, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textDim)
                }
                Spacer(Modifier.weight(1f))
                // 有改動時換主色底，提醒還沒套用；沒改動時是中性底（照現值確認，見 Step3Store.applyPatch）。
                // 不是切換型，不帶選取語意（isToggle 預設 false）
                VsToolbarPill("套用", onApply, selected = !state.patch.isEmpty, style = PillStyle.Quiet)
            }

            // 欄位本身可以滾動，**提示行與主按鈕不行** —— 欄位一多，矮螢幕上剩下的高度就不夠
            // 同時擠下所有東西；輸入框可以捲動找，但「按下去會發生什麼事」永遠要摸得到，
            // 不能因為欄位太多就被擠出畫面外（規格第五節、手冊 §四第三步）。
            // 最大高度：扣掉鍵盤後可用高度的 46%（原型 .meta-drawer 的 max-height: 46vh）。
            // 另外再封頂在「可用高度 − 工具列／標題／提示行／按鈕列／最少一排縮圖」之外：
            // dock 自己帶著 imePadding（高度含鍵盤），鍵盤開著時只用 46% 的話，抽屜加上這些
            // 固定列會比畫面剩下的空間還高，縮圖網格被擠到 0 高、按鈕列被截（實機 2107113SG 看到過）。
            // `weight(1f, fill = false)`：提示行與主按鈕先量，抽屜只拿剩下的空間 ——
            // 橫向或矮螢幕加鍵盤時，抽屜縮短（自己捲動），按鈕不會被擠成 0 高。
            val imeDp = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
            val visible = availableHeight - imeDp
            val maxDrawer = minOf(visible * 0.46f, visible - DOCK_RESERVED).coerceAtLeast(120.dp)
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = maxDrawer)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 月曆選擇器（2026-10-17 由打字改成選擇器）；各張不同時顯示〈多個值〉，不選就不會動到任何一張
                val dateField = state.eventDateField
                VsDateField(
                    label = "時間",
                    value = state.patch.eventDate ?: (dateField as? Common.One<String>)?.value.orEmpty(),
                    onPick = onEditEventDate,
                    mixed = state.patch.eventDate == null && dateField is Common.Mixed,
                    labelAccent = state.patch.eventDate != null,
                    size = TextFieldSize.Dense,
                )

                DrawerTextField(
                    label = "地點",
                    field = state.placeField,
                    edited = state.patch.place,
                    onEdit = onEditPlace,
                )
                SuggestionRow(placeSuggestions, kindOf = { ChipKind.PLACE }, onPick = onEditPlace)

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
                    onEdit = onEditDescription,
                )
                // 捲到底時，最後一個欄位與下面的提示行之間留一點呼吸
                Spacer(Modifier.height(4.dp))
            }
        }

        VsActionDock(
            status = {
                // 套用後的短暫回饋優先；使用者一動欄位或勾選就會消失，回到「N 張 · M 已完成」
                val notice = state.appliedNotice
                if (notice != null) {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = AppTheme.colors.ok,
                    )
                } else {
                    Text(state.headerText)
                }
            },
        ) {
            VsButton(
                "完成",
                onClick = {
                    if (!state.patch.isEmpty) onApply()
                    onFinish()
                },
                enabled = !requireAllFilled || state.allFilled,
            )
        }
    }
}

/**
 * 抽屜欄位聚焦時，等鍵盤動畫跑完再把自己捲進可見範圍。
 *
 * 用 `hasFocus` 而不是 `isFocused`：這個 Modifier 掛在 [VsTextField] 的外層 `Column`，
 * 真正拿到焦點的是裡面的輸入框，外層只會是「子孫有焦點」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.revealOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { if (it.hasFocus) scope.launch { delay(300); requester.bringIntoView() } }
}

/**
 * 抽屜裡的一個文字欄位。
 *
 * 顯示的值有三種來源，優先序就是這個順序：
 * 1. `edited` —— 使用者在這一輪打過的字（含空字串＝要清空）
 * 2. `Common.One` —— 勾選中的張數值一致，直接顯示它
 * 3. `Common.Mixed` —— 顯示〈多個值〉佔位字樣，**不動它就不會變**
 *
 * 動過的欄位（`edited != null`）標題變主色並亮點（原型 `.field.will-write`）。
 */
@Composable
private fun DrawerTextField(
    label: String,
    field: Common<*>,
    edited: String?,
    onEdit: (String) -> Unit,
) {
    val shown = edited ?: (field as? Common.One<*>)?.value?.toString() ?: ""
    val mixed = edited == null && field is Common.Mixed
    VsTextField(
        value = shown,
        onValueChange = onEdit,
        modifier = Modifier.revealOnFocus(),
        label = label,
        labelAccent = edited != null,
        // 〈多個值〉：不動它就不會變（規格第五節套用語意表）。斜體 textDim 是主要訊號，
        // 不只靠顏色，色覺障礙的使用者才分得出「大家都空著」與「各不相同」
        placeholder = if (mixed) "〈多個值〉" else null,
        mixedPlaceholder = mixed,
        size = TextFieldSize.Dense,
        // semanticLabel 預設就是 label（「時間」「地點」「描述」）
    )
}

/** 用過的地點／標籤：一排 chip，點了就填入／加入。沒有建議時整段不顯示（2026-10-17 拿掉小標）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(values: List<String>, kindOf: (String) -> ChipKind, onPick: (String) -> Unit) {
    if (values.isEmpty()) return
    // 列距 0：小膠囊外面已有 44dp 高的透明觸控外框
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> VsTagChip(value, kindOf(value), onClick = { onPick(value) }) }
    }
}

/**
 * 標籤欄位。chip 形式（規格第五節欄位表），**整組覆蓋**：
 * 每一次增刪都把完整的一組往上送，不送差異 —— 差異在「整組覆蓋」的語意下沒有意義。
 *
 * 由上到下：標題 → 已加的標籤 → 輸入框＋【加入】→ 用過的標籤（同照片編輯抽屜，2026-10-17）。
 * 小膠囊依標籤的種類畫圖示；資料庫裡還沒有的新標籤當成「其他」。
 *
 * **鍵盤的 Enter（完成）與【加入】做同一件事**：原本只有「＋」鈕能加，鍵盤送出沒有反應。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagField(
    field: Common<List<String>>,
    edited: List<String>?,
    suggestions: List<TagNameKind>,
    onEdit: (List<String>) -> Unit,
) {
    val shown = edited ?: (field as? Common.One<List<String>>)?.value ?: emptyList()
    val mixed = edited == null && field is Common.Mixed
    val kinds = remember(suggestions) { suggestions.associate { it.name to it.kind } }
    fun kindOf(tag: String) = ChipKind.ofTagKind(kinds[tag] ?: "other")
    var typing by remember { mutableStateOf("") }
    // 空白不加、加完清空
    fun add() {
        if (typing.isNotBlank()) { onEdit(shown + typing); typing = "" }
    }
    Column {
        VsFieldLabel("標籤", labelAccent = edited != null)
        if (shown.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { tag -> VsRemovableTag(tag, kindOf(tag), onRemove = { onEdit(shown - tag) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            VsTextField(
                value = typing,
                onValueChange = { typing = it },
                modifier = Modifier.weight(1f).revealOnFocus(),
                placeholder = if (mixed) "〈多個值〉" else "新增標籤",
                mixedPlaceholder = mixed,
                size = TextFieldSize.Dense,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                semanticLabel = "新增標籤",
            )
            VsButton("加入", onClick = ::add, variant = ButtonVariant.Secondary)
        }
        Spacer(Modifier.height(4.dp))
        val options = suggestions.map { it.name }.filterNot { it in shown }
        SuggestionRow(options, kindOf = ::kindOf, onPick = { onEdit(shown + it) })
    }
}
