package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.core.time.formatClock
import com.xenyaa.videoshot.ui.common.chineseDate

/**
 * 長按縮圖浮出的單張圖資（規格第五節第三步「長按縮圖」）：時間、地點、標籤、描述、影片時間點，沒有值的寫「未設定」。
 * 背景半透明黑、白字，看得到底下的縮圖牆。**點畫面任何地方才關**（含點浮窗本身）—— `focusable` 的 Popup
 * 點外面與返回鍵都會呼叫 [onDismiss]。
 */
@Composable
internal fun Step3InfoPopup(cell: Step3Cell, details: ShotDetails?, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val position = remember(gap) { BelowOrAbove(gap) }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .widthIn(max = 260.dp)
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val unset = "未設定"
            InfoRow(DetailField.DATE, details?.eventDate?.takeIf { it.isNotBlank() }?.let(::chineseDate) ?: unset)
            InfoRow(DetailField.PLACE, details?.place?.takeIf { it.isNotBlank() } ?: unset)
            InfoRow(DetailField.TAGS, details?.tags?.takeIf { it.isNotEmpty() }?.joinToString("、") ?: unset)
            InfoRow(DetailField.DESCRIPTION, details?.description?.takeIf { it.isNotBlank() } ?: unset)
            Text(
                "影片 ${formatClock(cell.atSec)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun InfoRow(field: DetailField, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(field.icon, contentDescription = field.label, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/** 放在格子下方；下方放不下就放上方。左右夾在視窗內（最左、最右那欄的格子浮窗不會超出螢幕）。 */
private class BelowOrAbove(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
