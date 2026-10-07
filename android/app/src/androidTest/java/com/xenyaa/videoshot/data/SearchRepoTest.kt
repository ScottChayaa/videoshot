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
                // 16A：place 欄位改成 place_id
                sbLevel = 3, eventDate = eventDate, placeId = libraryDb.placeIdOf(place), description = null,
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

    /** 15C 最終審查 Minor：標籤列帶標籤自己的 kind，地點列固定 "other"（畫面端先看 kind=="place"）。 */
    @Test
    fun searchFacets_標籤列帶tagKind地點列為other() = runTest {
        libraryDb.tagDao().insert(TagEntity(0, "小明", "person", "[]"))
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭", tagNames = listOf("小明", "露營"))

        val facets = repo.searchFacets(upToMonth = null, limit = 10)

        assertEquals("person", facets.single { it.name == "小明" }.tagKind)
        assertEquals("topic", facets.single { it.name == "露營" }.tagKind)
        val place = facets.single { it.name == "宜蘭" }
        assertEquals("place", place.kind)
        assertEquals("other", place.tagKind)
    }

    /** 16B：時間範圍拿掉範圍內沒有圖的項目（排序另見 `StatsReadTest`）。 */
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
            upToMonth = null, after = null, limit = 10,
        )

        assertEquals(listOf(byPlace, byTag, byKeyword), page.items.map { it.id })
    }

    @Test
    fun searchByQuery_兩個字的關鍵字也查得到_對應手冊驗收案例24() = runTest {
        val id = seedShot("v1", 0.0, "2026-03-01", place = null)
        libraryDb.shotDao().updateDescription(id, "大蝦")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(keywords = listOf("大蝦")),
            upToMonth = null, after = null, limit = 10,
        )

        assertEquals(listOf(id), page.items.map { it.id })
    }

    @Test
    fun searchByQuery_三個字以上的關鍵字走FTS一樣查得到() = runTest {
        // 關鍵字要真的 >=3 個字元才會走 SearchDao.matchIds(FTS5 trigram)——
        // 這裡順便也測到 shot_fts 的 update 觸發器(shot_fts_after_update)：
        // seed 時 description 是 null，靠 updateDescription 才把內容寫進去，FTS 索引要跟著同步更新。
        val id = seedShot("v1", 0.0, "2026-03-01", place = null)
        libraryDb.shotDao().updateDescription(id, "夜裡的大蝦")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(keywords = listOf("夜裡的")),
            upToMonth = null, after = null, limit = 10,
        )

        assertEquals(listOf(id), page.items.map { it.id })
    }

    @Test
    fun searchByQuery_關鍵字含雙引號等FTS保留字元不會丟語法錯誤() = runTest {
        // RoomLibraryRepo.keywordIdsOf 把 >=3 字的關鍵字包成 FTS5 片語字面值再傳給 matchIds，
        // 避免 `"`、`(`、`)`、`:`、`*` 這些字元被 FTS5 查詢語法解讀成運算子而丟 fts5: syntax error。
        // 這裡的關鍵字本身就帶一對雙引號，驗證跳脫(把內嵌的 " 雙寫)之後仍是合法語法、也還查得到。
        val id = seedShot("v1", 0.0, "2026-03-01", place = null)
        libraryDb.shotDao().updateDescription(id, "他說\"好吃\"")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(keywords = listOf("\"好吃\"")),
            upToMonth = null, after = null, limit = 10,
        )

        assertEquals(listOf(id), page.items.map { it.id })
    }

    @Test
    fun searchByQuery_keyset分頁不重複不遺漏() = runTest {
        val ids = (1..5).map { seedShot("v1", it.toDouble(), "2026-03-0$it", place = "宜蘭") }

        val first = repo.searchByQuery(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), upToMonth = null, after = null, limit = 2)
        assertEquals(2, first.items.size)
        val second = repo.searchByQuery(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), upToMonth = null, after = first.next, limit = 2)
        val third = repo.searchByQuery(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), upToMonth = null, after = second.next, limit = 2)

        val seen = (first.items + second.items + third.items).map { it.id }
        assertEquals(ids.sortedDescending(), seen)
        assertEquals(null, third.next)
    }

    @Test
    fun searchByQueryCount_跟結果張數一致() = runTest {
        seedShot("v1", 0.0, "2026-03-01", place = "宜蘭")
        seedShot("v1", 10.0, "2026-03-01", place = "台北")

        val count = repo.searchByQueryCount(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), upToMonth = null)

        assertEquals(1, count)
    }

    /**
     * 文字模式的時間篩選是跟 [ParsedQuery.dateFrom]／[ParsedQuery.dateTo] 各自獨立的第二層
     * 篩選（最終審查 Important 5）——這裡兩張都命中查詢條件（同一個地點），但只有一張落在
     * `upToMonth` 邊界以內，`searchByQuery`／`searchByQueryCount` 都要只回那一張。
     */
    @Test
    fun searchByQuery_upToMonth邊界以外的不算進結果() = runTest {
        val inside = seedShot("v1", 0.0, "2026-01-15", place = "宜蘭")
        seedShot("v1", 10.0, "2026-03-01", place = "宜蘭")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")),
            upToMonth = "2026-01", after = null, limit = 10,
        )
        val count = repo.searchByQueryCount(
            com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), upToMonth = "2026-01",
        )

        assertEquals(listOf(inside), page.items.map { it.id })
        assertEquals(1, count)
    }

    /** 16A：地點不在全文索引裡，關鍵字改比對地點名稱，命中的算地點層。 */
    @Test
    fun 描述查詢的關鍵字也比對地點名稱() = runTest {
        val hit = seedShot("v1", 0.0, "2026-03-01", place = "宜蘭礁溪")
        seedShot("v1", 10.0, "2026-03-02", place = "台北")

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(keywords = listOf("礁溪")),
            upToMonth = null, after = null, limit = 10,
        )

        assertEquals(listOf(hit), page.items.map { it.id })
    }

    /** 16A 唯一刻意的行為改變：關鍵字命中地點名稱算地點層（2），高於只靠標籤命中（1）。 */
    @Test
    fun 關鍵字命中地點名稱排在只命中標籤的前面() = runTest {
        val byPlaceName = seedShot("v1", 0.0, "2026-01-01", place = "宜蘭礁溪")
        val byTag = seedShot("v1", 10.0, "2026-03-01", place = null, tagNames = listOf("露營"))

        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(tags = listOf("露營"), keywords = listOf("礁溪")),
            upToMonth = null, after = null, limit = 10,
        )

        assertEquals(listOf(byPlaceName, byTag), page.items.map { it.id })
    }

    /** 16B 設計決議 4：兩種查法的結果與分頁必須完全一樣。門檻設 0 強迫掃描、設很大強迫分段合併。 */
    @Test
    fun 分段合併與掃描兩種查法結果一樣() = runTest {
        val dates = listOf("2026-01-05", "2026-01-05", "2026-01-20", "2026-02-01", "2026-02-01", "2026-02-01",
            "2026-02-15", "2026-02-28", "2026-03-01", "2026-03-02", "2026-01-31", "2026-02-10")
        dates.forEachIndexed { i, d ->
            seedShot("v1", i.toDouble(), d, place = if (i % 3 == 0) "宜蘭" else null, tagNames = if (i % 2 == 0) listOf("露營") else emptyList())
        }
        seedShot("v2", 0.0, "2025-12-31", place = "宜蘭", tagNames = listOf("露營"))

        suspend fun allPages(repo: RoomLibraryRepo): List<com.xenyaa.videoshot.data.repo.model.ShotRow> {
            val rows = mutableListOf<com.xenyaa.videoshot.data.repo.model.ShotRow>()
            var cursor: com.xenyaa.videoshot.core.paging.ShotCursor? = null
            do {
                val page = repo.searchByFacets(setOf("宜蘭"), setOf("露營"), upToMonth = "2026-02", after = cursor, limit = 3)
                rows += page.items
                cursor = page.next
            } while (cursor != null)
            return rows
        }

        val union = allPages(RoomLibraryRepo(libraryDb, Dispatchers.IO, facetUnionThreshold = Long.MAX_VALUE))
        val scan = allPages(RoomLibraryRepo(libraryDb, Dispatchers.IO, facetUnionThreshold = 0))
        assertEquals(scan.map { it.id }, union.map { it.id })
        assertEquals(scan, union)
        assertEquals(union.map { it.id }.distinct(), union.map { it.id }) // 同時命中地點與標籤的圖只出現一次
        assertEquals(true, union.isNotEmpty())
        assertEquals(false, union.any { it.eventDate.startsWith("2026-03") })
        assertEquals(true, union.any { it.eventDate == "2025-12-31" })

        // 張數也要兩種查法一致（含同時命中地點與標籤、時間範圍排除 2026-03）
        for (month in listOf("2026-02", "2026-01", null)) {
            val u = RoomLibraryRepo(libraryDb, Dispatchers.IO, facetUnionThreshold = Long.MAX_VALUE)
                .searchByFacetsCount(setOf("宜蘭"), setOf("露營"), month)
            val c = RoomLibraryRepo(libraryDb, Dispatchers.IO, facetUnionThreshold = 0)
                .searchByFacetsCount(setOf("宜蘭"), setOf("露營"), month)
            assertEquals("month=$month", c, u)
        }
        assertEquals(union.size, RoomLibraryRepo(libraryDb, Dispatchers.IO, facetUnionThreshold = Long.MAX_VALUE)
            .searchByFacetsCount(setOf("宜蘭"), setOf("露營"), "2026-02"))
    }

    /** 設計決議 2：張數最多數到 1001（畫面顯示 1000+）。 */
    @Test
    fun 結果張數有上限() = runTest {
        libraryDb.videoDao().upsert(VideoEntity("big", "t", "c", "2026-01-01T00:00:00Z", 600, "public", null, 1L))
        val placeId = libraryDb.placeIdOf("宜蘭")
        libraryDb.execOnWriter(
            "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1200) " +
                "INSERT INTO shot (video_id, at_sec, source, frame_index, sb_level, event_date, place_id, description, created_at) " +
                "SELECT 'big', i, 'storyboard', i, 3, '2026-01-01', $placeId, NULL, 0 FROM n"
        )
        assertEquals(1001, repo.searchByFacetsCount(setOf("宜蘭"), emptySet(), upToMonth = null))
        assertEquals(1001, repo.searchByQueryCount(com.xenyaa.videoshot.core.query.ParsedQuery(places = listOf("宜蘭")), upToMonth = null))
        assertEquals(0, repo.searchByFacetsCount(setOf("宜蘭"), emptySet(), upToMonth = "2025-12"))
    }
}
