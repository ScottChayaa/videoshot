package com.xenyaa.videoshot.backup

import com.xenyaa.videoshot.core.backup.BackupCodec
import com.xenyaa.videoshot.core.backup.RemoteBackupSummary
import com.xenyaa.videoshot.core.backup.backupFileName
import com.xenyaa.videoshot.core.backup.backupsToDelete
import com.xenyaa.videoshot.data.library.LIBRARY_SCHEMA_VERSION
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 快照 → 上傳 → 保留 3 份 → 更新時間戳 的協調者（規格第十節）。手動【立即備份】與
 * WorkManager 的每日自動備份都呼叫這裡——差別只在有沒有繞過「距上次備份 > 24 小時」的檢查。
 */
class BackupManager(
    private val snapshotTo: suspend (dest: File) -> Unit,
    private val store: BackupStore,
    private val workDir: File,
    private val io: CoroutineDispatcher,
    private val deviceName: () -> String,
    private val shotCount: suspend () -> Int,
    private val lastChangedAtSec: suspend () -> Long,
    private val lastBackupAtSec: suspend () -> Long,
    private val nowSec: () -> Long,
    private val markBackedUp: suspend (nowSec: Long) -> Unit,
) {
    /**
     * @param force true＝帳號頁【立即備份】，跳過「有變更且距上次 > 24 小時」的檢查
     *        （規格第十節「觸發」表的「手動」列）；false＝WorkManager 每日排程，照「自動」列的條件判斷。
     * @return true 代表真的執行了一次備份（上傳成功）；false 代表這次判斷不需要備份。
     */
    suspend fun runIfDue(force: Boolean): Boolean = withContext(io) {
        if (!force) {
            val changed = lastChangedAtSec() > lastBackupAtSec()
            val overADay = nowSec() - lastBackupAtSec() > 24 * 3600
            if (!changed || !overADay) return@withContext false
        }

        workDir.mkdirs()
        val now = nowSec()
        val rawDb = File(workDir, "snapshot-$now.db")
        val gz = File(workDir, backupFileName(now))
        try {
            snapshotTo(rawDb)
            val sha256 = BackupCodec.gzipWithSha256(rawDb, gz)
            val newBackup = NewBackup(
                file = gz,
                sha256 = sha256,
                schemaVersion = LIBRARY_SCHEMA_VERSION,
                shotCount = shotCount(),
                deviceName = deviceName(),
                createdAtEpochSec = now,
            )
            store.upload(newBackup)
            enforceRetention()
            markBackedUp(now)
            true
        } finally {
            rawDb.delete()
            gz.delete()
        }
    }

    private suspend fun enforceRetention() {
        val summaries = store.list().map { RemoteBackupSummary(it.id, it.createdAtEpochSec) }
        backupsToDelete(summaries, keep = 3).forEach { store.delete(it.id) }
    }
}
