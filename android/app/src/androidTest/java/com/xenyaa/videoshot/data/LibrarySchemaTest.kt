package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LIBRARY_SCHEMA_VERSION
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySchemaTest {

    private lateinit var db: LibraryDatabase

    @Before fun setUp() { db = inMemoryLibraryDb() }
    @After fun tearDown() { db.close() }

    @Test
    fun 用的是自帶的_SQLite_而不是系統內建的() = runTest {
        val version = db.readSingleText("SELECT sqlite_version()")
        assertEquals("3.50", version.substringBeforeLast('.'))
    }

    @Test
    fun schema_版本是_1() {
        assertEquals(1, LIBRARY_SCHEMA_VERSION)
    }

    @Test
    fun video_存得進去也讀得回來() = runTest {
        val v = VideoEntity(
            id = "aqz-KE-bpKQ",
            title = "Big Buck Bunny",
            channelTitle = "Blender",
            publishedAt = "2014-11-10T06:05:55-08:00",
            durationSec = 635,
            privacy = "public",
            sbSpec = null,
            addedAt = 1_757_000_000L,
        )
        db.videoDao().upsert(v)
        assertEquals(v, db.videoDao().byId("aqz-KE-bpKQ"))
        assertNull(db.videoDao().byId("不存在"))
    }
}
