package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
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
 * 「共 M 張」則是 SQL 的 COUNT，所以 M 可能比清單長 —— 滑到尾端就去載下一頁。
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
    total: Int,
    startIndex: Int,
    loader: ThumbLoader,
    hintSeen: Boolean,
    onHintSeen: () -> Unit,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
    onIndexChange: (Int) -> Unit,
    actions: LightboxActions,
    modifier: Modifier = Modifier,
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
            if (page >= items.size - 5 && items.size < total) onLoadMore()
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

    Box(modifier.fillMaxSize().background(AppTheme.colors.lightboxBg)) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(AppTheme.spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(AppTheme.spacing.tap)) {
                    Icon(VsIcons.Close, contentDescription = "關閉", tint = AppTheme.colors.accentInk)
                }
                Text(
                    "第 ${pager.currentPage + 1} / 共 $total 張",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.accentInk,
                    modifier = Modifier.weight(1f).padding(horizontal = AppTheme.spacing.s2),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(AppTheme.spacing.tap)) {
                        Icon(VsIcons.More, contentDescription = "更多", tint = AppTheme.colors.accentInk)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("編輯圖資") },
                            leadingIcon = { Icon(VsIcons.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; actions.onEdit(shot) },
                        )
                        DropdownMenuItem(
                            // 破壞性動作：唯一用 danger 色的地方（手冊 §零第二條）
                            text = { Text("刪除這張收藏", color = AppTheme.colors.danger) },
                            leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                            onClick = { menuOpen = false; confirmingDelete = true },
                        )
                    }
                }
            }

            // 大圖跟一次性提示放同一個 Box：提示用 BottomCenter 貼著**這個 Box 的下緣**，
            // 也就是秒數列／動作列開始的地方，不是螢幕的物理下緣 —— 系統導覽列多高都不會蓋到它。
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
                            // 下面那顆固定的秒數列已經是這張圖的權威顯示 —— 拿不到圖時
                            // 預留圖不用再疊一次同樣的秒數（會重複，畫面上真的疊字）
                            showTimeOnPlaceholder = false,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                    }
                }

                if (!hintDone) {
                    Text(
                        "左右滑動看上一張／下一張",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.accentInk,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AppTheme.spacing.s4)
                            .background(AppTheme.colors.overlay)
                            .padding(AppTheme.spacing.s3)
                            // 點它也可以關掉 —— 擋到畫面的東西一定要有辦法立刻收掉
                            .clickable { hintDone = true; onHintSeen() },
                    )
                }
            }

            // 秒數列跟動作列一起讓開導覽列（手冊 §三第四條的動作分層、規格第六節的秒數顯示）：
            // 這個 Column 不在畫面最下面就不會多吃一段有效內容高度，
            // navigationBarsPadding 的上緣本來就是 0，只會把下緣往上推
            Column(Modifier.navigationBarsPadding()) {
                Text(
                    formatClock(shot.atSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textFaint,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.s4),
                )

                // 動作分層（手冊 §三第四條）：主要動作只有一顆，其餘瀏覽動作是圖示鈕，
                // 破壞性動作（刪除）與次要動作（編輯）收在上面的「⋯」，不跟這裡並排
                Row(
                    Modifier.fillMaxWidth().padding(AppTheme.spacing.s4),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { actions.onPlay(shot) }, modifier = Modifier.weight(1f)) {
                        Icon(VsIcons.Play, contentDescription = null)
                        Text("播放這一段", modifier = Modifier.padding(start = AppTheme.spacing.s2))
                    }
                    IconButton(onClick = { actions.onAddToFolder(shot) }, modifier = Modifier.size(AppTheme.spacing.tap)) {
                        Icon(VsIcons.FolderPlus, contentDescription = "加入分類", tint = AppTheme.colors.accentInk)
                    }
                    IconButton(onClick = { actions.onShare(shot) }, modifier = Modifier.size(AppTheme.spacing.tap)) {
                        Icon(VsIcons.Share, contentDescription = "分享", tint = AppTheme.colors.accentInk)
                    }
                }
            }
        }

        if (confirmingDelete) {
            AlertDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text("刪除這張收藏？") },
                text = { Text("YouTube 原片不受影響") },
                confirmButton = {
                    TextButton(onClick = { confirmingDelete = false; actions.onDelete(shot) }) {
                        Text("刪除", color = AppTheme.colors.danger)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } },
            )
        }
    }
}
