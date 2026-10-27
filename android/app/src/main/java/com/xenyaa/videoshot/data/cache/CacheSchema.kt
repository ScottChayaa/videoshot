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

/** v2→v3：新增 `edit_draft`（剪輯草稿，永遠一列）。其餘三張表不動。SQL 要跟 Room 依 [com.xenyaa.videoshot.data.cache.entity.EditDraftEntity] 產生的一字不差，原因同上。 */
val CACHE_MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `edit_draft` (`orientation` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, `id` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

/** v3→v4：新增 `edit_task`（剪輯輸出任務）。其餘表不動。SQL 照抄 `4.json`，原因同上。 */
val CACHE_MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `edit_task` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `state` TEXT NOT NULL, " +
                "`progress` INTEGER NOT NULL, `title` TEXT NOT NULL, `orientation` TEXT NOT NULL, " +
                "`duration_ms` INTEGER NOT NULL, `payload` TEXT NOT NULL, `auto_upload` INTEGER NOT NULL, " +
                "`fail_reason` TEXT, `skipped_sources` INTEGER NOT NULL, `gallery_uri` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)"
        )
    }
}

/** cache.db 的遷移清單。它不進備份，沒有備份相容性的問題（規格第四節）。 */
val CACHE_MIGRATIONS: Array<Migration> = arrayOf(CACHE_MIGRATION_1_2, CACHE_MIGRATION_2_3, CACHE_MIGRATION_3_4)
