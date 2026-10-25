package com.xenyaa.videoshot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 小膠囊的種類色：地點與標籤各一色（標籤不分種類）。
 *
 * **不隨色系變**：種類色是「辨識種類」的編碼，換主題就換顏色會讓使用者記住的對應失效。不放進 [Palette] 就是這個理由。
 */
object KindColors {
    val place = Color(0xFF0EA5E9)
    /** 石板藍灰：標籤是輔助資訊，要比主色安靜；跟地點的天藍靠鮮豔度分開。 */
    val tag = Color(0xFF64748B)
}
