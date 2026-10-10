package com.xenyaa.videoshot.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.data.cache.CACHE_MIGRATIONS
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.model.FacetRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
}
