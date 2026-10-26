package com.xenyaa.videoshot.core.timeline

/**
 * 剪輯的輸出方向：決定輸出的解析度與長度上限。
 *
 * 直式 9:16 在 3 分鐘內上傳到 YouTube 會成為 Shorts；橫式 16:9 最長 10 分鐘。
 * 純邏輯，不依賴 Android 型別；`maxDurationMs` 是整支輸出片的長度上限（毫秒）。
 */
enum class ClipOrientation(val width: Int, val height: Int, val maxDurationMs: Long) {
    PORTRAIT(1080, 1920, 180_000),
    LANDSCAPE(1920, 1080, 600_000),
}
