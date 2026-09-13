package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LIBRARY_SCHEMA_VERSION
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.FolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.ShotFolderEntity
import com.xenyaa.videoshot.data.library.entity.ShotImageEntity
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private suspend fun seedVideo(id: String = "aqz-KE-bpKQ") {
        db.videoDao().upsert(
            VideoEntity(id, "t", "c", "2014-11-10T06:05:55-08:00", 635, "public", null, 1L)
        )
    }

    private fun storyboardShot(videoId: String = "aqz-KE-bpKQ", frameIndex: Int = 0) =
        ShotEntity(
            id = 0, videoId = videoId, atSec = frameIndex.toDouble(), source = "storyboard",
            frameIndex = frameIndex, sbLevel = 3, eventDate = "2014-11-10",
            place = null, description = null,
            aiTranscript = null, aiVisualDesc = null, aiRaw = null, createdAt = 1L,
        )

    @Test
    fun 同一支影片的同一格不能收藏兩次() = runTest {
        seedVideo()
        db.shotDao().insert(storyboardShot(frameIndex = 7))
        val again = runCatching { db.shotDao().insert(storyboardShot(frameIndex = 7)) }
        assertTrue(again.isFailure)
    }

    @Test
    fun 手動補圖不受去重限制() = runTest {
        seedVideo()
        val manual = storyboardShot().copy(source = "manual", frameIndex = null, sbLevel = null, atSec = 12.5)
        db.shotDao().insert(manual)
        db.shotDao().insert(manual)
        assertEquals(2, db.shotDao().countOfVideo("aqz-KE-bpKQ"))
    }

    @Test
    fun 刪掉_shot_會連帶刪掉它的手動圖與所有關聯() = runTest {
        seedVideo()
        val shotId = db.shotDao().insert(storyboardShot(frameIndex = 1))
        db.shotDao().putImage(ShotImageEntity(shotId, byteArrayOf(1, 2, 3)))
        val tagId = db.tagDao().insert(TagEntity(0, "露營", "topic", "[]"))
        db.tagDao().link(ShotTagEntity(shotId, tagId, "human"))
        val folderId = db.folderDao().insert(FolderEntity(0, null, "2014 秋", 1L))
        db.folderDao().link(ShotFolderEntity(shotId, folderId, 1L))

        db.shotDao().deleteById(shotId)

        assertNull(db.shotDao().imageOf(shotId))
        assertEquals(0, db.tagDao().linkCountOfShot(shotId))
        assertEquals(0, db.folderDao().linkCountOfShot(shotId))
        assertEquals(1, db.tagDao().count())
        assertEquals(1, db.folderDao().count())
    }

    @Test
    fun 刪掉資料夾會連帶刪掉子資料夾與關聯但不動到圖() = runTest {
        seedVideo()
        val shotId = db.shotDao().insert(storyboardShot(frameIndex = 2))
        val parent = db.folderDao().insert(FolderEntity(0, null, "旅行", 1L))
        val child = db.folderDao().insert(FolderEntity(0, parent, "埃及", 1L))
        db.folderDao().link(ShotFolderEntity(shotId, child, 1L))

        db.folderDao().deleteById(parent)

        assertEquals(0, db.folderDao().count())
        assertEquals(0, db.folderDao().linkCountOfShot(shotId))
        assertEquals(1, db.shotDao().countOfVideo("aqz-KE-bpKQ"))
    }

    @Test
    fun 標籤名稱不可重複() = runTest {
        db.tagDao().insert(TagEntity(0, "露營", "topic", "[]"))
        val again = runCatching { db.tagDao().insert(TagEntity(0, "露營", "other", "[]")) }
        assertTrue(again.isFailure)
    }

    @Test
    fun 規格第四節列的索引全部都在() = runTest {
        val names = db.readAllText(
            "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%'"
        ).toSet()
        assertTrue(
            names.toString(),
            names.containsAll(
                listOf(
                    "index_shot_event_date_id",
                    "index_shot_video_id",
                    "index_shot_place",
                    "index_shot_video_id_frame_index",
                    "index_shot_tag_tag_id",
                    "index_shot_tag_shot_id",
                    "index_folder_parent_id",
                    "index_shot_folder_folder_id_added_at",
                    "index_shot_folder_shot_id",
                )
            )
        )
    }
}
