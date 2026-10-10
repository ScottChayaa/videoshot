package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.model.FacetRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 篩選候選的最近使用時間（規格第四節 `facet_recent`）。 */
@RunWith(AndroidJUnit4::class)
class FacetRecentCacheTest {

    private lateinit var db: CacheDatabase
    private lateinit var repo: RoomCacheRepo

    @Before fun setUp() {
        db = inMemoryCacheDb()
        repo = RoomCacheRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private val place1 = FacetRef(1, 1)
    private val place2 = FacetRef(1, 2)
    private val tag1 = FacetRef(2, 1)

    @Test fun 記錄後讀得回來_再記一次覆蓋成新的時間() = runTest {
        repo.touchFacets(listOf(place1, tag1), usedAt = 100)
        repo.touchFacets(listOf(place1), usedAt = 200)
        assertEquals(mapOf(place1 to 200L, tag1 to 100L), repo.facetRecent())
    }

    @Test fun 同編號的地點與標籤互不干擾() = runTest {
        repo.touchFacets(listOf(place1), usedAt = 100)
        repo.touchFacets(listOf(tag1), usedAt = 300)
        assertEquals(100L, repo.facetRecent()[place1])
        assertEquals(300L, repo.facetRecent()[tag1])
    }

    @Test fun 合併時目標取較新的時間_來源刪掉() = runTest {
        repo.touchFacets(listOf(place1), usedAt = 500)
        repo.touchFacets(listOf(place2), usedAt = 100)
        repo.mergeFacetRecent(kind = 1, fromId = 1, toId = 2)
        assertEquals(mapOf(place2 to 500L), repo.facetRecent())
    }

    @Test fun 合併時目標比較新就保留目標的時間() = runTest {
        repo.touchFacets(listOf(place1), usedAt = 100)
        repo.touchFacets(listOf(place2), usedAt = 500)
        repo.mergeFacetRecent(kind = 1, fromId = 1, toId = 2)
        assertEquals(mapOf(place2 to 500L), repo.facetRecent())
    }

    @Test fun 合併時目標沒有紀錄就沿用來源的時間() = runTest {
        repo.touchFacets(listOf(place1), usedAt = 100)
        repo.mergeFacetRecent(kind = 1, fromId = 1, toId = 2)
        assertEquals(mapOf(place2 to 100L), repo.facetRecent())
    }

    @Test fun 合併時來源沒有紀錄就什麼都不動() = runTest {
        repo.touchFacets(listOf(place2), usedAt = 100)
        repo.mergeFacetRecent(kind = 1, fromId = 1, toId = 2)
        assertEquals(mapOf(place2 to 100L), repo.facetRecent())
    }

    @Test fun 刪除只刪那一列() = runTest {
        repo.touchFacets(listOf(place1, place2, tag1), usedAt = 100)
        repo.forgetFacetRecent(kind = 1, id = 1)
        assertEquals(setOf(place2, tag1), repo.facetRecent().keys)
    }

    @Test fun 還原時的清空也清掉最近使用時間() = runTest {
        repo.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "missing", 0, 0L, null)))
        repo.touchFacets(listOf(place1), usedAt = 100)
        repo.clearAll()
        assertEquals(emptyMap<FacetRef, Long>(), repo.facetRecent())
        assertEquals(0, repo.countByState("missing"))
    }
}
