package com.xenyaa.videoshot.data.library

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * library.db 的 schema 版本。**這是跨平台的資料契約**（規格第四節）——
 * 動到任何一張表的欄位就要 +1 並補上 migration，還原比這個版本新的備份必須拒絕。
 */
const val LIBRARY_SCHEMA_VERSION = 2

/**
 * shot_fts 是 FTS5 的 external-content 虛擬表（內容仍在 shot，不重複存一份），
 * tokenizer 用 trigram —— 中文沒有空白分詞，trigram 是唯一可行的選擇（規格第二節第 7 點）。
 * Room 的 @Fts4 不支援 FTS5，所以整組用原生 SQL 建，並在 onCreate 與 migration 兩處共用這份清單。
 *
 * 觸發器用 external-content 表的標準寫法：刪除要先送一筆 'delete' 指令列把舊內容退出索引，
 * 否則索引會留下對不上內容表的殘骸。
 *
 * 只索引 `description`：地點在 `place` 表，不進全文索引（規格第八節）。
 */
val FTS_SETUP_SQL: List<String> = listOf(
    """
    CREATE VIRTUAL TABLE IF NOT EXISTS shot_fts USING fts5(
        description,
        content='shot',
        content_rowid='id',
        tokenize='trigram'
    )
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS shot_fts_after_insert AFTER INSERT ON shot BEGIN
        INSERT INTO shot_fts(rowid, description) VALUES (new.id, new.description);
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS shot_fts_after_delete AFTER DELETE ON shot BEGIN
        INSERT INTO shot_fts(shot_fts, rowid, description) VALUES ('delete', old.id, old.description);
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS shot_fts_after_update AFTER UPDATE OF description ON shot BEGIN
        INSERT INTO shot_fts(shot_fts, rowid, description) VALUES ('delete', old.id, old.description);
        INSERT INTO shot_fts(rowid, description) VALUES (new.id, new.description);
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
        STATS_SETUP_SQL.forEach { connection.execSQL(it) }
    }

    override fun onOpen(connection: SQLiteConnection) {
        // 外鍵預設是關的；不打開的話 ON DELETE CASCADE 形同虛設
        connection.execSQL("PRAGMA foreign_keys = ON")
    }
}

/**
 * v1 → v2（階段 16A）：地點改成 `place` 表、全文索引只索引描述、新增統計表。
 *
 * **不重建 `shot` 表**：`DROP TABLE shot` 會先做一次隱含的 DELETE，外鍵連動會把 `shot_tag`、
 * `shot_folder`、`shot_image` 全部刪光。改用 `ADD COLUMN`＋`DROP COLUMN`（SQLite 3.35+），
 * 拿掉 `place` 欄位前要先拿掉引用它的索引與全文索引觸發器。
 * `CREATE TABLE place` 必須跟 Room 產生的語句一字不差（見 `schemas/.../2.json`），否則 Room 開檔驗證會失敗。
 */
val MIGRATION_1_2_SQL: List<String> = listOf(
    "DROP TRIGGER IF EXISTS shot_fts_after_insert",
    "DROP TRIGGER IF EXISTS shot_fts_after_delete",
    "DROP TRIGGER IF EXISTS shot_fts_after_update",
    "DROP TABLE IF EXISTS shot_fts",
    "CREATE TABLE IF NOT EXISTS `place` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `aliases` TEXT NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_place_name` ON `place` (`name`)",
    "INSERT INTO place (name, aliases) SELECT DISTINCT place, '[]' FROM shot WHERE place IS NOT NULL AND trim(place) <> ''",
    "ALTER TABLE shot ADD COLUMN `place_id` INTEGER REFERENCES `place`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
    "UPDATE shot SET place_id = (SELECT id FROM place WHERE place.name = shot.place) WHERE place IS NOT NULL",
    "DROP INDEX IF EXISTS `index_shot_place`",
    "ALTER TABLE shot DROP COLUMN place",
    "CREATE INDEX IF NOT EXISTS `index_shot_place_id` ON `shot` (`place_id`)",
) + FTS_SETUP_SQL + "INSERT INTO shot_fts(shot_fts) VALUES ('rebuild')" + STATS_SETUP_SQL + STATS_REBUILD_SQL

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        MIGRATION_1_2_SQL.forEach { connection.execSQL(it) }
    }
}

/**
 * library.db 的遷移清單。接線由 MigrationTest 盯著版本與陣列對得上。
 *
 * 遷移裡若重建 `shot` 表，必須一併重建 `shot_fts`、它的三個觸發器與統計表的觸發器（`StatsSchema.kt`）。
 */
val LIBRARY_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
