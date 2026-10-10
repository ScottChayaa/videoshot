package com.xenyaa.videoshot.data

import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.FacetRef
import com.xenyaa.videoshot.data.repo.model.FilterOption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FacetUsageTest {

    private fun place(name: String, id: Long) = FilterOption(name, true, "other", emptyList(), id)
    private fun tag(name: String, id: Long) = FilterOption(name, false, "topic", emptyList(), id)

    // repo 給的順序＝全部時間張數多到少
    private val byCount = listOf(place("廣西", 1), tag("貓", 1), place("宜蘭", 2), tag("狗", 2), place("花蓮", 3))

    @Test fun 沒有使用紀錄時維持張數排序() {
        assertEquals(byCount, FacetUsage.orderByRecent(byCount, emptyMap()))
    }

    @Test fun 用過的依時間新到舊在前_沒用過的維持張數排序() {
        val recent = mapOf(FacetRef(1, 3) to 200L, FacetRef(1, 2) to 100L, FacetRef(2, 2) to 150L)
        assertEquals(
            listOf("花蓮", "狗", "宜蘭", "廣西", "貓"),
            FacetUsage.orderByRecent(byCount, recent).map { it.name },
        )
    }

    @Test fun 地點與標籤同編號不會搞混() {
        val recent = mapOf(FacetRef(2, 1) to 100L) // 標籤 1＝貓，不是地點 1＝廣西
        val ordered = FacetUsage.orderByRecent(byCount, recent)
        assertEquals("貓", ordered.first().name)
        assertEquals(listOf("廣西", "宜蘭", "花蓮"), ordered.filter { it.isPlace }.map { it.name })
    }

    @Test fun 編號是0的補位項目當成沒用過() {
        val missing = FilterOption("不在範圍內", true, "other", emptyList())
        val recent = mapOf(FacetRef(1, 0) to 999L, FacetRef(1, 3) to 100L)
        assertEquals(
            listOf("花蓮", "不在範圍內", "廣西"),
            FacetUsage.orderByRecent(listOf(missing, place("廣西", 1), place("花蓮", 3)), recent).map { it.name },
        )
    }

    @Test fun 同一個時間的維持原本順序() {
        val recent = mapOf(FacetRef(1, 1) to 100L, FacetRef(1, 3) to 100L)
        assertEquals(listOf("廣西", "花蓮"), FacetUsage.orderByRecent(byCount, recent).filter { it.isPlace }.take(2).map { it.name })
    }

    private class Library(private val options: List<FilterOption>) : FakeLibraryRepo() {
        val refsCalls = mutableListOf<Pair<Set<String>, Set<String>>>()
        override suspend fun filterOptions(upToMonth: String?) = options
        override suspend fun facetRefs(places: Set<String>, tagNames: Set<String>): List<FacetRef> {
            refsCalls += places to tagNames
            return places.mapNotNull { n -> options.firstOrNull { it.isPlace && it.name == n }?.let { FacetRef(1, it.id) } } +
                tagNames.mapNotNull { n -> options.firstOrNull { !it.isPlace && it.name == n }?.let { FacetRef(2, it.id) } }
        }
    }

    @Test fun 記錄使用後候選依新的時間排序() = runTest {
        val cache = FakeCacheRepo()
        var now = 100L
        val usage = FacetUsage(Library(byCount), cache) { now }
        usage.markUsed(places = setOf("宜蘭"), tagNames = emptySet())
        now = 200L
        usage.markUsed(places = setOf("花蓮"), tagNames = setOf("狗"))
        // 花蓮與狗同為 200，維持張數排序的相對順序（狗在花蓮前）
        assertEquals(listOf("狗", "花蓮", "宜蘭", "廣西", "貓"), usage.filterOptions(null).map { it.name })
    }

    @Test fun 沒有東西要記就不查名稱() = runTest {
        val library = Library(byCount)
        FacetUsage(library, FakeCacheRepo()) { 1L }.markUsed(emptySet(), emptySet())
        assertTrue(library.refsCalls.isEmpty())
    }

    @Test fun 讀不到使用時間時照張數排序_不讓抽屜讀取失敗() = runTest {
        val cache = object : FakeCacheRepo() {
            override suspend fun facetRecent(): Map<FacetRef, Long> = throw IllegalStateException("cache.db 壞了")
        }
        assertEquals(byCount, FacetUsage(Library(byCount), cache) { 1L }.filterOptions(null))
    }

    @Test fun 記錄失敗不往外丟() = runTest {
        val cache = object : FakeCacheRepo() {
            override suspend fun touchFacets(refs: Collection<FacetRef>, usedAt: Long) = throw IllegalStateException("寫不進去")
        }
        FacetUsage(Library(byCount), cache) { 1L }.markUsed(setOf("宜蘭"), emptySet()) // 不丟例外就算過
    }

    @Test fun 讀候選失敗照樣往外丟_抽屜才會顯示重試() = runTest {
        val library = object : FakeLibraryRepo() {
            override suspend fun filterOptions(upToMonth: String?): List<FilterOption> = throw IllegalStateException("library.db 壞了")
        }
        val result = runCatching { FacetUsage(library, FakeCacheRepo()) { 1L }.filterOptions(null) }
        assertTrue(result.isFailure)
    }
}
