package com.xenyaa.videoshot.data.cache

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v1→v2：新增 `facet_recent`（篩選候選的最近使用時間）。`thumb_state` 與 `draft` 不動。SQL 要跟 Room 依 [com.xenyaa.videoshot.data.cache.entity.FacetRecentEntity] 產生的一字不差，否則開檔驗證會失敗。 */
val CACHE_MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `facet_recent` (`kind` INTEGER NOT NULL, `ref_id` INTEGER NOT NULL, " +
                "`used_at` INTEGER NOT NULL, PRIMARY KEY(`kind`, `ref_id`))"
        )
    }
}

/** cache.db 的遷移清單。它不進備份，沒有備份相容性的問題（規格第四節）。 */
val CACHE_MIGRATIONS: Array<Migration> = arrayOf(CACHE_MIGRATION_1_2)
