package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
import com.xenyaa.videoshot.data.library.dao.TagNameKind
import com.xenyaa.videoshot.ui.common.ChipKind
import com.xenyaa.videoshot.ui.common.ChipSize
import com.xenyaa.videoshot.ui.common.VsTagChip
import com.xenyaa.videoshot.ui.common.chineseDate
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.drop
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.icons.VsIcons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.common.ButtonVariant
import com.xenyaa.videoshot.ui.common.VsButton
import com.xenyaa.videoshot.ui.theme.OnDarkColors
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 分享的內容(規格第六節動作表):`https://youtu.be/{videoId}?t={at_sec}`。
 * 秒數**取整數** —— YouTube 的 `t` 參數只吃整數秒,小數點會被當成無效值整個忽略。
 */
fun shareTextOf(shot: ShotRow): String = "https://youtu.be/${shot.videoId}?t=${shot.atSec.toInt()}"

/**
 * Lightbox 的動作。`onPlay`／`onAddToFolder` 依賴階段 9／階段 8，
 * 在那兩階段完工前由呼叫端接成「說明還沒做」的 snackbar（見 `AppRoot`）。
 */
data class LightboxActions(
    val onPlay: (ShotRow) -> Unit = {},
    val onAddToFolder: (ShotRow) -> Unit = {},
    val onShare: (ShotRow) -> Unit = {},
    val onEdit: (ShotRow) -> Unit = {},
    val onDelete: (ShotRow) -> Unit = {},
)

/**
 * 全屏檢視器。
 *
 * **背景不透明**（手冊 §三第一條）——「半透明 0.97」在真機上仍然透得出底層的月份標題，
 * 那會讓人以為自己還在清單上。
 *
 * 左右滑動的範圍是**進來時的清單**（規格第六節）：首頁目前已載入的那一份。
 * 看圖時不顯示第幾張（規格第六節，2026-10-07 決定）；滑到尾端就去載下一頁。
 *
 * 大圖是 320×180 放大置中，全屏會偏軟，這是儲存尺寸的天生限制（規格附錄 A-6）。
 *
 * **系統列的安全區**：`MainActivity` 呼叫了 `enableEdgeToEdge()`，視窗本來就畫到系統列下面，
 * 而 Lightbox 又刻意畫在 `AppShell` 外面（連底部導覽都要蓋掉），所以沒有 `Scaffold` 替它讓開
 * 系統列——上排與下排這兩條**互動用的**列各自用 `statusBarsPadding()`／`navigationBarsPadding()`
 * 讓開，背景（`lightboxBg`）跟中間的大圖不用讓，全螢幕不透明才顧得到手冊 §三第一條。
 */
