package com.xenyaa.videoshot.core.time

/**
 * 影片秒數的顯示格式 `MM:SS`。超過一小時仍然只進位到分（`75:30`）——
 * 收藏的是影片裡的一個時間點，`01:15:30` 在縮圖角落太長。
 */
fun formatClock(sec: Double): String {
    val total = sec.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
