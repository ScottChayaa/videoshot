package com.xenyaa.videoshot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 標籤小膠囊的種類色，逐一對應原型 `mockups/uiux-v2/app.js` 的 `KIND`。
 *
 * **不隨色系變**：種類色是「辨識種類」的編碼，換主題就換顏色會讓使用者記住的對應失效。不放進 [Palette] 就是這個理由。
 */
object KindColors {
    val place = Color(0xFF0EA5E9)
    val person = Color(0xFF6366F1)
    val topic = Color(0xFF8B5CF6)
    val pet = Color(0xFFF59E0B)
    val other = Color(0xFF64748B)
}
