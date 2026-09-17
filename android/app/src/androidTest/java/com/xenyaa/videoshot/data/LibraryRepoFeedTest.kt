package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.ShotPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepoFeedTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private suspend fun seed(videoId: String, eventDate: String, n: Int, place: String? = null): List<Long> {
        db.videoDao().upsert(VideoEntity(videoId, "t", "c", "${eventDate}T00:00:00Z", 600, "public", null, 1L))
        return (0 until n).map { i ->
            db.shotDao().insert(
                ShotEntity(
                    id = 0, videoId = videoId, atSec = i.toDouble(), source = "storyboard",
                    frameIndex = i, sbLevel = 3, eventDate = eventDate, place = place,
                    description = null, aiTranscript = null, aiVisualDesc = null, aiRaw = null,
                    createdAt = 1L,
                )
            )
        }
    }

    @Test
    fun 篩選某月以前_含當月整月() = runTest {
        seed("v1", "2026-03-31", 1)
        seed("v2", "2026-04-01", 1)
        seed("v3", "2026-01-15", 1)
        val page = repo.homeFeed(after = null, limit = 50, upToMonth = "2026-03")
        assertEquals(listOf("2026-03-31", "2026-01-15"), page.items.map { it.eventDate })
    }

    @Test
    fun 沒有篩選時整條時間軸都在() = runTest {
        seed("v1", "2026-04-01", 1)
        seed("v2", "2026-01-15", 1)
        assertEquals(2, repo.homeFeed(after = null, limit = 50).items.size)
    }

    @Test
    fun 篩選狀態下的_keyset_分頁不重不漏() = runTest {
        seed("v1", "2026-03-10", 3)
        seed("v2", "2026-05-10", 3)   // 被篩掉的那幾張不可以佔用 limit 的名額
        val first = repo.homeFeed(after = null, limit = 2, upToMonth = "2026-03")
        val second = repo.homeFeed(after = first.next, limit = 2, upToMonth = "2026-03")
        val ids = (first.items + second.items).map { it.id }
        assertEquals(3, ids.distinct().size)
        assertEquals(null, second.next)
    }

    @Test
    fun 總張數跟著篩選走() = runTest {
        seed("v1", "2026-03-10", 3)
        seed("v2", "2026-05-10", 2)
        assertEquals(5, repo.shotCount())
        assertEquals(3, repo.shotCount(upToMonth = "2026-03"))
        assertEquals(0, repo.shotCount(upToMonth = "2025-12"))
    }

    @Test
    fun 月份標籤列混排地點與標籤並依張數排序() = runTest {
        val ids = seed("v1", "2026-03-10", 3, place = "宜蘭")
        val tag = db.tagDao().insert(TagEntity(0, "露營", "topic", "[]"))
        db.tagDao().link(ShotTagEntity(ids[0], tag, "human"))
        db.tagDao().link(ShotTagEntity(ids[1], tag, "human"))
        val facets = repo.monthFacets("2026-03")
        assertEquals(listOf("宜蘭" to 3, "露營" to 2), facets.map { it.name to it.count })
        assertEquals(listOf("place", "tag"), facets.map { it.kind })
    }

    @Test
    fun 月份標籤列只算那個月() = runTest {
        seed("v1", "2026-03-10", 1, place = "宜蘭")
        seed("v2", "2026-04-10", 1, place = "花蓮")
        assertEquals(listOf("宜蘭"), repo.monthFacets("2026-03").map { it.name })
    }

    @Test
    fun 讀得出一張圖的標籤名() = runTest {
        val ids = seed("v1", "2026-03-10", 1)
        val a = db.tagDao().insert(TagEntity(0, "露營", "topic", "[]"))
        val b = db.tagDao().insert(TagEntity(0, "阿明", "person", "[]"))
        db.tagDao().link(ShotTagEntity(ids[0], a, "human"))
        db.tagDao().link(ShotTagEntity(ids[0], b, "human"))
        assertEquals(listOf("阿明", "露營"), repo.tagsOfShot(ids[0]).sorted())
        assertEquals(emptyList<String>(), repo.tagsOfShot(9999L))
    }

    /** 就地編輯只有標籤「名稱」，repo 要在同一個交易內解析成 id，不存在就建。 */
    @Test
    fun 用標籤名套用會在交易內建立缺少的標籤() = runTest {
        val ids = seed("v1", "2026-03-10", 1)
        repo.patchShots(ids, ShotPatch(null, null, null, tagIds = null, tagNames = listOf("新標籤")))
        assertEquals(listOf("新標籤"), repo.tagsOfShot(ids[0]))
        // 再套一次同名不會建出第二個
        repo.patchShots(ids, ShotPatch(null, null, null, tagIds = null, tagNames = listOf("新標籤")))
        assertEquals(1, db.tagDao().count())
    }

    @Test
    fun 同時給_tagIds_與_tagNames_會被擋下來() = runTest {
        val ids = seed("v1", "2026-03-10", 1)
        val failed = runCatching {
            repo.patchShots(ids, ShotPatch(null, null, null, tagIds = listOf(1L), tagNames = listOf("x")))
        }.isFailure
        assertTrue(failed)
    }

    /** 空字串＝清空（`:core` 的 DetailsPatch 約定），null＝不動。 */
    @Test
    fun 空字串把地點清成_null_而不是寫進空字串() = runTest {
        val ids = seed("v1", "2026-03-10", 1, place = "宜蘭")
        repo.patchShots(ids, ShotPatch(null, "", null, null))
        assertEquals(null, repo.shotById(ids[0])!!.place)
        // null 的欄位不動
        repo.patchShots(ids, ShotPatch(null, "花蓮", null, null))
        repo.patchShots(ids, ShotPatch(null, null, "描述", null))
        assertEquals("花蓮", repo.shotById(ids[0])!!.place)
    }

    /**
     * event_date 是 TEXT NOT NULL、也是月份篩選唯一依據的欄位 —— 跟 place／description
     * 不一樣，空字串在這一欄沒有「清空」的意義可以存，只能維持原值不動（規格第四節）。
     */
    @Test
    fun 空字串的日期不會改動原值() = runTest {
        val ids = seed("v1", "2026-03-10", 1)
        repo.patchShots(ids, ShotPatch("", null, null, null))
        assertEquals("2026-03-10", repo.shotById(ids[0])!!.eventDate)
    }
}
