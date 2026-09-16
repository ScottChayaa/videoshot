package com.xenyaa.videoshot.ui.lightbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * Lightbox 的動作。Task 10 才填真的行為 —— 這一步先讓版面與滑動站得住。
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

    Box(modifier.fillMaxSize().background(AppTheme.colors.lightboxBg)) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier.fillMaxWidth().padding(AppTheme.spacing.s2),
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
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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

            Text(
                formatClock(items[pager.currentPage.coerceAtMost(items.lastIndex)].atSec),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textFaint,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.s4),
            )

            // Task 10 會在這裡放動作分層（主按鈕、兩顆圖示鈕、⋯）
            Row(
                Modifier.fillMaxWidth().padding(AppTheme.spacing.s4),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
            ) {}
        }

        if (!hintDone) {
            Text(
                "左右滑動看上一張／下一張",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.accentInk,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppTheme.spacing.s6)
                    .background(AppTheme.colors.overlay)
                    .padding(AppTheme.spacing.s3)
                    // 點它也可以關掉 —— 擋到畫面的東西一定要有辦法立刻收掉
                    .clickable { hintDone = true; onHintSeen() },
            )
        }
    }
}
