package com.xenyaa.videoshot.data

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.data.cache.CACHE_MIGRATIONS
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback
import com.xenyaa.videoshot.data.library.entity.PlaceEntity

/** 每個測試自己開一個 in-memory 的 library.db，彼此不互相污染。 */
fun inMemoryLibraryDb(): LibraryDatabase =
    Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        LibraryDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .addCallback(LibrarySchemaCallback)
        .addMigrations(*LIBRARY_MIGRATIONS)
        .build()

// usePrepared 是 androidx.room.PooledConnection 的成員（Transactor 繼承它），不必 import。
suspend fun LibraryDatabase.readSingleText(sql: String): String =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { stmt -> stmt.step(); stmt.getText(0) }
    }

suspend fun LibraryDatabase.readSingleLong(sql: String): Long =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { stmt -> stmt.step(); stmt.getLong(0) }
    }

suspend fun LibraryDatabase.readAllText(sql: String): List<String> =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { stmt ->
            buildList { while (stmt.step()) add(stmt.getText(0)) }
        }
    }

fun inMemoryCacheDb(): CacheDatabase =
    Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        CacheDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*CACHE_MIGRATIONS)
        .build()

/** 測試用：地點名稱轉 id，不存在就建（正式程式走 `RoomLibraryRepo.placeIdForWrite`，同一套規則）。null／空白回 null。 */
suspend fun LibraryDatabase.placeIdOf(name: String?): Long? {
    if (name.isNullOrBlank()) return null
    return placeDao().byName(name)?.id ?: placeDao().insert(PlaceEntity(0, name, "[]"))
}

/** 測試用：在寫入連線上執行一句 SQL。 */
suspend fun LibraryDatabase.execOnWriter(sql: String) =
    useWriterConnection { it.usePrepared(sql) { stmt -> stmt.step() } }

/** 測試用：統計表（每月明細與總數）必須等於從 shot／shot_tag 獨立重數的結果，且沒有負數。 */
suspend fun LibraryDatabase.assertStatsMatchRecount() {
    val truth = """
        SELECT substr(event_date, 1, 7) AS m, 0 AS k, 0 AS r, COUNT(*) AS n FROM shot GROUP BY 1
        UNION ALL
        SELECT substr(event_date, 1, 7), 1, place_id, COUNT(*) FROM shot WHERE place_id IS NOT NULL GROUP BY 1, 3
        UNION ALL
        SELECT substr(s.event_date, 1, 7), 2, st.tag_id, COUNT(*) FROM shot_tag st JOIN shot s ON s.id = st.shot_id GROUP BY 1, 3
    """
    val expected = readAllText("SELECT m || '|' || k || '|' || r || '|' || n FROM ($truth) ORDER BY 1")
    val actual = readAllText("SELECT month || '|' || kind || '|' || ref_id || '|' || cnt FROM shot_stat WHERE cnt <> 0 ORDER BY 1")
    org.junit.Assert.assertEquals("每月明細", expected, actual)

    val expectedTotal = readAllText("SELECT k || '|' || r || '|' || SUM(n) FROM ($truth) GROUP BY k, r ORDER BY 1")
    val actualTotal = readAllText("SELECT kind || '|' || ref_id || '|' || cnt FROM shot_stat_total WHERE cnt <> 0 ORDER BY 1")
    org.junit.Assert.assertEquals("總數", expectedTotal, actualTotal)

    org.junit.Assert.assertEquals("不能有負數", 0L, readSingleLong(
        "SELECT (SELECT COUNT(*) FROM shot_stat WHERE cnt < 0) + (SELECT COUNT(*) FROM shot_stat_total WHERE cnt < 0)"
    ))
}
