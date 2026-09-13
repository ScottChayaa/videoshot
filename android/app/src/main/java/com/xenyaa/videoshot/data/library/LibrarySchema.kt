package com.xenyaa.videoshot.data.library

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * library.db 的 schema 版本。**這是跨平台的資料契約**（規格第四節）——
 * 動到任何一張表的欄位就要 +1 並補上 migration，還原比這個版本新的備份必須拒絕。
 */
const val LIBRARY_SCHEMA_VERSION = 1

/**
 * shot_fts 是 FTS5 的 external-content 虛擬表（內容仍在 shot，不重複存一份），
 * tokenizer 用 trigram —— 中文沒有空白分詞，trigram 是唯一可行的選擇（規格第二節第 7 點）。
 * Room 的 @Fts4 不支援 FTS5，所以整組用原生 SQL 建，並在 onCreate 與 migration 兩處共用這份清單。
 *
 * 觸發器用 external-content 表的標準寫法：刪除要先送一筆 'delete' 指令列把舊內容退出索引，
 * 否則索引會留下對不上內容表的殘骸。
 */
val FTS_SETUP_SQL: List<String> = listOf(
    """
    CREATE VIRTUAL TABLE IF NOT EXISTS shot_fts USING fts5(
        description,
        place,
        content='shot',
        content_rowid='id',
        tokenize='trigram'
    )
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS shot_fts_after_insert AFTER INSERT ON shot BEGIN
        INSERT INTO shot_fts(rowid, description, place) VALUES (new.id, new.description, new.place);
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS shot_fts_after_delete AFTER DELETE ON shot BEGIN
        INSERT INTO shot_fts(shot_fts, rowid, description, place)
        VALUES ('delete', old.id, old.description, old.place);
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS shot_fts_after_update AFTER UPDATE ON shot BEGIN
        INSERT INTO shot_fts(shot_fts, rowid, description, place)
        VALUES ('delete', old.id, old.description, old.place);
        INSERT INTO shot_fts(rowid, description, place) VALUES (new.id, new.description, new.place);
    END
    """.trimIndent(),
)

/** 建立與開啟 library.db 時，補上 Room 管不到的東西。 */
object LibrarySchemaCallback : RoomDatabase.Callback() {

    /**
     * onCreate 在 Room 建完所有實體表之後才呼叫，所以 shot 這時已經存在
     * （external-content 的 FTS 表需要內容表先在）。
     */
    override fun onCreate(connection: SQLiteConnection) {
        FTS_SETUP_SQL.forEach { connection.execSQL(it) }
    }

    override fun onOpen(connection: SQLiteConnection) {
        // 外鍵預設是關的；不打開的話 ON DELETE CASCADE 形同虛設
        connection.execSQL("PRAGMA foreign_keys = ON")
    }
}
