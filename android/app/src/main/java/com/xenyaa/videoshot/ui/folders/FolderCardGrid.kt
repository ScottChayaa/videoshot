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

/** 相簿畫面與封存相簿畫面共用的兩欄卡片格線（原型 `.fd-grid`：欄距 8、列距 12；狀態列後面上內距收成 8）。 */
@Composable
fun FolderCardGrid(
    cards: List<FolderCard>,
    loader: ThumbLoader,
    onOpen: (FolderCard) -> Unit,
    onRename: (FolderCard) -> Unit,
    onToggleArchive: (FolderCard) -> Unit,
    onDelete: (FolderCard) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                onOpen = { onOpen(card) },
                onRename = { onRename(card) },
                onToggleArchive = { onToggleArchive(card) },
                onDelete = { onDelete(card) },
            )
        }
    }
}
