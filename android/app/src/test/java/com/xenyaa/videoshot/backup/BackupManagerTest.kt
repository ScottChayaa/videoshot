package com.xenyaa.videoshot.backup

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** 預設已連結——大多數案例測的是別的判斷,沒連結的那條路徑有自己的案例。 */
    private fun manager(isLinked: Boolean = true) = BackupManager(
        snapshotTo = { dest -> dest.writeBytes(ByteArray(16) { it.toByte() }) },
        store = store,
        workDir = workDir,
        io = Dispatchers.IO,
        deviceName = { "測試裝置" },
        shotCount = { 7 },
        isLinked = { isLinked },
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

    /**
     * 最終審查 Important 1：沒有連結 Google 帳號就沒有備份的去處，連 force（帳號頁
     * 【立即備份】）都不該做——不擋的話會白做一次 VACUUM INTO ＋ gzip，最後必然倒在取 token
     * 那一步。這裡要確認的是「連 store.upload 都沒被呼叫」，不只是回傳 false。
     */
    @Test
    fun 沒有連結帳號時連force也不備份() = runTest {
        lastChangedAt = 200; lastBackupAt = 0; now = 100_000
        val ran = manager(isLinked = false).runIfDue(force = true)
        assertFalse(ran)
        assertTrue("沒連結就不該有任何上傳", store.list().isEmpty())
        assertNull(markedBackedUpAt)
    }

    /** 同上的自動排程版：沒連結的裝置天天都會通過「有變更且超過 24 小時」，要在更前面就擋掉。 */
    @Test
    fun 沒有連結帳號時自動排程也不備份() = runTest {
        lastChangedAt = 200; lastBackupAt = 100; now = 100 + 25 * 3600
        assertFalse(manager(isLinked = false).runIfDue(force = false))
        assertTrue(store.list().isEmpty())
    }

    /**
     * 規格第十節：「失敗則保留『有變更』標記，下次再試」——上傳失敗時絕對不能更新
     * `lastBackupAt`，不然那次沒上傳成功的變更會被當成已經備份過，永遠不會再被送上去。
     */
    @Test
    fun 上傳失敗時不更新上次備份時間() = runTest {
        now = 7_000
        store.failNextUpload = RuntimeException("上傳中斷")
        runCatching { manager().runIfDue(force = true) }
        assertNull(markedBackedUpAt)
        assertTrue(store.list().isEmpty())
    }

    /**
     * 保留策略只是整理雲端上多出來的舊檔，它失敗不代表這次備份失敗——備份已經在 Drive 上了，
     * `markBackedUp` 一定要照記（最終審查 Important 3 備份側）。
     */
    @Test
    fun 保留策略失敗不影響這次備份的結果() = runTest {
        now = 9_000
        val failingRetention = object : BackupStore by store {
            override suspend fun list(): List<RemoteBackup> = throw RuntimeException("列出失敗")
        }
        val mgr = BackupManager(
            snapshotTo = { dest -> dest.writeBytes(ByteArray(16)) },
            store = failingRetention,
            workDir = workDir,
            io = Dispatchers.IO,
            deviceName = { "測試裝置" },
            shotCount = { 7 },
            isLinked = { true },
            lastChangedAtSec = { lastChangedAt },
            lastBackupAtSec = { lastBackupAt },
            nowSec = { now },
            markBackedUp = { at -> markedBackedUpAt = at },
        )
        assertTrue(mgr.runIfDue(force = true))
        assertEquals(9_000L, markedBackedUpAt)
    }

    @Test
    fun 結束後不留暫存檔() = runTest {
        now = 5_000
        manager().runIfDue(force = true)
        assertTrue(workDir.listFiles()?.isEmpty() != false)
    }

    @After fun tearDown() {}
}
