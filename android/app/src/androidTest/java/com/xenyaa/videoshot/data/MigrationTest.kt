package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.BackupCompat
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LIBRARY_SCHEMA_VERSION
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.checkBackupSchema
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private lateinit var db: LibraryDatabase

    @Before fun setUp() { db = inMemoryLibraryDb() }
    @After fun tearDown() { db.close() }

    @Test
    fun 新建的_DB_的_user_version_等於_schema_版本() = runTest {
        assertEquals(LIBRARY_SCHEMA_VERSION.toLong(), db.readSingleLong("PRAGMA user_version"))
    }

    @Test
    fun migration_陣列涵蓋從_1_到現行版本的每一階() {
        assertEquals(LIBRARY_SCHEMA_VERSION - 1, LIBRARY_MIGRATIONS.size)
    }

    @Test
    fun 比_app_舊的備份要跑遷移() {
        assertEquals(BackupCompat.NEEDS_MIGRATION, checkBackupSchema(LIBRARY_SCHEMA_VERSION - 1))
    }

    @Test
    fun 同版本的備份直接可用() {
        assertEquals(BackupCompat.OK, checkBackupSchema(LIBRARY_SCHEMA_VERSION))
    }

    @Test
    fun 比_app_新的備份要拒絕() {
        assertEquals(BackupCompat.TOO_NEW, checkBackupSchema(LIBRARY_SCHEMA_VERSION + 1))
    }

    @Test
    fun 完整性檢查通過() = runTest {
        assertEquals("ok", db.readSingleText("PRAGMA integrity_check"))
    }
}
