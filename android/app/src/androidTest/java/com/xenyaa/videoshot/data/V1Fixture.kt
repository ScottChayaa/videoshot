package com.xenyaa.videoshot.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File

/**
 * 寫出一份 **v1 格式**的 library.db（階段 16A 之前的結構：地點是 `shot.place` 文字、全文索引含地點）。
 * 建表語句照抄 `schemas/.../LibraryDatabase/1.json`，`room_master_table` 的 identity hash 也是 v1 的，
 * Room 打開時會判斷成「舊版、要跑遷移」。
 *
 * 內容：2 支影片、6 張圖（地點：宜蘭×2、花蓮×2、空白×1、null×1）、3 個標籤、1 個資料夾、1 張手動圖。
 */
fun writeV1Library(file: File) {
    listOf("", "-wal", "-shm").forEach { File(file.path + it).delete() }
    val c = BundledSQLiteDriver().open(file.path)
    try {
        listOf(
            "CREATE TABLE IF NOT EXISTS `video` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `channel_title` TEXT NOT NULL, `published_at` TEXT NOT NULL, `duration_sec` INTEGER NOT NULL, `privacy` TEXT NOT NULL, `sb_spec` TEXT, `added_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `shot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `video_id` TEXT NOT NULL, `at_sec` REAL NOT NULL, `source` TEXT NOT NULL, `frame_index` INTEGER, `sb_level` INTEGER, `event_date` TEXT NOT NULL, `place` TEXT, `description` TEXT, `ai_transcript` TEXT, `ai_visual_desc` TEXT, `ai_raw` TEXT, `created_at` INTEGER NOT NULL, FOREIGN KEY(`video_id`) REFERENCES `video`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_shot_event_date_id` ON `shot` (`event_date`, `id`)",
            "CREATE INDEX IF NOT EXISTS `index_shot_video_id` ON `shot` (`video_id`)",
            "CREATE INDEX IF NOT EXISTS `index_shot_place` ON `shot` (`place`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_shot_video_id_frame_index` ON `shot` (`video_id`, `frame_index`)",
            "CREATE TABLE IF NOT EXISTS `shot_image` (`shot_id` INTEGER NOT NULL, `webp` BLOB NOT NULL, PRIMARY KEY(`shot_id`), FOREIGN KEY(`shot_id`) REFERENCES `shot`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `tag` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `aliases` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)",
            "CREATE TABLE IF NOT EXISTS `shot_tag` (`shot_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`shot_id`, `tag_id`), FOREIGN KEY(`shot_id`) REFERENCES `shot`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tag_id`) REFERENCES `tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_shot_tag_tag_id` ON `shot_tag` (`tag_id`)",
            "CREATE INDEX IF NOT EXISTS `index_shot_tag_shot_id` ON `shot_tag` (`shot_id`)",
            "CREATE TABLE IF NOT EXISTS `folder` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `parent_id` INTEGER, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`parent_id`) REFERENCES `folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_folder_parent_id` ON `folder` (`parent_id`)",
            "CREATE TABLE IF NOT EXISTS `shot_folder` (`shot_id` INTEGER NOT NULL, `folder_id` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`shot_id`, `folder_id`), FOREIGN KEY(`shot_id`) REFERENCES `shot`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`folder_id`) REFERENCES `folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_shot_folder_folder_id_added_at` ON `shot_folder` (`folder_id`, `added_at`)",
            "CREATE INDEX IF NOT EXISTS `index_shot_folder_shot_id` ON `shot_folder` (`shot_id`)",
            // v1 的全文索引（含地點）與三個觸發器
            "CREATE VIRTUAL TABLE IF NOT EXISTS shot_fts USING fts5(description, place, content='shot', content_rowid='id', tokenize='trigram')",
            "CREATE TRIGGER IF NOT EXISTS shot_fts_after_insert AFTER INSERT ON shot BEGIN INSERT INTO shot_fts(rowid, description, place) VALUES (new.id, new.description, new.place); END",
            "CREATE TRIGGER IF NOT EXISTS shot_fts_after_delete AFTER DELETE ON shot BEGIN INSERT INTO shot_fts(shot_fts, rowid, description, place) VALUES ('delete', old.id, old.description, old.place); END",
            "CREATE TRIGGER IF NOT EXISTS shot_fts_after_update AFTER UPDATE ON shot BEGIN INSERT INTO shot_fts(shot_fts, rowid, description, place) VALUES ('delete', old.id, old.description, old.place); INSERT INTO shot_fts(rowid, description, place) VALUES (new.id, new.description, new.place); END",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a47035aa76d7911ded6979e99a1c7e4e')",
            // 資料
            "INSERT INTO video VALUES ('v1', 't', 'c', '2026-01-01T00:00:00Z', 600, 'public', NULL, 1)",
            "INSERT INTO video VALUES ('v2', 't', 'c', '2026-01-01T00:00:00Z', 600, 'public', NULL, 1)",
            "INSERT INTO shot VALUES (1, 'v1', 0, 'storyboard', 0, 3, '2026-01-05', '宜蘭', '加勒比海夜潛看到的大蝦', NULL, NULL, NULL, 1)",
            "INSERT INTO shot VALUES (2, 'v1', 1, 'storyboard', 1, 3, '2026-01-06', '宜蘭', NULL, NULL, NULL, NULL, 1)",
            "INSERT INTO shot VALUES (3, 'v1', 2, 'storyboard', 2, 3, '2026-02-01', '花蓮', '海邊玩水', NULL, NULL, NULL, 1)",
            "INSERT INTO shot VALUES (4, 'v2', 0, 'storyboard', 0, 3, '2026-02-02', '花蓮', NULL, NULL, NULL, NULL, 1)",
            "INSERT INTO shot VALUES (5, 'v2', 1, 'storyboard', 1, 3, '2026-03-01', '', NULL, NULL, NULL, NULL, 1)",
            "INSERT INTO shot VALUES (6, 'v2', 2.5, 'manual', NULL, NULL, '2026-03-02', NULL, NULL, NULL, NULL, NULL, 1)",
            "INSERT INTO shot_image VALUES (6, x'010203')",
            "INSERT INTO tag VALUES (1, '露營', 'topic', '[]')",
            "INSERT INTO tag VALUES (2, '阿明', 'person', '[\"明哥\"]')",
            "INSERT INTO tag VALUES (3, '美食', 'topic', '[]')",
            "INSERT INTO shot_tag VALUES (1, 1, 'human')",
            "INSERT INTO shot_tag VALUES (1, 2, 'human')",
            "INSERT INTO shot_tag VALUES (3, 2, 'human')",
            "INSERT INTO shot_tag VALUES (4, 3, 'human')",
            "INSERT INTO folder VALUES (1, NULL, '旅行', 1)",
            "INSERT INTO shot_folder VALUES (1, 1, 1)",
            "INSERT INTO shot_folder VALUES (3, 1, 2)",
            "PRAGMA user_version = 1",
        ).forEach { c.execSQL(it) }
    } finally {
        c.close()
    }
}
