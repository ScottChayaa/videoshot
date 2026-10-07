package com.xenyaa.videoshot.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** 真的拿一份 v1 檔案，用正式的 Room 設定打開，驗證遷移後資料一筆不少、結構正確。 */
@RunWith(AndroidJUnit4::class)
class MigrationFromV1Test {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: LibraryDatabase

    @Before fun setUp() {
        file = File(tmp.root, "library.db")
        writeV1Library(file)
        db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LibraryDatabase::class.java, file.path)
            .setDriver(BundledSQLiteDriver())
            .addCallback(LibrarySchemaCallback)
            .addMigrations(*LIBRARY_MIGRATIONS)
            .build()
    }

    @After fun tearDown() { db.close() }

    @Test fun 升到版本_2_完整性與外鍵都沒問題() = runTest {
        assertEquals(2L, db.readSingleLong("PRAGMA user_version"))
        assertEquals("ok", db.readSingleText("PRAGMA integrity_check"))
        assertEquals(emptyList<String>(), db.readAllText("SELECT \"table\" FROM pragma_foreign_key_check"))
    }

    @Test fun 圖_標籤_資料夾_手動圖一筆不少() = runTest {
        assertEquals(6L, db.readSingleLong("SELECT COUNT(*) FROM shot"))
        assertEquals(4L, db.readSingleLong("SELECT COUNT(*) FROM shot_tag"))
        assertEquals(2L, db.readSingleLong("SELECT COUNT(*) FROM shot_folder"))
        assertEquals(1L, db.readSingleLong("SELECT COUNT(*) FROM shot_image"))
        assertEquals("[\"明哥\"]", db.readSingleText("SELECT aliases FROM tag WHERE name = '阿明'"))
    }

    @Test fun 地點搬進地點表_空白與null都變成沒有地點() = runTest {
        assertEquals(listOf("宜蘭", "花蓮"), db.readAllText("SELECT name FROM place ORDER BY name"))
        val repo = RoomLibraryRepo(db, Dispatchers.IO)
        assertEquals("宜蘭", repo.shotById(1)!!.place)
        assertEquals("花蓮", repo.shotById(4)!!.place)
        assertNull(repo.shotById(5)!!.place)
        assertNull(repo.shotById(6)!!.place)
        assertEquals(0L, db.readSingleLong("SELECT COUNT(*) FROM pragma_table_info('shot') WHERE name = 'place'"))
    }

    @Test fun 全文索引重建且只索引描述() = runTest {
        assertEquals(listOf(1L), db.searchDao().matchIds("\"夜潛看\""))
        assertEquals(listOf(3L), db.searchDao().matchIds("\"海邊玩\""))
        assertEquals(emptyList<Long>(), db.searchDao().matchIds("\"宜蘭\""))
    }

    @Test fun 統計表算好了() = runTest {
        // 2026-01：全部 2、宜蘭 2、露營 1、阿明 1；2026-02：全部 2、花蓮 2、阿明 1、美食 1；2026-03：全部 2
        assertEquals(
            listOf("2026-01|0|2", "2026-02|0|2", "2026-03|0|2"),
            db.readAllText("SELECT month || '|' || kind || '|' || cnt FROM shot_stat WHERE kind = 0 AND cnt > 0 ORDER BY month"),
        )
        assertEquals(6L, db.readSingleLong("SELECT cnt FROM shot_stat_total WHERE kind = 0 AND ref_id = 0"))
        assertEquals(2L, db.readSingleLong("SELECT cnt FROM shot_stat_total WHERE kind = 2 AND ref_id = 2"))
        assertEquals(2L, db.readSingleLong(
            "SELECT t.cnt FROM shot_stat_total t JOIN place p ON p.id = t.ref_id WHERE t.kind = 1 AND p.name = '花蓮'"
        ))
    }

    @Test fun 升級後照常寫入_統計跟著變() = runTest {
        val repo = RoomLibraryRepo(db, Dispatchers.IO)
        repo.deleteShot(1) // 宜蘭、露營、阿明各一
        assertEquals(5L, db.readSingleLong("SELECT cnt FROM shot_stat_total WHERE kind = 0 AND ref_id = 0"))
        assertEquals(0L, db.readSingleLong("SELECT cnt FROM shot_stat_total WHERE kind = 2 AND ref_id = 1"))
        assertEquals(1L, db.readSingleLong("SELECT cnt FROM shot_stat_total WHERE kind = 2 AND ref_id = 2"))
    }
}
