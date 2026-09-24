package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.ui.thumb.ThumbImage
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 清單頁與資料夾頁上半共用的一張卡片。
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
            .clip(shape)
            .background(AppTheme.colors.surface)
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
                Text(
                    "還沒有圖片",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textFaint,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                PreviewCollage(card.preview, loader, Modifier.fillMaxSize())
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = AppTheme.spacing.s3, end = AppTheme.spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = AppTheme.spacing.s2)) {
                Text(card.name, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
                Text(
                    "${card.shotCount} 張",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDim,
                )
            }
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
        ThumbImage(shot = shot, loader = loader, contentDescription = null, modifier = cellModifier)
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
