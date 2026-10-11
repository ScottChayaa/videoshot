package com.xenyaa.videoshot.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader

/**
 * 相簿畫面與封存相簿畫面共用的兩欄卡片格線（原型 `.fd-grid`：欄距 8、列距 12；狀態列後面上內距收成 8）。
 *
 * [selection] 非空＝多選中：點一下切換選取；不在多選時點一下開相簿。長按一律切換選取（進入多選）。
 */
@Composable
fun FolderCardGrid(
    cards: List<FolderCard>,
    loader: ThumbLoader,
    selection: Set<Long>,
    onOpen: (FolderCard) -> Unit,
    onToggleSelect: (FolderCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selecting = selection.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize().testTag("folderGrid"),
        contentPadding = PaddingValues(
            start = AppTheme.spacing.s3, top = AppTheme.spacing.s2,
            end = AppTheme.spacing.s3, bottom = AppTheme.spacing.s3,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s2),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        items(cards, key = { it.id }) { card ->
            FolderCardTile(
                card = card,
                loader = loader,
                onClick = { if (selecting) onToggleSelect(card) else onOpen(card) },
                onLongClick = { onToggleSelect(card) },
                selected = if (selecting) card.id in selection else null,
            )
        }
    }
}
