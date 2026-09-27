package com.xenyaa.videoshot.backup

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** [BackupStore] 的記憶體版本（比照 `FakePlayer`／`FakeCapture` 的角色）。 */
class FakeBackupStore : BackupStore {
    private val nextId = AtomicLong(1)
    private val files = mutableMapOf<String, ByteArray>()
    private val backups = mutableListOf<RemoteBackup>()

    var failNextUpload: Exception? = null
    var failNextDownload: Exception? = null

    override suspend fun list(): List<RemoteBackup> = backups.sortedByDescending { it.createdAtEpochSec }

    override suspend fun upload(backup: NewBackup, onProgress: (Long, Long) -> Unit): RemoteBackup {
        failNextUpload?.let { failNextUpload = null; throw it }
        val bytes = backup.file.readBytes()
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        val id = "fake-${nextId.getAndIncrement()}"
        files[id] = bytes
        val remote = RemoteBackup(
            id = id,
            name = backup.file.name,
            createdAtEpochSec = backup.createdAtEpochSec,
            sizeBytes = bytes.size.toLong(),
            schemaVersion = backup.schemaVersion,
            shotCount = backup.shotCount,
            deviceName = backup.deviceName,
            sha256 = backup.sha256,
        )
        backups += remote
        return remote
    }

    override suspend fun download(id: String, dest: File, onProgress: (Long, Long) -> Unit) {
        failNextDownload?.let { failNextDownload = null; throw it }
        val bytes = files[id] ?: error("no such backup: $id")
        dest.writeBytes(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }

    override suspend fun delete(id: String) {
        files.remove(id)
        backups.removeAll { it.id == id }
    }
}
