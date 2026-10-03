package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xenyaa.videoshot.ui.theme.OnDarkColors
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 分類清單頁的一張卡片（原型 `.fd-card`）。**資料夾頁上半用的是另一份卡片**（見 `FolderScreen.kt`）。
 *
 * `surface` 底、1dp `border`、圓角 12、輕陰影。圖片區維持 16:9 的 2×2 靜態拼貼
 * （原型是 72px 高的輪播；手冊只要求「內容預覽拼貼（最多 4 張）」，手冊優先）。
 * 張數是圖片區右下角的膠囊，名稱列不再顯示張數。
 *
 * `⋯` 是**看得見的**按鈕（手冊 §六第二條：長按仍然有效，但不是唯一入口）——
 * 只有長按的話，沒有人會發現改名跟刪除在哪裡。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCardTile(
    card: FolderCard,
    loader: ThumbLoader,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AppTheme.radii.md)

    Column(
        modifier
            .shadow(1.dp, shape)
            .clip(shape)
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.border, shape)
            .focusRing(shape)
            .combinedClickable(
                onClickLabel = "開啟",
                onLongClickLabel = "更多操作",
                onLongClick = { menuOpen = true },
                onClick = onOpen,
            ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(AppTheme.colors.surface2)) {
            if (card.preview.isEmpty()) {
                // 空資料夾：置中的資料夾圖示 24dp ＋「還沒有圖片」（手冊 §六第一條）
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1),
                ) {
                    Icon(VsIcons.Folder, contentDescription = null, tint = AppTheme.colors.textFaint, modifier = Modifier.size(24.dp))
                    Text(
                        "還沒有圖片",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textFaint,
                    )
                }
            } else {
                PreviewCollage(card.preview, loader, Modifier.fillMaxSize())
            }
            // 張數膠囊（原型 `.fd-n`）：scrim 底、白字 13、全圓角、左右內距 8、行高 20
            Text(
                "${card.shotCount} 張",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = OnDarkColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppTheme.spacing.s2)
                    .background(AppTheme.colors.scrim, CircleShape)
                    .padding(horizontal = AppTheme.spacing.s2),
            )
        }
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = AppTheme.spacing.tap)
                .padding(start = AppTheme.spacing.s3, end = AppTheme.spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                card.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = AppTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = AppTheme.spacing.s2),
            )
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape),
                ) {
                    Icon(VsIcons.More, contentDescription = "「${card.name}」的更多操作", tint = AppTheme.colors.textDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("改名") },
                        leadingIcon = { Icon(VsIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        // 破壞性動作：這是這一頁唯一用 danger 色的地方（手冊 §零第二條）
                        text = { Text("刪除資料夾", color = AppTheme.colors.danger) },
                        leadingIcon = { Icon(VsIcons.Trash, contentDescription = null, tint = AppTheme.colors.danger) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/**
 * 預覽拼貼：1 張＝滿版，2 張＝左右，3 張＝左半 ＋ 右邊上下，4 張＝2×2。
 *
 * **靜態拼貼，不是輪播** —— 原型每 4 秒換一張，等於每張卡各跑一個計時器；
 * 手冊只要求「內容預覽拼貼（最多 4 張）」。
 */
@Composable
private fun PreviewCollage(shots: List<ShotRow>, loader: ThumbLoader, modifier: Modifier) {
    val gap = AppTheme.spacing.s1

    @Composable
    fun cell(shot: ShotRow, cellModifier: Modifier) {
        // 小格子放不下圖示＋秒數（15C 最終審查），預留圖只畫圖示
        ThumbImage(shot = shot, loader = loader, contentDescription = null, modifier = cellModifier, showTimeOnPlaceholder = false)
    }

    when (shots.size) {
        1 -> cell(shots[0], modifier)
        2 -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            cell(shots[0], Modifier.weight(1f).fillMaxSize())
            cell(shots[1], Modifier.weight(1f).fillMaxSize())
        }
        3 -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            cell(shots[0], Modifier.weight(1f).fillMaxSize())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                cell(shots[1], Modifier.weight(1f).fillMaxWidth())
                cell(shots[2], Modifier.weight(1f).fillMaxWidth())
            }
        }
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                cell(shots[0], Modifier.weight(1f).fillMaxSize())
                cell(shots[1], Modifier.weight(1f).fillMaxSize())
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                cell(shots[2], Modifier.weight(1f).fillMaxSize())
                cell(shots[3], Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}