@Composable
fun LightboxScreen(
    items: List<ShotRow>,
    /** 已載入的清單後面還有沒有下一頁；剩不到 5 張且還有下一頁就先去要。 */
    hasMore: Boolean,
    startIndex: Int,
    loader: ThumbLoader,
    hintSeen: Boolean,
    onHintSeen: () -> Unit,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
    onIndexChange: (Int) -> Unit,
    actions: LightboxActions,
    modifier: Modifier = Modifier,
    /** 一張圖的標籤（名稱與種類）。讀不到就回空清單，標籤列留白，不擋看圖。 */
    tagsOf: suspend (ShotRow) -> List<TagNameKind> = { emptyList() },
    /** 就地編輯存檔後遞增：標籤改了但 [ShotRow] 不含標籤、比不出差異，靠它重讀標籤列。 */
    tagsRefreshKey: Int = 0,
) {
    if (items.isEmpty()) {
        // 刪到最後一張時會走到這裡（案例 17）
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )

    LaunchedEffect(pager, items.size) {
        snapshotFlow { pager.currentPage }.collect { page ->
            onIndexChange(page)
            // 剩不到 5 張就先去要下一頁，滑動才不會停在「已載入的最後一張」
            if (page >= items.size - 5 && hasMore) onLoadMore()
        }
    }

    // 提示在**使用者做過一次滑動**之後才算看過。一開啟就記成看過的話，
    // 它會在使用者讀到之前就消失，等於這個一次性提示從來沒出現過。
    // drop(1)：snapshotFlow 一開始就會送出目前這一頁，那不是一次滑動
    var hintDone by remember(hintSeen) { mutableStateOf(hintSeen) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.drop(1).collect {
            if (!hintDone) {
                hintDone = true
                onHintSeen()
            }
        }
    }

    // 頂列的「⋯」與底部動作列共用同一張圖，所以在 Column 最上面算一次（Task 10 步驟 3 的備註）
    val shot = items[pager.currentPage.coerceAtMost(items.lastIndex)]
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val iconShape = RoundedCornerShape(AppTheme.radii.sm)
    val tags by produceState(emptyList<TagNameKind>(), shot.id, tagsRefreshKey) {
        value = emptyList()
        // 輔助資訊：讀不到就沒有標籤列，不擋看圖（跟詳情頁讀標籤同一個取捨）
        value = try {
            tagsOf(shot)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            emptyList()
        }
    }

    Box(modifier.fillMaxSize().background(AppTheme.colors.lightboxBg)) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(AppTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 圖示色用 OnDarkColors：Lightbox 底色不隨主題變，前景也不能隨主題變
                IconButton(onClick = onClose, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(iconShape)) {
                    Icon(VsIcons.Back, contentDescription = "返回", tint = OnDarkColors.primary)
                }
                Text(
                    chineseDate(shot.eventDate),
                    style = MaterialTheme.typography.titleSmall,
                    color = OnDarkColors.primary,
                    maxLines = 1,
                )
                // 地點小膠囊佔掉日期與「⋯」之間剩下的寬度；名稱太長就在這一格裡左右滑，不換行
                Row(
                    Modifier
                        .weight(1f)
                        .padding(start = AppTheme.spacing.s2)
                        .horizontalScroll(remember(shot.id) { ScrollState(0) }),
                ) {
                    shot.place?.let { VsTagChip(it, ChipKind.PLACE, size = ChipSize.Mini) }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(iconShape)) {
                        Icon(VsIcons.More, contentDescription = "更多", tint = OnDarkColors.primary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("詳細資料") },
                            leadingIcon = { Icon(VsIcons.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; actions.onEdit(shot) },
                        )
                        DropdownMenuItem(
                            // 破壞性動作：唯一用 danger 色的地方（手冊 §零第二條）
                            text = { Text("刪除", color = AppTheme.colors.danger) },
                            leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                            onClick = { menuOpen = false; confirmingDelete = true },
                        )
                    }
                }
            }

            // 標籤列：只有一列，放不下就左右滑，不換行。沒有標籤也保留這一列的高度——
            // 左右滑到有標籤／沒標籤的圖時，大圖才不會跟著上下跳
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TAG_ROW_MIN_HEIGHT)
                    // 換一張圖就從頭開始，不沿用上一張滑到的位置
                    .horizontalScroll(remember(shot.id) { ScrollState(0) })
                    .padding(horizontal = AppTheme.spacing.s4),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tags.forEach { VsTagChip(it.name, ChipKind.ofTagKind(it.kind), size = ChipSize.Mini) }
            }

            // 大圖跟一次性提示放同一個 Box：提示用 BottomCenter 貼著**這個 Box 的下緣**，
            // 也就是描述／動作列開始的地方，不是螢幕的物理下緣 —— 系統導覽列多高都不會蓋到它。
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "收藏的大圖" },
                ) { page ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ThumbImage(
                            shot = items[page],
                            loader = loader,
                            contentDescription = items[page].description,
                            // Lightbox 不顯示影片秒數（頂列只有日期，2026-10-12 決定），預留圖也不疊
                            showTimeOnPlaceholder = false,
                            // 照圖本身的寬高比完整顯示：直式影片（Shorts）的圖是直的，裁成 16:9 會只剩中間一條
                            fitImageAspect = true,
                        )
                    }
                }

                if (!hintDone) {
                    Text(
                        "左右滑動看上一張／下一張",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkColors.primary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AppTheme.spacing.s3)
                            // 膠囊(原型 .lb-swipe)：半透明黑底、全圓角、上下 4 左右 12
                            .clip(CircleShape)
                            .background(OnDarkColors.hint)
                            // 點它也可以關掉 —— 擋到畫面的東西一定要有辦法立刻收掉
                            .clickable { hintDone = true; onHintSeen() }
                            .padding(horizontal = AppTheme.spacing.s3, vertical = AppTheme.spacing.s1),
                    )
                }
            }

            // 描述跟動作列一起讓開導覽列（手冊 §三第四條的動作分層）：
            // 這個 Column 不在畫面最下面就不會多吃一段有效內容高度，
            // navigationBarsPadding 的上緣本來就是 0，只會把下緣往上推
            Column(Modifier.navigationBarsPadding()) {
                // 描述固定佔兩行（沒有描述也留著），橫的直的、有沒有描述，底部動作列都在同一個位置
                Text(
                    shot.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkColors.primary,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppTheme.spacing.s4, end = AppTheme.spacing.s4, top = AppTheme.spacing.s3),
                )

                // 動作分層（手冊 §三第四條）：主要動作只有一顆，其餘瀏覽動作是圖示鈕，
                // 破壞性動作（刪除）與次要動作（編輯）收在上面的「⋯」，不跟這裡並排
                Row(
                    Modifier.fillMaxWidth().padding(AppTheme.spacing.s4),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VsButton(
                        text = "播放這一段",
                        onClick = { actions.onPlay(shot) },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Primary,
                        icon = VsIcons.Play,
                    )
                    DarkIconButton(VsIcons.FolderPlus, "加入相簿") { actions.onAddToFolder(shot) }
                    DarkIconButton(VsIcons.Share, "分享") { actions.onShare(shot) }
                }
            }
        }

        if (confirmingDelete) {
            AlertDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text("刪除這張照片？") },
                text = { Text("YouTube 原片不受影響") },
                confirmButton = {
                    VsButton(
                        text = "刪除",
                        onClick = { confirmingDelete = false; actions.onDelete(shot) },
                        variant = ButtonVariant.Danger,
                    )
                },
                dismissButton = {
                    VsButton(text = "取消", onClick = { confirmingDelete = false }, variant = ButtonVariant.Quiet)
                },
            )
        }
    }
}

/** 動作列的次要圖示鈕(原型 `.btn.on-dark`)：44×44、白 12% 底、圓角 8、20dp 白色圖示。 */
@Composable
private fun DarkIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(AppTheme.spacing.tap)
            .clip(shape)
            .background(OnDarkColors.fill)
            .focusRing(shape),
    ) {
        Icon(icon, contentDescription = label, tint = OnDarkColors.primary, modifier = Modifier.size(20.dp))
    }
}

/** 標籤列的最小高度：迷你小膠囊一列的高度，沒有標籤時也佔住這一列。 */
private val TAG_ROW_MIN_HEIGHT = 32.dp
