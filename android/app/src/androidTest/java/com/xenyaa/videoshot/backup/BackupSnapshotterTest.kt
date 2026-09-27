package com.xenyaa.videoshot.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.inMemoryLibraryDb
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSnapshotterTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: LibraryDatabase

    @Before fun setUp() { db = inMemoryLibraryDb() }
    @After fun tearDown() { db.close() }

    private fun pick(frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = "2026-09-27", place = null, description = null, webp = null,
    )

    @Test
    fun 快照檔可以被獨立開啟_內容跟原DB一致() = runTest {
        val repo = RoomLibraryRepo(db, Dispatchers.IO)
        repo.commitPicks(
            VideoEntity("v1", "片名", "頻道", "2026-09-27T10:00:00Z", 600, "public", null, 1L),
            listOf(pick(0), pick(1), pick(2)),
        )

        val dest = File(tmp.root, "snapshot.db")
        BackupSnapshotter(db, Dispatchers.IO).snapshotTo(dest)

        assertTrue(dest.exists())
        val driver = BundledSQLiteDriver()
        val connection = driver.open(dest.absolutePath)
        try {
            val integrityOk = connection.prepare("PRAGMA integrity_check").use { stmt ->
                stmt.step(); stmt.getText(0)
            }
            assertEquals("ok", integrityOk)
            val shotCount = connection.prepare("SELECT COUNT(*) FROM shot").use { stmt ->
                stmt.step(); stmt.getLong(0)
            }
            assertEquals(3L, shotCount)
        } finally {
            connection.close()
        }
    }

    @Test
    fun 目的檔已存在時會被覆蓋() = runTest {
        val dest = File(tmp.root, "snapshot.db").apply { writeText("stale") }
        BackupSnapshotter(db, Dispatchers.IO).snapshotTo(dest)
        assertTrue(dest.length() > 5L) // 真的被换成一個 SQLite 檔案，不是留著舊的 5 個位元組
    }
}
