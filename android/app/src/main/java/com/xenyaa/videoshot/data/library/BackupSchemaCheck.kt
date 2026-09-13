package com.xenyaa.videoshot.data.library

enum class BackupCompat {
    /** 版本相同，直接換檔即可 */
    OK,

    /** 備份比 app 舊，換檔後由 Room 跑遷移 */
    NEEDS_MIGRATION,

    /** 備份比 app 新 —— 拒絕還原，提示先更新 app（規格第十節） */
    TOO_NEW,
}

/**
 * 拿備份檔的 PRAGMA user_version 跟 app 目前的 schema 版本比。
 * 之所以不能硬開比較新的備份：新版可能多了欄位或表，舊 app 讀不懂，
 * 一旦寫進去就把使用者的資料弄壞了，而且那是唯一一份。
 */
fun checkBackupSchema(backupUserVersion: Int): BackupCompat = when {
    backupUserVersion > LIBRARY_SCHEMA_VERSION -> BackupCompat.TOO_NEW
    backupUserVersion < LIBRARY_SCHEMA_VERSION -> BackupCompat.NEEDS_MIGRATION
    else -> BackupCompat.OK
}
