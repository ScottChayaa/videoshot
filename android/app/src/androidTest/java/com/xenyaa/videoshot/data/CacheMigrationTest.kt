package com.xenyaa.videoshot.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.data.cache.CACHE_MIGRATIONS
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.model.FacetRef
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

/** 真的拿一份 v1 的 cache.db，用正式的 Room 設定打開：縮圖回填狀態與草稿一筆不少，新表可以用。 */
@RunWith(AndroidJUnit4::class)
class CacheMigrationTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: CacheDatabase

    @Before fun setUp() {
        val file = File(tmp.root, "cache.db")
        // v1 的建表 SQL 一字不差取自 app/schemas/com.xenyaa.videoshot.data.cache.CacheDatabase/1.json
        BundledSQLiteDriver().open(file.path).let { c ->
            try {
                c.execSQL("CREATE TABLE IF NOT EXISTS `thumb_state` (`video_id` TEXT NOT NULL, `sb_level` INTEGER NOT NULL, `frame_index` INTEGER NOT NULL, `state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `next_try_at` INTEGER NOT NULL, `lost_reason` TEXT, PRIMARY KEY(`video_id`, `sb_level`, `frame_index`))")
                c.execSQL("CREATE TABLE IF NOT EXISTS `draft` (`video_id` TEXT NOT NULL, `step` INTEGER NOT NULL, `payload` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, `id` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                c.execSQL("INSERT INTO thumb_state VALUES ('v1', 3, 0, 'missing', 2, 50, NULL)")
                c.execSQL("INSERT INTO draft VALUES ('v1', 2, '{}', 100, 1)")
                c.execSQL("PRAGMA user_version = 1")
            } finally {
                c.close()
            }
        }
        db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CacheDatabase::class.java, file.path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*CACHE_MIGRATIONS)
            .build()
    }

    @After fun tearDown() { db.close() }

    @Test fun 舊資料一筆不少_新表可以寫() = runTest {
        val repo = RoomCacheRepo(db, Dispatchers.IO)
        assertEquals(2, repo.thumbState("v1", 3, 0)!!.attempts)
        assertEquals("v1", repo.currentDraft()!!.videoId)
        repo.touchFacets(listOf(FacetRef(1, 7)), usedAt = 123)
        assertEquals(mapOf(FacetRef(1, 7) to 123L), repo.facetRecent())
    }

    @Test fun 二升三新增剪輯草稿表而且舊資料還在() = runTest {
        val file = File(tmp.root, "cache-v2.db")
        // v2 的建表 SQL 一字不差取自 app/schemas/com.xenyaa.videoshot.data.cache.CacheDatabase/2.json
        BundledSQLiteDriver().open(file.path).let { c ->
            try {
                c.execSQL("CREATE TABLE IF NOT EXISTS `thumb_state` (`video_id` TEXT NOT NULL, `sb_level` INTEGER NOT NULL, `frame_index` INTEGER NOT NULL, `state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `next_try_at` INTEGER NOT NULL, `lost_reason` TEXT, PRIMARY KEY(`video_id`, `sb_level`, `frame_index`))")
                c.execSQL("CREATE TABLE IF NOT EXISTS `draft` (`video_id` TEXT NOT NULL, `step` INTEGER NOT NULL, `payload` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, `id` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                c.execSQL("CREATE TABLE IF NOT EXISTS `facet_recent` (`kind` INTEGER NOT NULL, `ref_id` INTEGER NOT NULL, `used_at` INTEGER NOT NULL, PRIMARY KEY(`kind`, `ref_id`))")
                c.execSQL("INSERT INTO draft VALUES ('v2', 3, '{}', 100, 1)")
                c.execSQL("INSERT INTO facet_recent VALUES (1, 7, 456)")
                c.execSQL("PRAGMA user_version = 2")
            } finally {
                c.close()
            }
        }
        val v2db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CacheDatabase::class.java, file.path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*CACHE_MIGRATIONS)
            .build()
        try {
            val repo = RoomCacheRepo(v2db, Dispatchers.IO)
            assertEquals("v2", repo.currentDraft()!!.videoId)
            assertEquals(mapOf(FacetRef(1, 7) to 456L), repo.facetRecent())
            // 新表可以寫可以讀
            assertNull(repo.currentClipDraft())
            val draft = newClipDraft(
                ClipOrientation.LANDSCAPE,
                listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1000, null, 1080, 1920, 0)),
            )
            repo.saveClipDraft(draft, now = 9)
            assertEquals(draft, repo.currentClipDraft())
        } finally {
            v2db.close()
        }
    }
}
