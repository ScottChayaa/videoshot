package com.xenyaa.videoshot.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing
import com.xenyaa.videoshot.wizard.Step3DetailsScreen

/**
 * 批次編輯的外殼：✕ ＋ 標題，其餘整段是階段 6 的 [Step3DetailsScreen]（規格第六節：不另做一套）。
 * **沒有底部導覽**（比照精靈／Lightbox）——理由同 `wizard/Step3DetailsScreen.kt` 的 KDoc：
 * 抽屜／按鈕列／導覽列三層會把縮圖區壓到剩四成。
 */
@Composable
fun BatchEditScreen(
    state: BatchEditState,
    bitmapFor: suspend (Int) -> ImageBitmap?,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onInvert: () -> Unit,
    onSelectUnapplied: () -> Unit,
    onEditEventDate: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onEditDescription: (String) -> Unit,
    onEditTags: (List<String>) -> Unit,
    onApply: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            BatchEditState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在載入…")
            }
            BatchEditState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("這支影片還沒有收藏，沒有東西可以編輯")
            }
            is BatchEditState.Ready -> {
                val step3State by state.store.state.collectAsStateWithLifecycle()
                Step3DetailsScreen(
                    state = step3State,
                    bitmapFor = bitmapFor,
                    onToggle = onToggle,
                    onSelectAll = onSelectAll,
                    onSelectNone = onSelectNone,
                    onInvert = onInvert,
                    onSelectUnapplied = onSelectUnapplied,
                    onEditEventDate = onEditEventDate,
                    onEditPlace = onEditPlace,
                    onEditDescription = onEditDescription,
                    onEditTags = onEditTags,
                    onApply = onApply,
                    onFinish = onFinish,
                    placeSuggestions = state.places,
                    tagSuggestions = state.tags,
                    modifier = Modifier.fillMaxSize().padding(top = 56.dp),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(AppTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(AppTheme.spacing.tap).focusRing(CircleShape)) {
                Icon(VsIcons.Close, contentDescription = "關閉", tint = AppTheme.colors.text)
            }
            Text(
                "批次編輯圖資",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.text,
                modifier = Modifier.padding(start = AppTheme.spacing.s2),
            )
        }
    }
}
