package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.entity.ShotTagEntity
import com.xenyaa.videoshot.data.library.entity.TagEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 查詢頁（階段 10）的 repo 層整合測試。用真的 in-memory Room DB——這批查詢有 UNION ALL、
 * CTE、row-value 比較，光靠 DAO 的假實作測不出 SQL 本身寫對了沒。
 */
@RunWith(AndroidJUnit4::class)
class SearchRepoTest {

    private val libraryDb = inMemoryLibraryDb()
    private val repo = RoomLibraryRepo(libraryDb, Dispatchers.Default)

    @After fun tearDown() { libraryDb.close() }

    private suspend fun seedShot(videoId: String, atSec: Double, eventDate: String, place: String?, tagNames: List<String> = emptyList()): Long {
        libraryDb.videoDao().upsert(VideoEntity(videoId, "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L))
        val id = libraryDb.shotDao().insert(
            com.xenyaa.videoshot.data.library.entity.ShotEntity(
                id = 0, videoId = videoId, atSec = atSec, source = "storyboard", frameIndex = atSec.toInt(),
                sbLevel = 3, eventDate = eventDate, place = place, description = null,
                aiTranscript = null, aiVisualDesc = null, aiRaw = null, createdAt = 0L,
            )
        )
        for (name in tagNames) {
            val tagId = libraryDb.tagDao().byName(name)?.id
                ?: libraryDb.tagDao().insert(TagEntity(0, name, "topic", "[]"))
            libraryDb.tagDao().link(ShotTagEntity(id, tagId, "human"))
        }
        return id
    }

    @Test
    fun searchFacets_地點與標籤混排依張數排序() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭", tagNames = listOf("露營"))
        seedShot("v1", 10.0, "2026-03-02", place = "宜蘭", tagNames = listOf("露營"))
        seedShot("v1", 20.0, "2026-02-01", place = "台北", tagNames = listOf("美食"))

        val facets = repo.searchFacets(upToMonth = null, limit = 10)

        assertEquals(listOf("宜蘭", "露營"), facets.filter { it.count == 2 }.map { it.name }.sorted())
        assertEquals(1, facets.count { it.name == "台北" })
    }

    @Test
    fun searchFacets_時間篩選只看該月以前() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        seedShot("v1", 10.0, "2026-01-01", place = "台北")

        val facets = repo.searchFacets(upToMonth = "2026-01", limit = 10)

        assertEquals(listOf("台北"), facets.map { it.name })
    }

    @Test
    fun queryVocabulary_地點與標籤含別名() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        libraryDb.tagDao().insert(TagEntity(0, "小橘", "pet", """["我家的貓","橘貓"]"""))

        val vocab = repo.queryVocabulary()

        assertEquals(listOf("宜蘭"), vocab.places)
        assertEquals("小橘", vocab.tags.single().name)
        assertEquals(listOf("我家的貓", "橘貓"), vocab.tags.single().aliases)
    }

    @Test
    fun searchByFacets_地點或標籤任一命中即算() = runTest {
        val a = seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        val b = seedShot("v1", 10.0, "2026-03-02", place = "台北", tagNames = listOf("露營"))
        seedShot("v1", 20.0, "2026-03-03", place = "高雄")

        val page = repo.searchByFacets(places = setOf("宜蘭"), tagNames = setOf("露營"), upToMonth = null, after = null, limit = 10)

        assertEquals(setOf(a, b), page.items.map { it.id }.toSet())
    }

    @Test
    fun searchByFacets_排序跟首頁一樣是時間新到舊() = runTest {
        seedShot("v1", 0.0, "2026-01-01", place = "宜蘭")
        seedShot("v1", 10.0, "2026-03-01", place = "宜蘭")

        val page = repo.searchByFacets(places = setOf("宜蘭"), tagNames = emptySet(), upToMonth = null, after = null, limit = 10)

        assertEquals(listOf("2026-03-01", "2026-01-01"), page.items.map { it.eventDate })
    }

    @Test
    fun searchByFacets_同一張圖有多個標籤不會重複出現() = runTest {
        val id = seedShot("v1", 0.0, "2026-03-01", place = null, tagNames = listOf("露營", "夜潛"))

        val page = repo.searchByFacets(places = emptySet(), tagNames = setOf("露營", "夜潛"), upToMonth = null, after = null, limit = 10)

        assertEquals(listOf(id), page.items.map { it.id })
    }

    @Test
    fun searchByFacetsCount_跟結果張數一致() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        seedShot("v1", 10.0, "2026-03-02", place = "台北")

        val count = repo.searchByFacetsCount(places = setOf("宜蘭"), tagNames = emptySet(), upToMonth = null)

        assertEquals(1, count)
    }

    @Test
    fun searchByQuery_相關度地點大於標籤大於關鍵字() = runTest {
        val byPlace = seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        val byTag = seedShot("v1", 10.0, "2026-03-01", place = null, tagNames = listOf("露營"))
        val byKeyword = seedShot("v1", 20.0, "2026-03-01", place = null)
        libraryDb.shotDao().updateDescription(byKeyword, "大蝦")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭"), tags = listOf("露營"), keywords = listOf("大蝦")),
            after = null, limit = 10,
        )

        assertEquals(listOf(byPlace, byTag, byKeyword), page.items.map { it.id })
    }

    @Test
    fun searchByQuery_兩個字的關鍵字也查得到_對應手冊驗收案例24() = runTest {
        val id = seedShot("v1", 0.0, "2026-03-01", place = null)
        libraryDb.shotDao().updateDescription(id, "大蝦")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(keywords = listOf("大蝦")),
            after = null, limit = 10,
        )

        assertEquals(listOf(id), page.items.map { it.id })
    }

    @Test
    fun searchByQuery_三個字以上的關鍵字走FTS一樣查得到() = runTest {
        val id = seedShot("v1", 0.0, "2026-03-01", place = null)
        libraryDb.shotDao().updateDescription(id, "夜裡的大蝦")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(keywords = listOf("大蝦")),
            after = null, limit = 10,
        )

        assertEquals(listOf(id), page.items.map { it.id })
    }

    @Test
    fun searchByQuery_keyset分頁不重複不遺漏() = runTest {
        val ids = (1..5).map { seedShot("v1", it.toDouble(), "2026-03-0$it", place = "宜蘭") }

        val first = repo.searchByQuery(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), after = null, limit = 2)
        assertEquals(2, first.items.size)
        val second = repo.searchByQuery(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), after = first.next, limit = 2)
        val third = repo.searchByQuery(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), after = second.next, limit = 2)

        val seen = (first.items + second.items + third.items).map { it.id }
        assertEquals(ids.sortedDescending(), seen)
        assertEquals(null, third.next)
    }

    @Test
    fun searchByQueryCount_跟結果張數一致() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        seedShot("v1", 10.0, "2026-03-01", place = "台北")

        val count = repo.searchByQueryCount(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")))

        assertEquals(1, count)
    }
}
