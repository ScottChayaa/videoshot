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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 16C：地點管理（清單、改名、刪除）、地點與標籤合併（舊名留成別名）、別名在查詢時視同本名。 */
@RunWith(AndroidJUnit4::class)
class PlaceManagementRepoTest {

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

    /** 宜蘭礁溪 2 張、礁溪 1 張、花蓮 1 張；標籤 阿明 2、明哥 1。 */
    private suspend fun seed() {
        repo.commitPicks(video("v1"), listOf(
            pick(0, "2026-01-05", "宜蘭礁溪", listOf("阿明")),
            pick(1, "2026-02-05", "宜蘭礁溪", listOf("阿明", "明哥")),
            pick(2, "2026-03-05", "礁溪"),
            pick(3, "2026-03-06", "花蓮", listOf("明哥")),
        ))
    }

    private suspend fun placeId(name: String) = db.placeDao().byName(name)!!.id

    @Test fun 地點清單附張數_沒有圖的地點是零() = runTest {
        seed()
        db.placeIdOf("沒用到")
        assertEquals(
            listOf("宜蘭礁溪|2", "沒用到|0", "礁溪|1", "花蓮|1"),
            repo.allPlacesWithUsage().map { "${it.name}|${it.shotCount}" },
        )
    }

    @Test fun 改名成沒人用的名字_直接改並存別名() = runTest {
        seed()
        repo.renamePlace(placeId("花蓮"), "花蓮市", listOf("洄瀾"))
        val p = repo.allPlacesWithUsage().single { it.name == "花蓮市" }
        assertEquals(listOf("洄瀾"), p.aliases)
        assertEquals(1, p.shotCount)
    }

    @Test fun 合併地點_圖改指向目標_舊名變別名_來源刪除_統計一致() = runTest {
        seed()
        val from = placeId("礁溪")
        repo.mergePlace(from, placeId("宜蘭礁溪"))
        val places = repo.allPlacesWithUsage()
        assertEquals(listOf("宜蘭礁溪|3", "花蓮|1"), places.map { "${it.name}|${it.shotCount}" })
        assertEquals(listOf("礁溪"), places.first { it.name == "宜蘭礁溪" }.aliases)
        assertNull(db.placeDao().byName("礁溪"))
        db.assertStatsMatchRecount()
    }

    @Test fun 改名撞名等於合併() = runTest {
        seed()
        repo.renamePlace(placeId("礁溪"), "宜蘭礁溪", emptyList())
        assertEquals(3, repo.allPlacesWithUsage().first { it.name == "宜蘭礁溪" }.shotCount)
        assertEquals(listOf("礁溪"), repo.allPlacesWithUsage().first { it.name == "宜蘭礁溪" }.aliases)
    }

    @Test fun 合併時來源的別名也併過去_去掉重複與目標本名() = runTest {
        seed()
        repo.renamePlace(placeId("宜蘭礁溪"), "宜蘭礁溪", listOf("湯圍"))
        repo.renamePlace(placeId("礁溪"), "礁溪", listOf("湯圍", "宜蘭礁溪", "礁溪鄉"))
        repo.mergePlace(placeId("礁溪"), placeId("宜蘭礁溪"))
        assertEquals(
            listOf("湯圍", "礁溪", "礁溪鄉"),
            repo.allPlacesWithUsage().first { it.name == "宜蘭礁溪" }.aliases,
        )
    }

    @Test fun 刪除地點_圖還在只是沒有地點() = runTest {
        seed()
        repo.deletePlace(placeId("花蓮"))
        assertEquals(4, repo.accountStats("2026-03").totalShots)
        assertTrue(repo.allPlacesWithUsage().none { it.name == "花蓮" })
        db.assertStatsMatchRecount()
    }

    @Test fun 不能合併到自己() = runTest {
        seed()
        val id = placeId("花蓮")
        assertTrue(runCatching { repo.mergePlace(id, id) }.isFailure)
        assertEquals(1, repo.allPlacesWithUsage().first { it.name == "花蓮" }.shotCount)
    }

    @Test fun 合併標籤_舊名變別名_同一張圖兩個都有也不重複() = runTest {
        seed()
        val tags = repo.allTagsWithUsage().associateBy { it.name }
        repo.mergeTag(tags.getValue("明哥").id, tags.getValue("阿明").id)
        val merged = repo.allTagsWithUsage().single()
        assertEquals("阿明", merged.name)
        assertEquals(3, merged.shotCount) // pick1 同時有兩個標籤，只算一次
        assertEquals(listOf("明哥"), merged.aliases)
        db.assertStatsMatchRecount()
    }

    @Test fun 查詢時地點別名視同本名() = runTest {
        seed()
        repo.mergePlace(placeId("礁溪"), placeId("宜蘭礁溪"))
        val page = repo.searchByFacets(setOf("礁溪"), emptySet(), upToMonth = null, after = null, limit = 10)
        assertEquals(3, page.items.size)
        assertEquals(3, repo.searchByFacetsCount(setOf("礁溪"), emptySet(), upToMonth = null))
        val vocab = repo.queryVocabulary()
        assertEquals(listOf("礁溪"), vocab.placeAliases["宜蘭礁溪"])
    }

    @Test fun 描述查詢的關鍵字也比對地點別名() = runTest {
        seed()
        repo.renamePlace(placeId("花蓮"), "花蓮", listOf("洄瀾"))
        val page = repo.searchByQuery(
            com.xenyaa.videoshot.core.query.ParsedQuery(keywords = listOf("洄瀾")),
            upToMonth = null, after = null, limit = 10,
        )
        assertEquals(1, page.items.size)
    }
}
