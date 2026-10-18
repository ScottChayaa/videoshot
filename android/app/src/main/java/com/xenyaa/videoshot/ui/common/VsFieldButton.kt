package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

/**
 * 欄位按鈕的寬度。五顆加【完成】要擠進 360dp 寬的手機：360 − 左右 32 − 間距 12 − 5×48 = 76dp 給【完成】，
 * 剛好放得下兩個字加左右留白；52dp 的話只剩 56dp，「完成」會被擠成兩行（2026-10-18）。
 */
val FieldButtonWidth = 48.dp

/**
 * 底部列的欄位按鈕（取圖第三步）：**圖示＋下方小字**，四個欄位的圖示不好一眼分辨，帶字才不會按錯（2026-10-18 scott）。
 *
 * [done]：`true`＝圖示右上小綠勾（這一欄每一張都有值）、`false`＝沒有勾、`null`＝這顆沒有完成狀態（【全選】）。
 * 綠勾不是唯一訊號 —— 也寫進 `stateDescription`（「已完成」／「未完成」），TalkBack 唸得出來（手冊 §零）。
 */
@Composable
fun VsFieldButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    done: Boolean? = null,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.radii.sm)
    Column(
        modifier
            .width(FieldButtonWidth)
            .heightIn(min = AppTheme.spacing.tap)
            .focusRing(shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (done != null) stateDescription = if (done) "已完成" else "未完成"
                if (!enabled) disabled()
                onClick { onClick(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) colors.accent else colors.textFaint,
                modifier = Modifier.size(22.dp),
            )
            if (done == true) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(colors.ok)
                        .border(1.5.dp, colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VsIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) colors.textDim else colors.textFaint,
            maxLines = 1,
        )
    }
}
