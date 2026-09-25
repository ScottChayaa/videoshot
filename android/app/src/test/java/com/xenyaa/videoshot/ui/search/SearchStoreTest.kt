package com.xenyaa.videoshot.ui.search

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStoreTest {

    private fun row(id: Long) = ShotRow(
        id = id, videoId = "v1", atSec = id.toDouble(), source = "storyboard", frameIndex = id.toInt(),
        sbLevel = 3, eventDate = "2026-03-01", place = null, description = null,
    )

    @Test
    fun 標籤模式沒選任何條件時查詢按鈕停用且有原因() {
        val s = SearchState(mode = SearchMode.TAG)
        assertFalse(SearchStore.canQuery(s))
        assertEquals("先選一個以上的標籤或地點", SearchStore.disabledReason(s))
        assertEquals("查詢", SearchStore.queryButtonLabel(s))
    }

    @Test
    fun 標籤模式選了條件按鈕文字帶數量() {
        val s = SearchState(mode = SearchMode.TAG, selected = setOf("place:宜蘭", "tag:露營"))
        assertTrue(SearchStore.canQuery(s))
        assertNull(SearchStore.disabledReason(s))
        assertEquals("查詢 2 個條件", SearchStore.queryButtonLabel(s))
    }

    @Test
    fun 文字模式沒輸入時停用() {
        val s = SearchState(mode = SearchMode.TEXT, textQuery = "  ")
        assertFalse(SearchStore.canQuery(s))
        assertEquals("請輸入想找的描述", SearchStore.disabledReason(s))
    }

    @Test
    fun toggleFacet切換選取狀態() {
        val facet = MonthFacet("宜蘭", "place", 3)
        val s1 = SearchStore.toggleFacet(SearchState(), facet)
        assertEquals(setOf("place:宜蘭"), s1.selected)
        val s2 = SearchStore.toggleFacet(s1, facet)
        assertEquals(emptySet<String>(), s2.selected)
    }

    @Test
    fun loadedFacets_超過30筆標記顯示更多並只留前30筆() {
        val facets = (1..31).map { MonthFacet("地點$it", "place", 32 - it) }
        val s = SearchStore.loadedFacets(SearchState(facetsLoading = true), facets)
        assertEquals(30, s.facets.size)
        assertTrue(s.facetsHasMore)
        assertFalse(s.facetsLoading)
    }

    @Test
    fun loadedFacets_換時間範圍後不在池子裡的選取會被清掉() {
        val before = SearchState(selected = setOf("place:宜蘭", "place:台北"))
        val s = SearchStore.loadedFacets(before, listOf(MonthFacet("宜蘭", "place", 1)))
        assertEquals(setOf("place:宜蘭"), s.selected)
    }

    @Test
    fun loadedFacets_顯示更多時可以傳更大的limit() {
        val facets = (1..31).map { MonthFacet("地點$it", "place", 32 - it) }
        val s = SearchStore.loadedFacets(SearchState(), facets, limit = 500)
        assertEquals(31, s.facets.size)
        assertFalse(s.facetsHasMore)
    }

    @Test
    fun setUpToMonth_清掉舊的facets等重新載入() {
        val before = SearchState(upToMonth = null, facets = listOf(MonthFacet("宜蘭", "place", 1)))
        val s = SearchStore.setUpToMonth(before, "2026-01")
        assertEquals("2026-01", s.upToMonth)
        assertEquals(emptyList<MonthFacet>(), s.facets)
        assertFalse(s.facetsHasMore)
        assertTrue(s.facetsLoading)
    }

    @Test
    fun startTagSearch_進入結果階段並清空舊結果() {
        val before = SearchState(results = listOf(row(1)), phase = SearchPhase.CONDITIONS)
        val s = SearchStore.startTagSearch(before)
        assertEquals(SearchPhase.RESULTS, s.phase)
        assertEquals(emptyList<ShotRow>(), s.results)
        assertNull(s.heard)
    }

    @Test
    fun startTextSearch_帶入聽懂了摘要() {
        val s = SearchStore.startTextSearch(SearchState(), ResolvedSummary("加勒比海・夜潛・大蝦", local = true))
        assertEquals(SearchPhase.RESULTS, s.phase)
        assertEquals("加勒比海・夜潛・大蝦", s.heard?.text)
        assertTrue(s.heard!!.local)
    }

    @Test
    fun appendTagPage_累加並設定endReached() {
        val s1 = SearchStore.appendTagPage(SearchState(), Page(listOf(row(1), row(2)), ShotCursor("2026-03-01", 1)), total = 5)
        assertEquals(2, s1.results.size)
        assertFalse(s1.endReached)
        val s2 = SearchStore.appendTagPage(s1, Page(listOf(row(3)), null), total = 5)
        assertEquals(3, s2.results.size)
        assertTrue(s2.endReached)
        assertEquals(5, s2.total)
    }

    @Test
    fun showConditions_回到條件階段但保留選取() {
        val before = SearchState(phase = SearchPhase.RESULTS, selected = setOf("place:宜蘭"), results = listOf(row(1)))
        val s = SearchStore.showConditions(before)
        assertEquals(SearchPhase.CONDITIONS, s.phase)
        assertEquals(setOf("place:宜蘭"), s.selected)
    }

    @Test
    fun canLoadMore_結果階段且未讀取中未到底才可以() {
        assertTrue(SearchStore.canLoadMore(SearchState(phase = SearchPhase.RESULTS)))
        assertFalse(SearchStore.canLoadMore(SearchState(phase = SearchPhase.CONDITIONS)))
        assertFalse(SearchStore.canLoadMore(SearchState(phase = SearchPhase.RESULTS, resultsLoading = true)))
        assertFalse(SearchStore.canLoadMore(SearchState(phase = SearchPhase.RESULTS, endReached = true)))
    }

    /** 刪一張：清單少一張、總數也要跟著少，理由同 `HomeStore.removeShot`（Task 12 覆查 Important 1）。 */
    @Test
    fun 刪一張會同時更新清單與總數() {
        val loaded = SearchStore.appendTagPage(SearchState(), Page(listOf(row(3), row(2), row(1)), null), total = 3)
        val after = SearchStore.removeShot(loaded, 2)
        assertEquals(listOf(3L, 1L), after.results.map { it.id })
        assertEquals(2, after.total)
    }

    @Test
    fun 刪不存在的_id_不動任何東西() {
        val loaded = SearchStore.appendTagPage(SearchState(), Page(listOf(row(1)), null), total = 1)
        assertEquals(loaded, SearchStore.removeShot(loaded, 999))
    }

    /** 標籤模式依日期排序，就地編輯改了日期要換到正確的位置，跟 `HomeStore.replace` 同一個理由。 */
    @Test
    fun 標籤模式換掉一張之後仍然依日期由新到舊() {
        fun rowAt(id: Long, date: String) = ShotRow(
            id = id, videoId = "v1", atSec = id.toDouble(), source = "storyboard", frameIndex = id.toInt(),
            sbLevel = 3, eventDate = date, place = null, description = null,
        )
        val loaded = SearchStore.appendTagPage(
            SearchState(mode = SearchMode.TAG),
            Page(listOf(rowAt(3, "2026-03-01"), rowAt(2, "2026-02-01"), rowAt(1, "2026-01-01")), null),
            total = 3,
        )
        val moved = SearchStore.replace(loaded, rowAt(3, "2026-01-15"))
        assertEquals(listOf(2L, 3L, 1L), moved.results.map { it.id })
    }

    /**
     * 文字模式的順序是查詢當下的相關度，不是日期——就地編輯不重新排序，只換資料本身
     * （見 `SearchStore.replace` KDoc 對這個限制的說明）。
     */
    @Test
    fun 文字模式換掉一張不會重新排序只換資料() {
        val loaded = SearchStore.appendTextPage(
            SearchState(mode = SearchMode.TEXT),
            com.xenyaa.videoshot.data.repo.model.SearchPage(listOf(row(3), row(2), row(1)), null),
            total = 3,
        )
        val edited = row(2).copy(place = "宜蘭")
        val after = SearchStore.replace(loaded, edited)
        // 順序不變（還是 3、2、1），但第二筆的資料已經換成新的
        assertEquals(listOf(3L, 2L, 1L), after.results.map { it.id })
        assertEquals("宜蘭", after.results[1].place)
    }

    @Test
    fun 換不存在的_id_不動任何東西() {
        val loaded = SearchStore.appendTagPage(SearchState(), Page(listOf(row(1)), null), total = 1)
        assertEquals(loaded, SearchStore.replace(loaded, row(999)))
    }
}
