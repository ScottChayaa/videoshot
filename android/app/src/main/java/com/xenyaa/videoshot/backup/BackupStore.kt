package com.xenyaa.videoshot.backup

import java.io.File

/** Drive appDataFolder 裡的一份備份（規格第十節）。`appProperties` 在 Drive REST 裡本來就都是字串。 */
data class RemoteBackup(
    val id: String,
    val name: String,
    val createdAtEpochSec: Long,
    val sizeBytes: Long,
    val schemaVersion: Int,
    val shotCount: Int,
    val deviceName: String,
    val sha256: String,
)

/** 要上傳的一份備份：本機已經壓縮好、算好雜湊的檔案，加上要寫進 `appProperties` 的中繼資料。 */
data class NewBackup(
    val file: File,
    val sha256: String,
    val schemaVersion: Int,
    val shotCount: Int,
    val deviceName: String,
    val createdAtEpochSec: Long,
)

/**
 * app 專用隱藏資料夾（appDataFolder）的存取介面（規格第三節模組邊界：
 * **Google Drive 只在 `backup` 裡用**，其他地方不得直接碰 Drive REST）。
 * 真正的 Drive 實作見 [DriveBackupStore]；測試與尚未連上真帳號時用 [FakeBackupStore]。
 */
interface BackupStore {
    /** 依建立時間新到舊排序。 */
    suspend fun list(): List<RemoteBackup>

    suspend fun upload(
        backup: NewBackup,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): RemoteBackup

    suspend fun download(
        id: String,
        dest: File,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    )

    suspend fun delete(id: String)
}
