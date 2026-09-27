package com.xenyaa.videoshot.core.backup

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupNamingTest {

    @Test
    fun 檔名格式符合規格() {
        // 2026-10-01 11:45:00 UTC
        val epochSec = 1790855100L
        assertEquals("library-20261001-1945.db.gz", backupFileName(epochSec, ZoneOffset.ofHours(8)))
    }

    @Test
    fun 保留最新_3_份_其餘要刪除() {
        val all = listOf(
            RemoteBackupSummary("a", 500),
            RemoteBackupSummary("b", 100),
            RemoteBackupSummary("c", 400),
            RemoteBackupSummary("d", 300),
            RemoteBackupSummary("e", 200),
        )
        val toDelete = backupsToDelete(all, keep = 3)
        assertEquals(listOf("e", "b"), toDelete.map { it.id })
    }

    @Test
    fun 不到保留門檻時全部留著() {
        val all = listOf(RemoteBackupSummary("a", 100), RemoteBackupSummary("b", 200))
        assertEquals(emptyList<RemoteBackupSummary>(), backupsToDelete(all, keep = 3))
    }

    @Test
    fun 尚未備份過顯示尚未備份() {
        assertEquals("尚未備份", lastBackupLabel(0L, 1_000_000L))
    }

    @Test
    fun 一分鐘內顯示剛剛() {
        assertEquals("剛剛", lastBackupLabel(1_000_000L, 1_000_030L))
    }

    @Test
    fun 超過一小時顯示小時前() {
        assertEquals("3 小時前", lastBackupLabel(1_000_000L, 1_000_000L + 3 * 3600 + 10))
    }
}
