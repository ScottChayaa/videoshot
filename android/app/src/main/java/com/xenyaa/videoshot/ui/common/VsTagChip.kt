package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.focusRing

enum class ChipSize { Regular, Mini }

/**
 * 標籤／地點小膠囊（原型 `styles.css`「chip」＋ `app.js` 的 `tagChip`）。
 *
 * 種類靠**圖示＋顏色**兩者一起區分；選取靠**變色＋打勾**兩者一起表示——都不只靠顏色（手冊 §五）。
 *
 * 可點的小膠囊分兩種（設計 §八，與 [VsToolbarPill] 的 `isToggle` 同一條規則）：
 * - [isToggle] = false（預設）：動作／導覽（首頁月份標籤、第三步的建議標籤），當成按鈕，**不帶選取語意**，
 *   TalkBack 不會唸「未勾選，核取方塊」；
 * - [isToggle] = true：可切換選取（核取方塊語意，[selected] 才有意義）。
 */
@Composable
fun VsTagChip(
    name: String,
    kind: ChipKind,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    isToggle: Boolean = false,
    count: Int? = null,
    size: ChipSize = ChipSize.Regular,
    onClick: (() -> Unit)? = null,
) {
    val regular = size == ChipSize.Regular
    val shape = if (regular) RoundedCornerShape(AppTheme.radii.full) else RoundedCornerShape(AppTheme.radii.sm)
    val bg = if (selected) AppTheme.colors.accent else AppTheme.colors.surface
    val line = if (selected) AppTheme.colors.accent else AppTheme.colors.border
    val ink = if (selected) AppTheme.colors.accentInk else AppTheme.colors.text
    val iconTint = if (selected) AppTheme.colors.accentInk else kind.color
    val textStyle = if (regular) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    val iconSize = if (regular) 16.dp else 13.dp
    val pad = if (regular) PaddingValues(horizontal = AppTheme.spacing.s4, vertical = AppTheme.spacing.s2)
    else PaddingValues(horizontal = AppTheme.spacing.s2, vertical = 2.dp)

    // 迷你＋可點：視覺膠囊維持迷你，但可點的那一層是至少 44dp 高的透明外框、膠囊置中（手冊 §零 觸控區）。
    // 一般尺寸本來就夠高；不可點的迷你不需要觸控區，維持緊湊。
    val wrapperClick = if (regular) null else onClick
    var m = if (wrapperClick != null) Modifier else modifier
    if (onClick != null && wrapperClick == null) {
        m = m.focusRing(shape).clip(shape)
            .chipClickable(isToggle, selected, onClick)
    }
    // 有外框時，外框的 selectable 是唯一的合併點（文字＋選取＋點擊）；
    // 內層再合併一次會讓內層自成一個節點，外框反而沒有文字
    if (wrapperClick == null) m = m.semantics(mergeDescendants = true) {}
    m = m.clip(shape)
        .background(bg)
        .border(1.dp, line, shape)
        .then(if (regular && onClick != null) Modifier.defaultMinSize(minHeight = AppTheme.spacing.tap) else Modifier)
        .padding(pad)

    val chip: @Composable () -> Unit = {
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s1)) {
            Icon(kind.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
            Text(
                buildAnnotatedString {
                    append(name)
                    if (count != null) {
                        append(' ')
                        withStyle(SpanStyle(color = if (selected) AppTheme.colors.accentInk else AppTheme.colors.textDim)) {
                            append(count.toString())
                        }
                    }
                },
                style = textStyle,
                color = ink,
            )
            if (selected) {
                Icon(
                    VsIcons.Check,
                    contentDescription = "已選",
                    tint = AppTheme.colors.accentInk,
                    modifier = Modifier.size(if (regular) 14.dp else 12.dp),
                )
            }
        }
    }
    if (wrapperClick != null) {
        Box(
            modifier
                .defaultMinSize(minHeight = AppTheme.spacing.tap)
                .focusRing(shape).clip(shape)
                .chipClickable(isToggle, selected, wrapperClick),
            contentAlignment = Alignment.Center,
        ) { chip() }
    } else {
        chip()
    }
}

/** 切換型用核取方塊語意（帶選取狀態）；動作型只是按鈕，沒有選取語意。 */
private fun Modifier.chipClickable(isToggle: Boolean, selected: Boolean, onClick: () -> Unit): Modifier =
    if (isToggle) selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
    else clickable(role = Role.Button, onClick = onClick)
