package com.xenyaa.videoshot.wizard

/** 影片時間標籤。超過一小時就讓分鐘數長過 60（`61:01`）—— 取圖用不到「時」這一位。 */
fun formatClock(sec: Double): String {
    val total = sec.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
