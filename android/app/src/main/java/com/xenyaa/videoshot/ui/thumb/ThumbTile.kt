package com.xenyaa.videoshot.ui.thumb

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.common.VsSelectedBadge
import com.xenyaa.videoshot.ui.common.VsUnselectedBadge
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/** 縮圖牆的格線（首頁、查詢結果、資料夾內容共用）。刻意偏離 4/8/12/16/24/32 尺標——縮圖牆要靠得緊才像一整面牆。 */
val ThumbGridGap = 1.dp

/**
 * 縮圖牆的一格：正方形、直角、`surface2` 底、`Crop`。
 * 首頁、查詢結果、資料夾內容共用（三處都是左右貼齊螢幕邊緣、格線 [ThumbGridGap]）。
 * 名稱預設走 [labelOf]（有描述唸描述，沒有就唸「片段縮圖 MM:SS」）。
 *
 * [selected] 不是 null＝多選模式（照片頁）：點一下切換選取，格子當核取方塊；已選畫主色框＋左上打勾，
 * 沒選畫空心圈。[onLongClick] 給了才有長按（照片頁用來進入多選）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThumbTile(
    shot: ShotRow,
    loader: ThumbLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = labelOf(shot),
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RectangleShape
    val selecting = selected != null
    Box(
        modifier
            .aspectRatio(1f)
            .focusRing(shape)
            .clip(shape)
            .background(AppTheme.colors.surface2)
            .combinedClickable(
                role = if (selecting) Role.Checkbox else Role.Button,
                onClickLabel = when (selected) {
                    null -> "開啟"
                    true -> "取消選取"
                    false -> "選取"
                },
                onLongClickLabel = if (onLongClick != null) "選取" else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .then(if (selected != null) Modifier.semantics { toggleableState = ToggleableState(selected) } else Modifier),
    ) {
        ThumbImage(shot, loader, Modifier.fillMaxSize(), contentDescription)
        when (selected) {
            true -> {
                Box(Modifier.fillMaxSize().border(3.dp, AppTheme.colors.accent, shape))
                VsSelectedBadge(Modifier.align(Alignment.TopStart).padding(4.dp), semanticLabel = null)
            }
            false -> VsUnselectedBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
            null -> Unit
        }
    }
}

