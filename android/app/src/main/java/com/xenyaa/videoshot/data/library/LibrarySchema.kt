package com.xenyaa.videoshot.data.library

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * library.db 的 schema 版本。**這是跨平台的資料契約**（規格第四節）——
 * 動到任何一張表的欄位就要 +1 並補上 migration，還原比這個版本新的備份必須拒絕。
 */
const val LIBRARY_SCHEMA_VERSION = 1

/** 建立與開啟 library.db 時，補上 Room 管不到的設定。 */
object LibrarySchemaCallback : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        // 外鍵預設是關的；不打開的話 ON DELETE CASCADE 形同虛設
        connection.execSQL("PRAGMA foreign_keys = ON")
    }
}
