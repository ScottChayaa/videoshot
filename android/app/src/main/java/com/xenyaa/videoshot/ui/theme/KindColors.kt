package com.xenyaa.videoshot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 小膠囊的種類色：地點與標籤各一色（標籤不分種類）。
 *
 * **不隨色系變**：種類色是「辨識種類」的編碼，換主題就換顏色會讓使用者記住的對應失效。不放進 [Palette] 就是這個理由。
 */
object KindColors {
    /** 綠松：海水的顏色（夕陽海灘色系，2026-10-25）。 */
    val place = Color(0xFF0EA5B7)
    /** 跟地點同色（2026-11-08；之前是漂流木棕 #8C6D46）：兩者靠圖示（地圖針／`#`）分辨。 */
    val tag = Color(0xFF0EA5B7)
}
