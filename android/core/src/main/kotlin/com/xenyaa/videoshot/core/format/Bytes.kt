package com.xenyaa.videoshot.core.format

import java.util.Locale

/**
 * 人類看得懂的檔案大小（帳號頁「儲存用量」，規格附錄 A-8：只顯示用量，不設上限）。
 *
 * **不用 `android.text.format.Formatter`**——那個要 `Context`，`:core` 不依賴 Android SDK
 * （CLAUDE.md「純邏輯放 :core」）。1024 進位（跟系統設定頁一致，不是硬碟廠商的 1000 進位）；
 * 小數位固定用 `Locale.US`——系統語系用逗號當小數點的話（少數地區），`%.1f` 會印出 `12,3 MB`。
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
