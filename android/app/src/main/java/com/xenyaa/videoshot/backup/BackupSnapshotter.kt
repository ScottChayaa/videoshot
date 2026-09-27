package com.xenyaa.videoshot.backup

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.xenyaa.videoshot.data.library.LibraryDatabase
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 對 `library.db` 做一次一致的快照（規格第十節：「`VACUUM INTO` 產生一致的快照——
 * WAL 模式下不可直接複製 DB 檔」）。快照本身就是一個完整、獨立、可直接開啟的 SQLite 檔，
 * 連 `user_version`（schema 版本）都會一起複製過去，還原流程要靠它判斷相容性。
 */
class BackupSnapshotter(
    private val libraryDb: LibraryDatabase,
    private val io: CoroutineDispatcher,
) {
    /** [dest] 事先不能存在——`VACUUM INTO` 遇到已存在的目的檔會直接失敗，所以先刪一次。 */
    suspend fun snapshotTo(dest: File) = withContext(io) {
        dest.delete()
        val escapedPath = dest.absolutePath.replace("'", "''")
        libraryDb.useWriterConnection { connection ->
            (connection as SQLiteConnection).execSQL("VACUUM INTO '$escapedPath'")
        }
    }
}
