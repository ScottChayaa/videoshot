package com.xenyaa.videoshot.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.details.normalizeTags
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.data.library.dao.TagNameKind
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.UNSELECTED_FILL_ALPHA
import com.xenyaa.videoshot.ui.common.VsBottomSheet
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.common.VsChipPager
import com.xenyaa.videoshot.ui.common.VsDateField
import com.xenyaa.videoshot.ui.common.VsSheetHeader
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.VsTextField
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 鍵盤彈出動畫大約多久；聚焦後等它跑完再捲，不然算到的可見範圍還是鍵盤出來前的。 */
private const val KEYBOARD_SETTLE_MS = 300L

/** 新建標籤時預設的種類（2026-10-12 scott 決定：主題）。 */
const val NEW_TAG_DEFAULT_KIND = "topic"

/**
 * 就地編輯**一張**圖的圖資。Lightbox ⋯ 選單的【詳細資料】與詳情頁的【編輯詳細資料】共用
 * （主計畫第 284 行）。
 *
 * 送出的是 [DetailsPatch]：**只帶動過的欄位**，沒動過的是 null。
 * 雖然這裡只有一張圖、大可整份覆蓋，但用同一個型別才能和精靈第三步、
 * 階段 9 的批次編輯走同一條寫入路徑（`LibraryRepo.patchShots`）。
 *
 * 空字串＝清空，與 `:core` 的 `DetailsPatch` 同一個約定。
 *
 * 地點與標籤的候選是小膠囊（[VsChipPager]：每頁最多 4 列、多的左右滑，同首頁篩選抽屜），
 * **打字就即時過濾**（名稱含有輸入的字）。新打的標籤（資料庫裡還沒有的）加入後，
 * 輸入框下面出現一列種類讓使用者選，預設 [NEW_TAG_DEFAULT_KIND]；種類隨 [DetailsPatch.newTagKinds] 送出，
 * 只在新建時寫入——既有標籤的種類在帳號頁的標籤管理改。
 *
 * @param tagSuggestions 全部既有標籤（名稱與種類），已選的小膠囊也從這裡查種類
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ShotEditSheet(
    details: ShotDetails,
    placeSuggestions: List<String>,
    tagSuggestions: List<TagNameKind>,
    onDismiss: () -> Unit,
    onSave: (DetailsPatch) -> Unit,
) {
    var eventDate by remember(details) { mutableStateOf(details.eventDate) }
    var place by remember(details) { mutableStateOf(details.place.orEmpty()) }
    var description by remember(details) { mutableStateOf(details.description.orEmpty()) }
    var tags by remember(details) { mutableStateOf(details.tags) }
    var typing by remember(details) { mutableStateOf("") }
    // 這次新打的標籤 → 種類；最後一個新標籤的名字（輸入框下面那一列種類選擇是給它的）
    var newKinds by remember(details) { mutableStateOf(emptyMap<String, String>()) }
    var kindFor by remember(details) { mutableStateOf<String?>(null) }

    val knownKinds = remember(tagSuggestions) { tagSuggestions.associate { it.name to it.kind } }
    fun kindOf(tag: String) = ChipKind.ofTagKind(knownKinds[tag] ?: newKinds[tag] ?: "other")

    // 「動過沒有」是拿現值跟原值比，不是記使用者按過幾次鍵 ——
    // 改完又改回來的話，那就是沒動過
    val tagsPatch = normalizeTags(tags).takeIf { it != normalizeTags(details.tags) }
    val patch = DetailsPatch(
        eventDate = eventDate.takeIf { it != details.eventDate },
        place = place.takeIf { it != details.place.orEmpty() },
        description = description.takeIf { it != details.description.orEmpty() },
        tags = tagsPatch,
        newTagKinds = if (tagsPatch == null) emptyMap() else newKinds.filterKeys { it in tagsPatch },
    )

    // 鍵盤開著時，輸入框下面的候選與種類選擇會被擋住：聚焦、打字、出現種類選擇時把整塊捲進可見範圍
    val placeReveal = remember { BringIntoViewRequester() }
    val tagReveal = remember { BringIntoViewRequester() }
    var tagFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(kindFor, typing) {
        if (tagFocused || kindFor != null) {
            delay(KEYBOARD_SETTLE_MS / 3)
            tagReveal.bringIntoView()
        }
    }

    fun addTag(raw: String) {
        val name = normalizeTags(listOf(raw)).firstOrNull() ?: return
        tags = normalizeTags(tags + name)
        if (name !in knownKinds && name !in details.tags && name !in newKinds) {
            newKinds = newKinds + (name to NEW_TAG_DEFAULT_KIND)
            kindFor = name
        }
    }

    fun removeTag(name: String) {
        tags = tags - name
        newKinds = newKinds - name
        if (kindFor == name) kindFor = null
    }

    VsBottomSheet(onDismissRequest = onDismiss) {
        VsSheetHeader("詳細資料")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.s4)
                .padding(top = AppTheme.spacing.s3, bottom = AppTheme.spacing.s5),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s4),
            ) {
                // 月曆選擇器（2026-10-17 由打字改成選擇器）：選出來的一定是合法的 YYYY-MM-DD，不用再驗格式
                VsDateField(label = "時間", value = eventDate, onPick = { eventDate = it })

                Column(
                    Modifier
                        .bringIntoViewRequester(placeReveal)
                        .onFocusEvent { if (it.hasFocus) scope.launch { delay(KEYBOARD_SETTLE_MS); placeReveal.bringIntoView() } },
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                ) {
                    VsTextField(value = place, onValueChange = { place = it }, label = "地點", placeholder = "輸入或從下面挑")
                    val query = place.trim()
                    // 欄位裡已經是一個既有地點（例如原本的值、剛點的候選）就列出其他全部，方便換一個；
                    // 打的是部分的字才過濾
                    val places = remember(placeSuggestions, query) {
                        val filtering = query.isNotEmpty() && query !in placeSuggestions
                        placeSuggestions.filter { it != query && (!filtering || it.contains(query, ignoreCase = true)) }
                    }
                    VsChipPager(
                        names = places,
                        kindOf = { ChipKind.PLACE },
                        onClick = { i -> place = places[i] },
                        resetKey = query,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                    FieldLabel("標籤")
                    if (tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                            tags.forEach { tag -> RemovableTag(tag, kindOf(tag)) { removeTag(tag) } }
                        }
                    }
                    // 輸入框＋種類選擇＋候選是一塊，鍵盤開著時一起捲上來
                    Column(
                        Modifier
                            .bringIntoViewRequester(tagReveal)
                            .onFocusEvent {
                                tagFocused = it.hasFocus
                                if (it.hasFocus) scope.launch { delay(KEYBOARD_SETTLE_MS); tagReveal.bringIntoView() }
                            },
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2), verticalAlignment = Alignment.Bottom) {
                            VsTextField(
                                value = typing,
                                onValueChange = { typing = it },
                                modifier = Modifier.weight(1f),
                                placeholder = "新增標籤",
                                semanticLabel = "新增標籤",
                                // 鍵盤的完成鍵＝【加入】（同取圖第三步）
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addTag(typing); typing = "" }),
                            )
                            VsButton("加入", onClick = { addTag(typing); typing = "" }, variant = ButtonVariant.Secondary)
                        }
                        kindFor?.takeIf { it in tags }?.let { name ->
                            NewTagKindRow(name, newKinds[name] ?: NEW_TAG_DEFAULT_KIND) { kind ->
                                newKinds = newKinds + (name to kind)
                            }
                        }
                        val tagQuery = typing.trim()
                        val tagOptions = remember(tagSuggestions, tags, tagQuery) {
                            tagSuggestions.filter {
                                it.name !in tags && (tagQuery.isEmpty() || it.name.contains(tagQuery, ignoreCase = true))
                            }
                        }
                        VsChipPager(
                            names = remember(tagOptions) { tagOptions.map { it.name } },
                            kindOf = { i -> ChipKind.ofTagKind(tagOptions[i].kind) },
                            onClick = { i -> addTag(tagOptions[i].name); typing = "" },
                            resetKey = tagQuery,
                        )
                    }
                }

                VsTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "描述",
                    lines = 2,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2)) {
                VsButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f), variant = ButtonVariant.Quiet)
                VsButton(
                    "儲存",
                    onClick = { onSave(patch) },
                    modifier = Modifier.weight(1f),
                    enabled = !patch.isEmpty,
                )
            }
        }
    }
}

/** 欄位標題，跟 [VsTextField] 的標籤同一個樣式（標籤區沒有單一輸入框可以掛標題）。 */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = AppTheme.colors.textDim,
    )
}

