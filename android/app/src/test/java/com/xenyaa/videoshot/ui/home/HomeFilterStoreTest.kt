package com.xenyaa.videoshot.ui.home

import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFilterStoreTest {

    private fun place(name: String, vararg aliases: String) = FilterOption(name, true, "other", aliases.toList())
    private fun tag(name: String, vararg aliases: String) = FilterOption(name, false, "topic", aliases.toList())
    private fun draft(
        places: Set<String> = emptySet(),
        tags: Set<String> = emptySet(),
        query: Map<Boolean, String> = mapOf(true to "", false to ""),
        expanded: Set<Boolean> = emptySet(),
    ) = FilterDraft(places, tags, query, expanded)

    @Test
    fun HomeFilter的isEmpty與size() {
        assertTrue(HomeFilter().isEmpty)
        assertEquals(0, HomeFilter().size)
        val f = HomeFilter(setOf("a", "b"), setOf("c"))
        assertFalse(f.isEmpty)
        assertEquals(3, f.size)
    }

    @Test
    fun 沒有搜尋字且未展開時只列前50個_其餘算隱藏數() {
        val options = (1..80).map { place("地點$it") } + (1..5).map { tag("標籤$it") }
        val section = FilterLists.visible(options, isPlace = true, draft = draft())
        assertEquals(50, section.shown.size)
        assertEquals("地點1", section.shown.first().name)
        assertEquals(30, section.hiddenCount)
        assertTrue(section.shown.all { it.isPlace })
    }

    @Test
    fun 已勾選但不在候選裡的補在最前面() {
        val options = listOf(place("礁溪"), place("宜蘭"))
        val section = FilterLists.visible(options, isPlace = true, draft = draft(places = setOf("墾丁")))
        assertEquals(listOf("墾丁", "礁溪", "宜蘭"), section.shown.map { it.name })
        assertEquals(FilterOption("墾丁", true, "other", emptyList()), section.shown.first())
        assertEquals(0, section.hiddenCount)
    }

    @Test
    fun 已勾選且在候選裡的維持原本位置() {
        val options = listOf(place("礁溪"), place("宜蘭"), place("墾丁"))
        val section = FilterLists.visible(options, isPlace = true, draft = draft(places = setOf("墾丁")))
        assertEquals(listOf("礁溪", "宜蘭", "墾丁"), section.shown.map { it.name })
    }

    @Test
    fun 搜尋字比對名稱或別名_不分大小寫_列出全部符合的() {
        val options = (1..60).map { place("礁溪$it") } +
            place("溫泉鄉", "礁溪") + place("Taipei", "台北") + place("其他")
        val byName = FilterLists.visible(options, true, draft(query = mapOf(true to "礁溪", false to "")))
        assertEquals(61, byName.shown.size)
        assertEquals(0, byName.hiddenCount)
        assertTrue(byName.shown.any { it.name == "溫泉鄉" })

        val byCase = FilterLists.visible(options, true, draft(query = mapOf(true to "TAIP", false to "")))
        assertEquals(listOf("Taipei"), byCase.shown.map { it.name })
    }

    @Test
    fun 已展開時全部列出() {
        val options = (1..80).map { tag("標籤$it") }
        val section = FilterLists.visible(options, isPlace = false, draft = draft(expanded = setOf(false)))
        assertEquals(80, section.shown.size)
        assertEquals(0, section.hiddenCount)
    }

    @Test
    fun 展開只影響自己那一區() {
        val options = (1..80).map { place("地點$it") }
        val section = FilterLists.visible(options, isPlace = true, draft = draft(expanded = setOf(false)))
        assertEquals(50, section.shown.size)
        assertEquals(30, section.hiddenCount)
    }

    @Test
    fun 地點區與標籤區各看各的() {
        val options = listOf(place("礁溪"), tag("溫泉"), place("宜蘭"), tag("夜市"))
        assertEquals(listOf("礁溪", "宜蘭"), FilterLists.visible(options, true, draft()).shown.map { it.name })
        assertEquals(listOf("溫泉", "夜市"), FilterLists.visible(options, false, draft()).shown.map { it.name })
    }

    @Test
    fun 月份標籤轉成篩選候選() {
        val p = MonthFacet("礁溪", "place", 3).toFilterOption()
        assertEquals(FilterOption("礁溪", true, "other", emptyList()), p)
        val t = MonthFacet("小黑", "tag", 2, tagKind = "animal").toFilterOption()
        assertEquals(FilterOption("小黑", false, "animal", emptyList()), t)
    }
}
