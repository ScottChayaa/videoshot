package com.xenyaa.videoshot.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 焦點框的粗細。不進 `Spacing` —— 那是版面的尺標，這是描邊。 */
val FocusRingWidth: Dp = 2.dp

/**
 * 鍵盤焦點框（手冊 §零：「接上實體鍵盤時，用 Tab 走得到每個可點的東西、看得到焦點框」）。
 *
 * 觸控不會產生焦點，所以手機上平常看不到這個框；它是給實體鍵盤、藍牙鍵盤與方向鍵用的。
 * 顏色取自色系的 `focusRing`（半透明，疊在元件原本的外觀上），換色系會跟著換。
 *
 * **要放在讓元件變成可聚焦的那個 modifier（`clickable`／`focusable`）之前** ——
 * `onFocusChanged` 只看得到排在它後面的焦點節點：
 *
 * ```
 * Modifier.clip(shape).focusRing(shape).clickable { … }
 * ```
 *
 * 判斷用 `hasFocus` 而不是 `isFocused`：`NavigationBarItem`、`IconButton` 這些現成元件
 * 把 focusable 藏在內部，外面只套得到容器，那時焦點是落在子節點上。
 *
 * 邊框一律掛著、只換顏色（沒有焦點時是透明），這樣 modifier 鏈的結構不隨焦點改變。
 */
@Composable
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(Radii.sm)): Modifier {
    var focused by remember { mutableStateOf(false) }
    val ring = AppTheme.colors.focusRing
    return this
        .onFocusChanged { focused = it.hasFocus }
        .border(FocusRingWidth, if (focused) ring else Color.Transparent, shape)
}
