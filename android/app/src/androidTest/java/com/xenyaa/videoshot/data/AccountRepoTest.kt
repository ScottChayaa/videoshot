package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRepoTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private fun video(id: String) = VideoEntity(id, "片名", "頻道", "2026-02-02T10:00:00Z", 600, "public", null, 1L)

    private fun pick(eventDate: String, frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = eventDate, place = null, description = null, webp = null,
    )

    @Test
    fun 統計總張數本月新增來源影片數() = runTest {
        repo.commitPicks(video("v1"), listOf(pick("2026-02-01", 0), pick("2026-03-05", 1)))
        repo.commitPicks(video("v2"), listOf(pick("2026-03-10", 0)))

        val stats = repo.accountStats(thisMonth = "2026-03")

        assertEquals(3, stats.totalShots)
        assertEquals(2, stats.thisMonthShots)
        assertEquals(2, stats.distinctVideos)
    }

    @Test
    fun 沒有任何收藏時三格都是零() = runTest {
        val stats = repo.accountStats(thisMonth = "2026-03")
        assertEquals(0, stats.totalShots)
        assertEquals(0, stats.thisMonthShots)
        assertEquals(0, stats.distinctVideos)
    }

    @Test
    fun 全部標籤附使用張數依名稱排序() = runTest {
        val ids = repo.commitPicks(video("v1"), listOf(pick("2026-02-01", 0), pick("2026-02-01", 1)))
        db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "露營", "topic", "[\"野營\"]"))
        val petId = db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "阿橘", "pet", "[]"))
        db.tagDao().link(com.xenyaa.videoshot.data.library.entity.ShotTagEntity(ids[0], petId, "human"))
        db.tagDao().link(com.xenyaa.videoshot.data.library.entity.ShotTagEntity(ids[1], petId, "human"))

        val tags = repo.allTagsWithUsage()

        assertEquals(listOf("阿橘", "露營"), tags.map { it.name })
        val camp = tags.first { it.name == "露營" }
        assertEquals("topic", camp.kind)
        assertEquals(listOf("野營"), camp.aliases)
        assertEquals(0, camp.shotCount)
        val pet = tags.first { it.name == "阿橘" }
        assertEquals(2, pet.shotCount)
    }

    @Test
    fun 改名成沒有人用過的名字直接更新() = runTest {
        val id = db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "舊名", "other", "[]"))

        repo.renameTag(id, "新名", "topic", listOf("別名一"))

        val tag = repo.allTagsWithUsage().single()
        assertEquals("新名", tag.name)
        assertEquals("topic", tag.kind)
        assertEquals(listOf("別名一"), tag.aliases)
    }

    @Test
    fun 改名成既有名稱會合併並刪掉舊標籤且既有標籤的kind與別名不被草稿覆蓋() = runTest {
        val ids = repo.commitPicks(video("v1"), listOf(pick("2026-02-01", 0), pick("2026-02-01", 1)))
        // keep 的 kind／aliases 刻意跟下面的改名草稿不同——如果合併時誤把草稿的值蓋到 keep 上，
        // 這裡會抓到（規格「既有那個標籤的 kind／別名不會被這次編輯的值覆蓋」）
        val keep = db.tagDao()
            .insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "阿明", "person", "[\"小明\"]"))
        val dup = db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "阿明哥", "person", "[]"))
        db.tagDao().link(com.xenyaa.videoshot.data.library.entity.ShotTagEntity(ids[0], keep, "human"))
        db.tagDao().link(com.xenyaa.videoshot.data.library.entity.ShotTagEntity(ids[1], dup, "human"))

        repo.renameTag(dup, "阿明", "topic", listOf("哥哥"))

        val tags = repo.allTagsWithUsage()
        assertEquals(1, tags.size)
        val merged = tags.single()
        assertEquals("阿明", merged.name)
        assertEquals(2, merged.shotCount)
        assertEquals("person", merged.kind)
        assertEquals(listOf("小明"), merged.aliases)
    }

    @Test
    fun 改名成既有名稱時一支圖同時關聯keep與dup不會撞主鍵() = runTest {
        // 這支圖已經同時關聯 keep 與 dup——合併時 (shot, keep) 已存在，
        // reassignLinks 用 UPDATE OR IGNORE 略過那一列，靠刪除 dup 時的外鍵連動清掉殘餘關聯
        val ids = repo.commitPicks(video("v1"), listOf(pick("2026-02-01", 0)))
        val keep = db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "阿明", "person", "[]"))
        val dup = db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "阿明哥", "person", "[]"))
        db.tagDao().link(com.xenyaa.videoshot.data.library.entity.ShotTagEntity(ids[0], keep, "human"))
        db.tagDao().link(com.xenyaa.videoshot.data.library.entity.ShotTagEntity(ids[0], dup, "human"))

        repo.renameTag(dup, "阿明", "person", emptyList())

        assertEquals(1, repo.allTagsWithUsage().size)
        assertEquals(1, db.tagDao().linkCountOfShot(ids[0]))
    }

    @Test
    fun 刪除標籤只解除關聯圖不會被刪() = runTest {
        val ids = repo.commitPicks(video("v1"), listOf(pick("2026-02-01", 0)))
        val id = db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "露營", "topic", "[]"))
        db.tagDao().link(com.xenyaa.videoshot.data.library.entity.ShotTagEntity(ids[0], id, "human"))

        repo.deleteTag(id)

        assertEquals(0, repo.allTagsWithUsage().size)
        assertEquals(1, db.shotDao().countOfVideo("v1"))
    }
}
