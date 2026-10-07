package com.xenyaa.videoshot.backup

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.backup.BackupCodec
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.readSingleLong
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var libraryDbFile: File
    private lateinit var libraryDb: LibraryDatabase
    private lateinit var cacheDbFile: File
    private lateinit var draftsDir: File
    private lateinit var workDir: File
    private lateinit var store: FakeBackupStore

    private fun openLibraryDb(): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, libraryDbFile.path)
            .setDriver(BundledSQLiteDriver())
            .addCallback(LibrarySchemaCallback)
            .addMigrations(*LIBRARY_MIGRATIONS)
            .build()

    private fun pick(frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = "2026-09-27", place = null, description = null, webp = null,
    )

    @Before fun setUp() {
        libraryDbFile = File(tmp.root, "library.db")
        cacheDbFile = File(tmp.root, "cache.db")
        draftsDir = File(tmp.root, "drafts").apply { mkdirs(); File(this, "v1/sheets").mkdirs() }
        workDir = File(tmp.root, "restore_work")
        libraryDb = openLibraryDb()
        store = FakeBackupStore()
    }

    @After fun tearDown() { if (::libraryDb.isInitialized) runCatching { libraryDb.close() } }

    private fun manager(db: LibraryDatabase = libraryDb) = RestoreManager(
        store = store,
        libraryDb = db,
        libraryDbFile = libraryDbFile,
        cacheRepo = RoomCacheRepo(
            Room.databaseBuilder(context, CacheDatabase::class.java, cacheDbFile.path)
                .setDriver(BundledSQLiteDriver()).build(),
            Dispatchers.IO,
        ),
        draftsDir = draftsDir,
        workDir = workDir,
        io = Dispatchers.IO,
    )

    /** 上傳一份「別的裝置」的備份到 [store]：內容跟現在本機的 library.db 不一樣，換檔後才驗得出來真的換過。 */
    private suspend fun uploadBackupWithShots(shotCount: Int): RemoteBackup {
        val otherDbFile = File(tmp.root, "other-${shotCount}.db")
        val otherDb = Room.databaseBuilder(context, LibraryDatabase::class.java, otherDbFile.path)
            .setDriver(BundledSQLiteDriver()).addCallback(LibrarySchemaCallback).addMigrations(*LIBRARY_MIGRATIONS).build()
        RoomLibraryRepo(otherDb, Dispatchers.IO).commitPicks(
            VideoEntity("other", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            (0 until shotCount).map { pick(it) },
        )
        val snapshotFile = File(tmp.root, "snapshot-${shotCount}.db")
        BackupSnapshotter(otherDb, Dispatchers.IO).snapshotTo(snapshotFile)
        otherDb.close()
        val gz = File(tmp.root, "snapshot-${shotCount}.db.gz")
        val sha256 = BackupCodec.gzipWithSha256(snapshotFile, gz)
        return store.upload(NewBackup(gz, sha256, 1, shotCount, "別的裝置", 100))
    }

    @Test
    fun 成功還原後本機資料變成備份的內容_舊快取與草稿被清空() = runTest {
        // 本機先有 2 張
        RoomLibraryRepo(libraryDb, Dispatchers.IO).commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0), pick(1)),
        )
        val remote = uploadBackupWithShots(shotCount = 5)

        val result = manager().restore(remote)

        assertTrue(result is RestoreResult.Success)
        val reopened = openLibraryDb()
        val shotCount = reopened.readSingleLong("SELECT COUNT(*) FROM shot")
        assertEquals(5L, shotCount)
        reopened.close()
        assertFalse(File(draftsDir, "v1/sheets").exists())
    }

    @Test
    fun 備份比app新時拒絕_原本資料不動() = runTest {
        RoomLibraryRepo(libraryDb, Dispatchers.IO).commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0)),
        )
        val remote = uploadBackupWithShots(shotCount = 3)
        // 手動把剛上傳那份備份的檔案內容的 user_version 改成比現在的 app 新——
        // 用另一個 driver 連線直接改，模擬「這份備份是用比較新的 app 版本做的」
        val bumpedGz = File(tmp.root, "bumped.db.gz")
        val bumpedRaw = File(tmp.root, "bumped.db")
        store.download(remote.id, bumpedGz)
        BackupCodec.gunzip(bumpedGz, bumpedRaw)
        val driver = androidx.sqlite.driver.bundled.BundledSQLiteDriver()
        driver.open(bumpedRaw.absolutePath).use { connection ->
            connection.prepare("PRAGMA user_version = 9999").use { it.step() }
        }
        val newSha = BackupCodec.gzipWithSha256(bumpedRaw, bumpedGz)
        store.delete(remote.id)
        val tooNewRemote = store.upload(NewBackup(bumpedGz, newSha, 9999, 3, "未來裝置", 200))

        val result = manager().restore(tooNewRemote)

        assertTrue(result is RestoreResult.Failure)
        val stillLocal = openLibraryDb()
        assertEquals(1L, stillLocal.readSingleLong("SELECT COUNT(*) FROM shot"))
        stillLocal.close()
    }

    @Test
    fun 雜湊不符時拒絕_原本資料不動() = runTest {
        RoomLibraryRepo(libraryDb, Dispatchers.IO).commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0)),
        )
        val remote = uploadBackupWithShots(shotCount = 3)
        val corrupted = remote.copy(sha256 = "0".repeat(64))

        val result = manager().restore(corrupted)

        assertTrue(result is RestoreResult.Failure)
        val stillLocal = openLibraryDb()
        assertEquals(1L, stillLocal.readSingleLong("SELECT COUNT(*) FROM shot"))
        stillLocal.close()
    }

    /** 16A：舊版 app 做的備份（v1）還原後，Room 打開時自動升級到 v2。 */
    @Test
    fun 還原v1備份會自動升級() = runTest {
        val v1File = File(tmp.root, "v1-backup.db")
        com.xenyaa.videoshot.data.writeV1Library(v1File)
        val gz = File(tmp.root, "v1-backup.db.gz")
        val sha256 = BackupCodec.gzipWithSha256(v1File, gz)
        val remote = store.upload(NewBackup(gz, sha256, 1, 6, "舊手機", 100))

        val result = manager().restore(remote)

        assertTrue(result is RestoreResult.Success)
        val reopened = openLibraryDb()
        assertEquals(2L, reopened.readSingleLong("PRAGMA user_version"))
        assertEquals(6L, reopened.readSingleLong("SELECT COUNT(*) FROM shot"))
        assertEquals(6L, reopened.readSingleLong("SELECT cnt FROM shot_stat_total WHERE kind = 0 AND ref_id = 0"))
        reopened.close()
    }
}
