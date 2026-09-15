package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepoWriteTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private val video = VideoEntity("v1", "片名", "頻道", "2026-02-02T10:00:00Z", 600, "public", null, 1L)

    private fun storyboardPick(frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = "2026-02-02", place = null, description = null, webp = null,
    )

    @Test
    fun 入庫會一起建立影片列() = runTest {
        repo.commitPicks(video, listOf(storyboardPick(0), storyboardPick(1)))
        assertNotNull(db.videoDao().byId("v1"))
        assertEquals(2, db.shotDao().countOfVideo("v1"))
    }

    @Test
    fun 手動補圖的_webp_存進_shot_image() = runTest {
        val manual = NewShot(12.5, "manual", null, null, "2026-02-02", null, null, byteArrayOf(7, 7, 7))
        val ids = repo.commitPicks(video, listOf(manual))
        assertTrue(byteArrayOf(7, 7, 7).contentEquals(db.shotDao().imageOf(ids.single())!!.webp))
    }

    @Test
    fun 整批入庫是一個交易_中途失敗不留半套() = runTest {
        repo.commitPicks(video, listOf(storyboardPick(3)))
        val failed = runCatching {
            repo.commitPicks(video, listOf(storyboardPick(4), storyboardPick(3), storyboardPick(5)))
        }
        assertTrue(failed.isFailure)
        assertEquals(1, db.shotDao().countOfVideo("v1"))
    }

    @Test
    fun 只套用動過的欄位_沒動過的不被覆蓋() = runTest {
        val ids = repo.commitPicks(
            video,
            listOf(storyboardPick(0).copy(place = "宜蘭", description = "原本的描述")),
        )
        repo.patchShots(ids, ShotPatch(eventDate = null, place = "花蓮", description = null, tagIds = null))
        val row = repo.shotById(ids.single())!!
        assertEquals("花蓮", row.place)
        assertEquals("原本的描述", row.description)
    }

    @Test
    fun 標籤是整組覆蓋() = runTest {
        val ids = repo.commitPicks(video, listOf(storyboardPick(0)))
        val a = db.tagDao().insert(TagEntity(0, "露營", "topic", "[]"))
        val b = db.tagDao().insert(TagEntity(0, "夜潛", "topic", "[]"))
        repo.patchShots(ids, ShotPatch(null, null, null, tagIds = listOf(a, b)))
        assertEquals(2, db.tagDao().linkCountOfShot(ids.single()))
        repo.patchShots(ids, ShotPatch(null, null, null, tagIds = listOf(a)))
        assertEquals(1, db.tagDao().linkCountOfShot(ids.single()))
    }

    @Test
    fun 刪掉最後一張會順帶刪掉影片列() = runTest {
        val ids = repo.commitPicks(video, listOf(storyboardPick(0), storyboardPick(1)))
        repo.deleteShot(ids[0])
        assertNotNull(db.videoDao().byId("v1"))
        repo.deleteShot(ids[1])
        assertNull(db.videoDao().byId("v1"))
    }

    @Test
    fun 刪掉整支收藏() = runTest {
        repo.commitPicks(video, listOf(storyboardPick(0), storyboardPick(1)))
        repo.deleteVideo("v1")
        assertEquals(0, db.shotDao().countOfVideo("v1"))
        assertNull(db.videoDao().byId("v1"))
    }

    @Test
    fun 同一層不可以有同名資料夾() = runTest {
        val root = repo.createFolder(parentId = null, name = "旅行")
        val dup = runCatching { repo.createFolder(parentId = null, name = "旅行") }
        assertTrue(dup.isFailure)
        assertNotNull(repo.createFolder(parentId = root, name = "旅行"))
    }

    @Test
    fun 資料夾深度不可以超過五層() = runTest {
        var parent: Long? = null
        repeat(5) { i -> parent = repo.createFolder(parent, "第${i + 1}層") }
        val sixth = runCatching { repo.createFolder(parent, "第6層") }
        assertTrue(sixth.isFailure)
    }

    @Test
    fun 名稱長度上限五十() = runTest {
        assertNotNull(repo.createFolder(null, "字".repeat(50)))
        assertTrue(runCatching { repo.createFolder(null, "字".repeat(51)) }.isFailure)
    }

    @Test
    fun 寫入之後會通知變更() = runTest {
        var changed = 0
        val watched: LibraryRepo = RoomLibraryRepo(db, Dispatchers.IO) { changed++ }
        val ids = watched.commitPicks(video, listOf(storyboardPick(0)))
        assertEquals(1, changed)
        watched.patchShots(ids, ShotPatch(null, "宜蘭", null, null))
        assertEquals(2, changed)
    }

    @Test
    fun 標籤跟著整批入庫_同名只建一個() = runTest {
        val ids = repo.commitPicks(
            video,
            listOf(
                storyboardPick(0).copy(tagNames = listOf("玩水", "阿明")),
                storyboardPick(1).copy(tagNames = listOf("玩水")),
            ),
        )
        assertEquals(2, db.tagDao().count())
        assertEquals(2, db.tagDao().linkCountOfShot(ids[0]))
        assertEquals(1, db.tagDao().linkCountOfShot(ids[1]))
    }

    @Test
    fun 已經存在的標籤沿用不重建() = runTest {
        repo.commitPicks(video, listOf(storyboardPick(0).copy(tagNames = listOf("玩水"))))
        repo.commitPicks(video, listOf(storyboardPick(1).copy(tagNames = listOf("玩水"))))
        assertEquals(1, db.tagDao().count())
    }

    @Test
    fun 精靈建的標籤kind是other() = runTest {
        repo.commitPicks(video, listOf(storyboardPick(0).copy(tagNames = listOf("玩水"))))
        assertEquals("other", db.tagDao().byName("玩水")!!.kind)
    }

    @Test
    fun 整批回滾時標籤也不留() = runTest {
        repo.commitPicks(video, listOf(storyboardPick(3)))
        val failed = runCatching {
            // 第二張撞到已收藏的格號 → 整批失敗
            repo.commitPicks(
                video,
                listOf(storyboardPick(4).copy(tagNames = listOf("露營")), storyboardPick(3)),
            )
        }
        assertTrue(failed.isFailure)
        assertEquals(0, db.tagDao().count())
    }

    @Test
    fun 既有地點與標籤查得出來供抽屜建議() = runTest {
        repo.commitPicks(
            video,
            listOf(
                storyboardPick(0).copy(place = "冬山河", tagNames = listOf("玩水")),
                storyboardPick(1).copy(place = "冬山河", tagNames = listOf("阿明")),
                storyboardPick(2).copy(place = null),
            ),
        )
        assertEquals(listOf("冬山河"), repo.distinctPlaces())
        assertEquals(listOf("玩水", "阿明"), repo.allTagNames())
    }
}
