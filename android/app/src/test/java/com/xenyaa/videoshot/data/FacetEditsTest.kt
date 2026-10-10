package com.xenyaa.videoshot.data

import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.FacetRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FacetEditsTest {

    /** 改名時：名稱是「宜蘭」就當成撞名合併到 2 號，其他名稱不撞名。 */
    private class Library : FakeLibraryRepo() {
        override suspend fun renamePlace(id: Long, name: String, aliases: List<String>): Long = if (name == "宜蘭") 2L else id
        override suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>): Long = if (name == "貓") 2L else id
    }

    private suspend fun setUp(): Pair<FacetEdits, FakeCacheRepo> {
        val cache = FakeCacheRepo()
        cache.touchFacets(listOf(FacetRef(1, 1), FacetRef(2, 1)), usedAt = 500)
        cache.touchFacets(listOf(FacetRef(1, 2), FacetRef(2, 2)), usedAt = 100)
        val library = Library()
        return FacetEdits(library, FacetUsage(library, cache) { 0L }) to cache
    }

    @Test fun 合併地點_目標取較新的時間() = runTest {
        val (edits, cache) = setUp()
        edits.mergePlace(fromId = 1, toId = 2)
        assertEquals(500L, cache.facetRecent()[FacetRef(1, 2)])
        assertEquals(null, cache.facetRecent()[FacetRef(1, 1)])
        assertEquals(500L, cache.facetRecent()[FacetRef(2, 1)]) // 標籤不受影響
    }

    @Test fun 合併標籤_目標取較新的時間() = runTest {
        val (edits, cache) = setUp()
        edits.mergeTag(fromId = 1, toId = 2)
        assertEquals(500L, cache.facetRecent()[FacetRef(2, 2)])
        assertEquals(null, cache.facetRecent()[FacetRef(2, 1)])
    }

    @Test fun 改名撞名_當成合併() = runTest {
        val (edits, cache) = setUp()
        edits.renamePlace(id = 1, name = "宜蘭", aliases = emptyList())
        edits.renameTag(id = 1, name = "貓", kind = "animal", aliases = emptyList())
        assertEquals(setOf(FacetRef(1, 2), FacetRef(2, 2)), cache.facetRecent().keys)
        assertEquals(500L, cache.facetRecent()[FacetRef(1, 2)])
    }

    @Test fun 改名不撞名_時間不動() = runTest {
        val (edits, cache) = setUp()
        edits.renamePlace(id = 1, name = "宜蘭縣", aliases = emptyList())
        assertEquals(500L, cache.facetRecent()[FacetRef(1, 1)])
    }

    @Test fun 刪除_連同紀錄刪掉() = runTest {
        val (edits, cache) = setUp()
        edits.deletePlace(1)
        edits.deleteTag(2)
        assertEquals(setOf(FacetRef(2, 1), FacetRef(1, 2)), cache.facetRecent().keys)
    }
}
