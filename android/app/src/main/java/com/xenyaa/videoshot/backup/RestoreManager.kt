package com.xenyaa.videoshot.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xenyaa.videoshot.core.backup.BackupCodec
import com.xenyaa.videoshot.data.library.BackupCompat
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.checkBackupSchema
import com.xenyaa.videoshot.data.repo.CacheRepo
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface RestoreResult {
    data object Success : RestoreResult
    data class Failure(val reason: String) : RestoreResult
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
            check(candidate.renameTo(libraryDbFile)) { "換檔失敗：${candidate.path} -> ${libraryDbFile.path}" }
            cacheRepo.clearAll()
            draftsDir.deleteRecursively()
            RestoreResult.Success
        } finally {
            gz.delete()
            candidate.delete()
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
