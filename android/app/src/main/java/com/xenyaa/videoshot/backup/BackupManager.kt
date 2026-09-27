package com.xenyaa.videoshot.backup

import com.xenyaa.videoshot.core.backup.BackupCodec
import com.xenyaa.videoshot.core.backup.RemoteBackupSummary
import com.xenyaa.videoshot.core.backup.backupFileName
import com.xenyaa.videoshot.core.backup.backupsToDelete
import com.xenyaa.videoshot.data.library.LIBRARY_SCHEMA_VERSION
import java.io.File
import kotlinx.coroutines.CancellationException
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
    private val isLinked: suspend () -> Boolean,
    private val lastChangedAtSec: suspend () -> Long,
    private val lastBackupAtSec: suspend () -> Long,
    private val nowSec: () -> Long,
    private val markBackedUp: suspend (nowSec: Long) -> Unit,
) {
    /**
     * @param force true＝帳號頁【立即備份】，跳過「有變更且距上次 > 24 小時」的檢查
     *        （規格第十節「觸發」表的「手動」列）；false＝WorkManager 每日排程，照「自動」列的條件判斷。
     *        **[force] 不能繞過「有沒有連結 Google 帳號」這一關**——見下面第一行的註解。
     * @return true 代表真的執行了一次備份（上傳成功）；false 代表這次判斷不需要備份。
     */
    suspend fun runIfDue(force: Boolean): Boolean = withContext(io) {
        // 沒有連結任何 Google 帳號就沒有備份的去處,連 force 也不例外——這一關要擺在 force
        // 前面。沒連結卻讓它往下走的話：使用者只要用過 app（lastChangedAt > 0、lastBackupAt
        // 還是 0）就會天天通過「有變更且超過 24 小時」的判斷,每天白做一次 VACUUM INTO ＋
        // gzip,最後必然倒在取 token 那一步,再被 WorkManager 依退避重試——純浪費 I/O 與電
        // （最終審查 Important 1）。
        if (!isLinked()) return@withContext false

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
            // 上傳成功的那一刻就記下時間戳,**排在保留策略之前**——保留策略只是整理雲端上
            // 多出來的舊檔,它失敗不代表這次備份失敗。順序反過來的話（先整理再記時間戳）,
            // 整理丟例外會讓 markBackedUp 永遠不會執行：帳號頁報「備份失敗」,而那份備份其實
            // 已經安穩躺在 Drive 上了,下一次排程還會再上傳一份一模一樣的
            // （最終審查 Important 3 備份側）。
            markBackedUp(now)
            enforceRetentionQuietly()
            true
        } finally {
            rawDb.delete()
            gz.delete()
        }
    }

    /**
     * 刪掉第 4 份以後的舊備份。**失敗只是沒整理乾淨,不往外傳**——最壞的情況是 Drive 上
     * 暫時多留幾份舊備份,下一次成功的備份會再整理一次追上來（見 [runIfDue] 裡的註解）。
     */
    private suspend fun enforceRetentionQuietly() {
        try {
            val summaries = store.list().map { RemoteBackupSummary(it.id, it.createdAtEpochSec) }
            backupsToDelete(summaries, keep = 3).forEach { store.delete(it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 整理不成功不影響這次備份的結果——刻意吞掉
        }
    }
}