/** 新標籤的種類選擇：「「名字」是：人物 動物 主題 其他」，單選。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTagKindRow(name: String, kind: String, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("「$name」是：", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textDim)
        TagKind.entries.forEach { k ->
            VsTagChip(
                name = k.label,
                kind = ChipKind.ofTagKind(k.id),
                selected = k.id == kind,
                isToggle = true,
                onClick = { onPick(k.id) },
            )
        }
    }
}

/**
 * 已選的標籤：外觀同 [VsTagChip]（中性半透明底、無框、帶種類圖示），尾端多一個關閉圖示，整顆可點＝移除。
 * 名稱「移除標籤 X」讓輔助技術聽得出點下去會發生什麼事。
 */
@Composable
private fun RemovableTag(name: String, kind: ChipKind, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    // 觸控區至少 44dp 高（手冊 §零），看得到的膠囊置中、跟其他小膠囊一樣矮
    Box(
        Modifier
            .defaultMinSize(minHeight = AppTheme.spacing.tap)
            .semantics(mergeDescendants = true) { contentDescription = "移除標籤 $name" }
            .clickable(role = Role.Button, onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .focusRing(shape)
                .clip(shape)
                .background(AppTheme.colors.text.copy(alpha = UNSELECTED_FILL_ALPHA))
                .padding(horizontal = AppTheme.spacing.s2, vertical = AppTheme.spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
        ) {
            Icon(kind.icon, contentDescription = null, tint = kind.color, modifier = Modifier.size(16.dp))
            Text(name, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.text)
            Icon(VsIcons.Close, contentDescription = null, tint = AppTheme.colors.textDim, modifier = Modifier.size(14.dp))
        }
    }
}
