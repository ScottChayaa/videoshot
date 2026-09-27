package com.xenyaa.videoshot.backup

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class BackupManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var workDir: File
    private lateinit var store: FakeBackupStore
    private var lastChangedAt = 0L
    private var lastBackupAt = 0L
    private var now = 0L
    private var markedBackedUpAt: Long? = null

    private fun manager() = BackupManager(
        snapshotTo = { dest -> dest.writeBytes(ByteArray(16) { it.toByte() }) },
        store = store,
        workDir = workDir,
        io = Dispatchers.IO,
        deviceName = { "測試裝置" },
        shotCount = { 7 },
        lastChangedAtSec = { lastChangedAt },
        lastBackupAtSec = { lastBackupAt },
        nowSec = { now },
        markBackedUp = { at -> markedBackedUpAt = at },
    )

    @Before fun setUp() {
        workDir = tmp.newFolder("work")
        store = FakeBackupStore()
    }

    @Test
    fun 沒有變更時不強制執行就不備份() = runTest {
        lastChangedAt = 100; lastBackupAt = 100; now = 100 + 25 * 3600
        val ran = manager().runIfDue(force = false)
        assertFalse(ran)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun 有變更但不到24小時就不備份() = runTest {
        lastChangedAt = 200; lastBackupAt = 100; now = 100 + 3600 // 只過 1 小時
        val ran = manager().runIfDue(force = false)
        assertFalse(ran)
    }

    @Test
    fun 有變更且超過24小時才備份() = runTest {
        lastChangedAt = 200; lastBackupAt = 100; now = 100 + 25 * 3600
        val ran = manager().runIfDue(force = false)
        assertTrue(ran)
        assertEquals(1, store.list().size)
        assertEquals(now, markedBackedUpAt)
    }

    @Test
    fun force為true時忽略時間限制() = runTest {
        lastChangedAt = 100; lastBackupAt = 100; now = 100 // 剛備份完，沒有變更
        val ran = manager().runIfDue(force = true)
        assertTrue(ran)
    }

    @Test
    fun 上傳的appProperties帶正確中繼資料() = runTest {
        now = 1_000
        manager().runIfDue(force = true)
        val uploaded = store.list().single()
        assertEquals(7, uploaded.shotCount)
        assertEquals("測試裝置", uploaded.deviceName)
    }

    @Test
    fun 保留策略只留最新3份() = runTest {
        val mgr = manager()
        repeat(4) { i -> now = 1000L + i * 100; mgr.runIfDue(force = true) }
        assertEquals(3, store.list().size)
        assertEquals(listOf(1300L, 1200L, 1100L), store.list().map { it.createdAtEpochSec })
    }

    @Test
    fun 結束後不留暫存檔() = runTest {
        now = 5_000
        manager().runIfDue(force = true)
        assertTrue(workDir.listFiles()?.isEmpty() != false)
    }

    @After fun tearDown() {}
}
