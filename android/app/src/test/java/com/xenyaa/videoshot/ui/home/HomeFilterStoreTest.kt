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
    ) = FilterDraft(places, tags, query)

    @Test
    fun HomeFilter的isEmpty與size() {
        assertTrue(HomeFilter().isEmpty)
        assertEquals(0, HomeFilter().size)
        val f = HomeFilter(setOf("a", "b"), setOf("c"))
        assertFalse(f.isEmpty)
        assertEquals(3, f.size)
    }

    @Test
    fun 沒有搜尋字時只列前100個_其餘算隱藏數() {
        val options = (1..130).map { place("地點$it") } + (1..5).map { tag("標籤$it") }
        val section = FilterLists.visible(options, isPlace = true, draft = draft())
        assertEquals(100, section.shown.size)
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

    /** 已勾選的項目不能因為超過上限就看不到。 */
    @Test
    fun 超出前100個的已勾選項目照樣列出_其餘算隱藏數() {
        val options = (1..130).map { place("地點$it") }
        val section = FilterLists.visible(options, true, draft(places = setOf("地點120", "地點110", "地點10")))
        assertEquals((1..100).map { "地點$it" } + listOf("地點110", "地點120"), section.shown.map { it.name })
        // 後 30 個裡有 2 個已勾選、已經列出，剩 28 個沒列
        assertEquals(28, section.hiddenCount)
    }

    @Test
    fun 前100個內的已勾選項目維持原位() {
        val options = (1..130).map { place("地點$it") }
        val section = FilterLists.visible(options, true, draft(places = setOf("地點5")))
        assertEquals((1..100).map { "地點$it" }, section.shown.map { it.name })
        assertEquals(30, section.hiddenCount)
    }

    @Test
    fun 後面全是已勾選則隱藏數為零() {
        val options = (1..102).map { place("地點$it") }
        val section = FilterLists.visible(options, true, draft(places = setOf("地點101", "地點102")))
        assertEquals(102, section.shown.size)
        assertEquals(0, section.hiddenCount)
    }

    @Test
    fun 搜尋字比對名稱或別名_不分大小寫() {
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
    fun 搜尋結果也最多100個() {
        val options = (1..150).map { place("礁溪$it") }
        val section = FilterLists.visible(options, true, draft(query = mapOf(true to "礁溪", false to "")))
        assertEquals(100, section.shown.size)
        assertEquals(50, section.hiddenCount)
    }

    // ---- 分頁 ----

    @Test
    fun 依寬度排列_排不下就換列() {
        // 寬 100、間距 10：30+10+30+10+30=110 放不下第三顆
        val pages = paginateChips(listOf(30, 30, 30, 30), maxWidth = 100, gap = 10, rowsPerPage = 4)
        assertEquals(listOf(listOf(listOf(0, 1), listOf(2, 3))), pages)
    }

    @Test
    fun 剛好塞滿一列不換列() {
        val pages = paginateChips(listOf(30, 30, 20), maxWidth = 100, gap = 10, rowsPerPage = 4)
        assertEquals(listOf(listOf(listOf(0, 1, 2))), pages)
    }

    @Test
    fun 每頁最多幾列_多的換頁() {
        val pages = paginateChips(List(9) { 80 }, maxWidth = 100, gap = 10, rowsPerPage = 4)
        assertEquals(3, pages.size)
        assertEquals(listOf(4, 4, 1), pages.map { it.size })
        assertEquals(listOf(8), pages.last().single())
    }

    @Test
    fun 比整列還寬的自己佔一列() {
        val pages = paginateChips(listOf(30, 150, 30), maxWidth = 100, gap = 10, rowsPerPage = 4)
        assertEquals(listOf(listOf(listOf(0), listOf(1), listOf(2))), pages)
    }

    @Test
    fun 沒有東西時沒有頁() {
        assertEquals(emptyList<List<List<Int>>>(), paginateChips(emptyList(), 100, 10, 4))
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
