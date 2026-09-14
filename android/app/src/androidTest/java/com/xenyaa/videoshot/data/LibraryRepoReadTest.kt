package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepoReadTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    /** 在 eventDate 這天塞 n 張圖，回傳它們的 id（由舊到新）。 */
    private suspend fun seed(videoId: String, eventDate: String, n: Int): List<Long> {
        db.videoDao().upsert(VideoEntity(videoId, "t", "c", "${eventDate}T00:00:00Z", 600, "public", null, 1L))
        return (0 until n).map { i ->
            db.shotDao().insert(
                ShotEntity(
                    id = 0, videoId = videoId, atSec = i.toDouble(), source = "storyboard",
                    frameIndex = i, sbLevel = 3, eventDate = eventDate,
                    place = if (i % 2 == 0) "宜蘭" else null, description = null,
                    aiTranscript = null, aiVisualDesc = null, aiRaw = null, createdAt = 1L,
                )
            )
        }
    }

    @Test
    fun 首頁由新到舊排序() = runTest {
        seed("v1", "2026-01-05", 2)
        seed("v2", "2026-03-01", 2)
        val page = repo.homeFeed(after = null, limit = 10)
        assertEquals(listOf("2026-03-01", "2026-03-01", "2026-01-05", "2026-01-05"), page.items.map { it.eventDate })
    }

    @Test
    fun keyset_分頁不重不漏() = runTest {
        seed("v1", "2026-02-02", 5)
        val first = repo.homeFeed(after = null, limit = 2)
        val second = repo.homeFeed(after = first.next, limit = 2)
        val third = repo.homeFeed(after = second.next, limit = 2)

        assertEquals(2, first.items.size)
        assertEquals(2, second.items.size)
        assertEquals(1, third.items.size)
        assertNull(third.next)
        val all = (first.items + second.items + third.items).map { it.id }
        assertEquals(all.size, all.toSet().size)
        assertEquals(5, all.size)
    }

    @Test
    fun 同一天之內以_id_遞減當第二排序鍵() = runTest {
        val ids = seed("v1", "2026-02-02", 3)
        val page = repo.homeFeed(after = null, limit = 10)
        assertEquals(ids.reversed(), page.items.map { it.id })
    }

    private data class MonthCountOf(val month: String, val count: Int)

    @Test
    fun 月份統計() = runTest {
        seed("v1", "2026-01-05", 2)
        seed("v2", "2026-01-20", 1)
        seed("v3", "2026-03-01", 4)
        assertEquals(
            listOf(MonthCountOf("2026-03", 4), MonthCountOf("2026-01", 3)),
            repo.monthCounts().map { MonthCountOf(it.month, it.count) },
        )
    }

    @Test
    fun 取單支影片的所有收藏依秒數排序() = runTest {
        db.videoDao().upsert(VideoEntity("v9", "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L))
        listOf(30.0, 5.5, 12.0).forEachIndexed { i, sec ->
            db.shotDao().insert(
                ShotEntity(0, "v9", sec, "storyboard", i, 3, "2026-01-01", null, null, null, null, null, 1L)
            )
        }
        assertEquals(listOf(5.5, 12.0, 30.0), repo.shotsOfVideo("v9").map { it.atSec })
    }

    @Test
    fun 取不到的_shot_回傳_null() = runTest {
        assertNull(repo.shotById(9999L))
    }

    /**
     * `frameIndex` 只在某個 storyboard 層級之內有意義（規格第四節 `{videoId}/L{level}/{frameIndex}`）。
     * 不照層級過濾的話，先前在 L2 取過的格號會把這次 L3 牆上不相干的格子畫成灰＋鎖。
     */
    @Test
    fun 已收藏的格號只算同一個層級的() = runTest {
        db.videoDao().upsert(VideoEntity("v7", "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L))
        db.shotDao().insert(
            ShotEntity(0, "v7", 10.0, "storyboard", 5, 3, "2026-01-01", null, null, null, null, null, 1L)
        )
        db.shotDao().insert(
            ShotEntity(0, "v7", 20.0, "storyboard", 7, 2, "2026-01-01", null, null, null, null, null, 1L)
        )

        assertEquals(setOf(5), repo.takenFrameIndexes("v7", 3))
        assertEquals(setOf(7), repo.takenFrameIndexes("v7", 2))
        assertEquals(emptySet<Int>(), repo.takenFrameIndexes("v7", 1))
    }
}
