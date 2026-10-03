package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.AppTheme

/**
 * 空狀態（原型「通用狀態」的 `.empty`）。有動作就帶一顆按鈕，不只是一句灰字（手冊 §六）。
 *
 * [secondary]＝訊息下方的第二行說明（15 `textDim` 置中）。
 * [actionText] 與 [onAction] **必須同時給或同時不給**——只給一個一定是呼叫端漏接線，直接 `require` 擋下。
 * [actionVariant] 預設主要按鈕；「清除篩選」這類次要動作可以改 Secondary。
 */
@Composable
fun VsEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionVariant: ButtonVariant = ButtonVariant.Primary,
) {
    require((actionText == null) == (onAction == null)) { "actionText 與 onAction 必須同時給或同時不給" }
    Column(
        modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s3),
    ) {
        if (icon != null) Icon(icon, null, tint = AppTheme.colors.textFaint, modifier = Modifier.size(34.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textDim, textAlign = TextAlign.Center)
        if (secondary != null) {
            Text(secondary, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textDim, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            VsButton(actionText, onAction, Modifier.padding(top = AppTheme.spacing.s2), variant = actionVariant)
        }
    }
}
