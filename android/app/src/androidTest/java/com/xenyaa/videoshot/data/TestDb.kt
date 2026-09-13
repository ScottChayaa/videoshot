package com.xenyaa.videoshot.data

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.LIBRARY_MIGRATIONS
import com.xenyaa.videoshot.data.library.LibrarySchemaCallback

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
        .build()
