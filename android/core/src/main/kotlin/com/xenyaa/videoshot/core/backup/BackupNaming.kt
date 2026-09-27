package com.xenyaa.videoshot.core.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

/**
 * 上傳到 Drive 的檔名（規格第十節 `library-{yyyyMMdd-HHmm}.db.gz`）。
 * 用裝置所在時區——單人單機，不必存 UTC 再換算。
 */
fun backupFileName(epochSec: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val stamp = Instant.ofEpochSecond(epochSec).atZone(zone).format(FILE_NAME_FORMAT)
    return "library-$stamp.db.gz"
}

/** Drive 上的一份備份，只留下決定保留策略要用的欄位。 */
data class RemoteBackupSummary(val id: String, val createdAtEpochSec: Long)

/**
 * 保留最近 [keep] 份，其餘要刪除（規格第十節「上傳成功後，才刪除超過 3 份的舊備份」）。
 * 純函式：呼叫端負責「先確認新檔上傳成功才呼叫這個」與「真的送出刪除」。
 */
fun backupsToDelete(all: List<RemoteBackupSummary>, keep: Int = 3): List<RemoteBackupSummary> =
    all.sortedByDescending { it.createdAtEpochSec }.drop(keep)

/** 帳號頁「上次備份：…」的顯示文字（手冊 §一：完成後顯示「上次備份：剛剛」）。 */
fun lastBackupLabel(lastBackupAtEpochSec: Long, nowEpochSec: Long): String {
    if (lastBackupAtEpochSec <= 0L) return "尚未備份"
    val deltaSec = (nowEpochSec - lastBackupAtEpochSec).coerceAtLeast(0)
    return when {
        deltaSec < 60 -> "剛剛"
        deltaSec < 3600 -> "${deltaSec / 60} 分鐘前"
        deltaSec < 86400 -> "${deltaSec / 3600} 小時前"
        else -> "${deltaSec / 86400} 天前"
    }
}
