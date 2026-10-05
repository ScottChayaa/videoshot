package com.xenyaa.videoshot.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xenyaa.videoshot.core.backup.BackupCodec
import com.xenyaa.videoshot.data.library.BackupCompat
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.checkBackupSchema
import com.xenyaa.videoshot.data.repo.CacheRepo
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface RestoreResult {
    data object Success : RestoreResult

    /**
     * @param needsRestart 這次失敗發生在 `libraryDb.close()` **之後**（也就是換檔那一步本身
     *        失敗），所以這個行程的 Room 連線已經關掉、沒有辦法再打開——不重啟的話接下來
     *        任何一次資料庫讀取都會炸。原本的 `library.db` 仍然完好（改名失敗不會動到任何
     *        一邊），重啟就會乾淨地重新開啟它。三個**換檔前**的驗證失敗（雜湊不符、
     *        `integrity_check` 不過、備份比 app 新）一律是 false：那時 `libraryDb` 還開著、
     *        一切正常,使用者應該可以直接挑另一份備份再試,不該被迫重啟
     *        （最終審查 Important 3 還原側）。
     */
    data class Failure(val reason: String, val needsRestart: Boolean = false) : RestoreResult
}

/**
 * 下載 → 驗證 → 換檔 → 清空 `cache.db` 與草稿(規格第十節的還原流程圖)。
 * **不負責重啟 app**——換檔完成後交給呼叫端(Task 13)決定何時重啟，讓
 * `AppContainer` 用全新的 Room 實例打開新檔(見本 Task 開頭的說明)。
 */
class RestoreManager(
    private val store: BackupStore,
    private val libraryDb: LibraryDatabase,
    private val libraryDbFile: File,
    private val cacheRepo: CacheRepo,
    private val draftsDir: File,
    private val workDir: File,
    private val io: CoroutineDispatcher,
) {
    suspend fun listBackups(): List<RemoteBackup> = store.list()

    suspend fun restore(backup: RemoteBackup): RestoreResult = withContext(io) {
        workDir.mkdirs()
        val gz = File(workDir, "restore-${backup.id}.db.gz")
        val candidate = File(workDir, "restore-${backup.id}.db")
        try {
            store.download(backup.id, gz)
            if (!BackupCodec.verifySha256(gz, backup.sha256)) {
                return@withContext RestoreResult.Failure("下載的檔案跟雲端記錄的雜湊不符，原本的圖庫沒有變動")
            }
            BackupCodec.gunzip(gz, candidate)

            val info = inspectCandidate(candidate.absolutePath)
            if (!info.integrityOk) {
                return@withContext RestoreResult.Failure("備份檔案損毀，原本的圖庫沒有變動")
            }
            when (checkBackupSchema(info.userVersion)) {
                BackupCompat.TOO_NEW ->
                    return@withContext RestoreResult.Failure("這份備份是用比較新的 app 版本做的，請先更新 app")
                BackupCompat.OK, BackupCompat.NEEDS_MIGRATION -> Unit
            }

            libraryDb.close()
            deleteWalSidecarsQuietly()
            // 這裡是不可回頭的那一點：libraryDb 已經關掉了。改名失敗（磁碟滿、權限）時原本的
            // library.db 完好無損（rename(2) 失敗不會動到任何一邊），但這個行程的 Room 連線
            // 沒辦法再打開——所以回傳 needsRestart = true,讓呼叫端重啟 app 用全新的 Room
            // 實例重新開啟那個沒被換掉的舊檔。丟 IllegalStateException 出去（先前的寫法）
            // 只會讓呼叫端顯示「還原失敗」然後停在一個資料庫連線已死的畫面上
            // （最終審查 Important 3 還原側）。
            if (!candidate.renameTo(libraryDbFile)) {
                return@withContext RestoreResult.Failure(
                    reason = "換檔失敗，原本的圖庫沒有變動；app 會重新啟動",
                    needsRestart = true,
                )
            }
            clearReconstructableStateQuietly()
            RestoreResult.Success
        } finally {
            gz.delete()
            candidate.delete()
        }
    }

    /**
     * 換檔成功之後的收尾：清掉 `cache.db` 與草稿。**失敗只吞掉、不往外傳**——換檔已經成功了,
     * 讓這兩步的例外把一次成功的還原回報成「還原失敗」是錯的（而且那時 `libraryDb` 已經關掉、
     * 呼叫端又因為「失敗」而不重啟,使用者就卡在一個資料庫連線已死的 app 裡）。這兩樣都是
     * **可重建**的狀態（AGENTS.md：「無法重建的在 library.db,DB 外面的都能重建」）,
     * 最壞情況是留著一份對不上新圖庫的舊快取／草稿,下次用到時自己會被覆寫或忽略
     * （最終審查 Important 3 還原側）。
     */
    private suspend fun clearReconstructableStateQuietly() {
        try {
            cacheRepo.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 可重建的快取——刻意吞掉
        }
        try {
            draftsDir.deleteRecursively()
        } catch (e: Exception) {
            // 同上（deleteRecursively 本身不丟例外,但 SecurityException 仍有可能）
        }
    }

    /** WAL 模式下正常關閉會 checkpoint，但保守起見還是自己清掉——換檔後這兩個檔案對應舊內容，留著會誤導下一次開啟。 */
    private fun deleteWalSidecarsQuietly() {
        File(libraryDbFile.path + "-wal").delete()
        File(libraryDbFile.path + "-shm").delete()
    }

    private fun inspectCandidate(path: String): CandidateInfo {
        val connection = BundledSQLiteDriver().open(path)
        try {
            val integrityOk = connection.prepare("PRAGMA integrity_check").use { stmt ->
                stmt.step(); stmt.getText(0) == "ok"
            }
            val userVersion = connection.prepare("PRAGMA user_version").use { stmt ->
                stmt.step(); stmt.getLong(0).toInt()
            }
            return CandidateInfo(integrityOk, userVersion)
        } finally {
            connection.close()
        }
    }

    private data class CandidateInfo(val integrityOk: Boolean, val userVersion: Int)
}
