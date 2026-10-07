package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 16B：首頁月份標籤、月份選單、查詢頁候選清單、帳號頁、標籤管理改讀統計表。
 * 每個測試都拿「從圖資直接算」的結果當對照，證明改讀統計表之後結果不變
 * （候選清單的排序依全部時間，是設計決議 3 刻意的改變，另有測試鎖住）。
 */
@RunWith(AndroidJUnit4::class)
class StatsReadTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: RoomLibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private fun video(id: String) = VideoEntity(id, "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L)

    private fun pick(i: Int, date: String, place: String?, tags: List<String> = emptyList()) = NewShot(
        atSec = i.toDouble(), source = "storyboard", frameIndex = i, sbLevel = 3,
        eventDate = date, place = place, description = null, webp = null, tagNames = tags,
    )

    /**
     * 2026-01：宜蘭×2（露營×2、阿明×1）、台北×1（阿明×1，所以阿明合計 2）
     * 2026-02：台北×3（美食×3）
     * 2026-03：花蓮×1、沒有地點×1（露營）
     * 全部時間：台北 4、宜蘭 2、花蓮 1；美食 3、露營 3、阿明 2
     */
    private suspend fun seed() {
        repo.commitPicks(video("v1"), listOf(
            pick(0, "2026-01-05", "宜蘭", listOf("露營", "阿明")),
            pick(1, "2026-01-06", "宜蘭", listOf("露營")),
            pick(2, "2026-01-07", "台北", listOf("阿明")),
            pick(3, "2026-02-01", "台北", listOf("美食")),
            pick(4, "2026-02-02", "台北", listOf("美食")),
            pick(5, "2026-02-03", "台北", listOf("美食")),
            pick(6, "2026-03-01", "花蓮"),
            pick(7, "2026-03-02", null, listOf("露營")),
        ))
    }

    @Test fun 月份選單() = runTest {
        seed()
        assertEquals(
            listOf("2026-03" to 2, "2026-02" to 3, "2026-01" to 3),
            repo.monthCounts().map { it.month to it.count },
        )
    }

    @Test fun 某月的地點與標籤_依張數再依名稱() = runTest {
        seed()
        val facets = repo.monthFacets("2026-01")
        assertEquals(
            listOf("宜蘭|place|2", "阿明|tag|2", "露營|tag|2", "台北|place|1"),
            facets.map { "${it.name}|${it.kind}|${it.count}" },
        )
        assertEquals("other", facets.first { it.name == "宜蘭" }.tagKind)
        assertEquals("other", facets.first { it.name == "露營" }.tagKind) // 入庫的新標籤 kind 是 other
    }

    @Test fun 候選清單_沒有時間範圍時依全部時間張數排序() = runTest {
        seed()
        assertEquals(
            listOf("台北", "美食", "露營", "宜蘭", "阿明", "花蓮"),
            repo.searchFacets(upToMonth = null, limit = 10).map { it.name },
        )
    }

    /** 設計決議 3：時間範圍只拿掉範圍內沒有圖的項目，排序仍依全部時間的張數（台北 4 排在宜蘭 2 前面，即使 1 月裡宜蘭比較多）。 */
    @Test fun 候選清單_時間範圍只過濾不改排序() = runTest {
        seed()
        assertEquals(
            listOf("台北", "露營", "宜蘭", "阿明"),
            repo.searchFacets(upToMonth = "2026-01", limit = 10).map { it.name },
        )
    }

    @Test fun 候選清單_上限與探測多一筆() = runTest {
        seed()
        assertEquals(3, repo.searchFacets(upToMonth = null, limit = 3).size)
    }

    @Test fun 帳號頁統計() = runTest {
        seed()
        val stats = repo.accountStats(thisMonth = "2026-02")
        assertEquals(8, stats.totalShots)
        assertEquals(3, stats.thisMonthShots)
        assertEquals(1, stats.distinctVideos)
    }

    @Test fun 標籤管理_沒有圖的標籤是零() = runTest {
        seed()
        db.tagDao().insert(com.xenyaa.videoshot.data.library.entity.TagEntity(0, "沒用到", "topic", "[]"))
        assertEquals(
            listOf("沒用到|0", "美食|3", "阿明|2", "露營|3"),
            repo.allTagsWithUsage().map { "${it.name}|${it.shotCount}" },
        )
    }

    @Test fun 刪光之後讀到的都是空的或零() = runTest {
        seed()
        repo.deleteVideo("v1")
        assertEquals(emptyList<Any>(), repo.monthCounts())
        assertEquals(emptyList<Any>(), repo.monthFacets("2026-01"))
        assertEquals(emptyList<Any>(), repo.searchFacets(upToMonth = null, limit = 10))
        assertEquals(0, repo.accountStats("2026-01").totalShots)
    }

    @Test fun 選取項目的總張數() = runTest {
        seed()
        val taipei = db.placeDao().byName("台北")!!.id
        val food = db.tagDao().byName("美食")!!.id
        assertEquals(7L, db.statsDao().selectedTotal(listOf(taipei), listOf(food)))
        assertEquals(0L, db.statsDao().selectedTotal(emptyList(), emptyList()))
    }
}
