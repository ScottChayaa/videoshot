package com.xenyaa.videoshot.ui.home

import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStoreTest {

    private fun row(id: Long, date: String = "2026-03-01") = ShotRow(
        id = id, videoId = "v1", atSec = 1.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = date, place = null, description = null,
    )

    @Test
    fun 接上一頁會延長清單並記住游標() {
        val state = HomeStore.appendPage(
            HomeState(),
            Page(listOf(row(3), row(2)), ShotCursor("2026-03-01", 2)),
        )
        assertEquals(listOf(3L, 2L), state.items.map { it.id })
        assertEquals(ShotCursor("2026-03-01", 2), state.cursor)
        assertFalse(state.endReached)
        assertFalse(state.loading)
    }

    @Test
    fun 游標是_null_代表到底了_不再請求() {
        val state = HomeStore.appendPage(HomeState(), Page(listOf(row(1)), null))
        assertTrue(state.endReached)
    }

    @Test
    fun 第二頁接在第一頁後面() {
        val first = HomeStore.appendPage(HomeState(), Page(listOf(row(3)), ShotCursor("2026-03-01", 3)))
        val second = HomeStore.appendPage(first, Page(listOf(row(2), row(1)), null))
        assertEquals(listOf(3L, 2L, 1L), second.items.map { it.id })
    }

    /** 換篩選＝整份清單作廢，游標也要一起丟掉，否則新條件會接著舊游標往下撈。 */
    @Test
    fun 換篩選會清空清單與游標() {
        val loaded = HomeStore.appendPage(HomeState(), Page(listOf(row(1)), ShotCursor("2026-03-01", 1)))
        val reset = HomeStore.reset(loaded, upToMonth = "2026-01")
        assertEquals(emptyList<ShotRow>(), reset.items)
        assertEquals(null, reset.cursor)
        assertEquals("2026-01", reset.upToMonth)
        assertFalse(reset.endReached)
    }

    @Test
    fun 載入中不會重複觸發() {
        val loading = HomeStore.startLoading(HomeState())
        assertTrue(loading.loading)
        assertFalse(HomeStore.canLoadMore(loading))
        assertTrue(HomeStore.canLoadMore(HomeState()))
        assertFalse(HomeStore.canLoadMore(HomeState(endReached = true)))
    }

    // 16B：Lightbox 不再顯示總數，首頁／資料夾不再查總張數
    /** 刪一張：清單少一張。 */
    @Test
    fun 刪一張會更新清單() {
        val loaded = HomeStore.appendPage(HomeState(), Page(listOf(row(3), row(2), row(1)), null))
        val after = HomeStore.removeShot(loaded, 2)
        assertEquals(listOf(3L, 1L), after.items.map { it.id })
    }

    @Test
    fun 刪不存在的_id_不動任何東西() {
        val loaded = HomeStore.appendPage(HomeState(), Page(listOf(row(1)), null))
        assertEquals(loaded, HomeStore.removeShot(loaded, 999))
    }

    /** 就地編輯改了日期，那張圖要換到另一個月份底下（原型的「已移到 2026年1月」）。 */
    @Test
    fun 換掉一張之後仍然依日期由新到舊() {
        val loaded = HomeStore.appendPage(
            HomeState(),
            Page(listOf(row(3, "2026-03-01"), row(2, "2026-02-01"), row(1, "2026-01-01")), null),
        )
        val moved = HomeStore.replace(loaded, row(3, "2026-01-15"))
        assertEquals(listOf(2L, 3L, 1L), moved.items.map { it.id })
    }

    @Test
    fun 地點併在標題那一列_其他標籤另起第二列_而且縮圖帶著它在清單裡的位置() {
        val loaded = HomeStore.appendPage(
            HomeState(
                facets = mapOf(
                    "2026-03" to listOf(MonthFacet("宜蘭", "place", 1), MonthFacet("夜市", "tag", 1)),
                    "2026-01" to listOf(MonthFacet("台南", "place", 1)),
                ),
            ),
            Page(listOf(row(3, "2026-03-05"), row(2, "2026-03-01"), row(1, "2026-01-09")), null),
        )
        val slots = HomeStore.slots(loaded)
        assertEquals(
            // 2026-01 只有地點，地點已經在標題列裡，不另起第二列
            listOf("h-2026-03", "f-2026-03", "t-3", "t-2", "h-2026-01", "t-1"),
            slots.map { it.key },
        )
        assertEquals(listOf(0, 1, 2), slots.filterIsInstance<HomeSlot.Tile>().map { it.index })
    }

    @Test
    fun 沒有標籤的月份也只有標題一格() {
        val loaded = HomeStore.appendPage(HomeState(), Page(listOf(row(1, "2026-03-05")), null))
        assertEquals(listOf("h-2026-03", "t-1"), HomeStore.slots(loaded).map { it.key })
    }

    @Test
    fun 每一格都知道自己屬於哪個月() {
        val loaded = HomeStore.appendPage(
            HomeState(facets = mapOf("2026-03" to listOf(MonthFacet("夜市", "tag", 1)))),
            Page(listOf(row(3, "2026-03-05"), row(1, "2026-01-09")), null),
        )
        val slots = HomeStore.slots(loaded)
        assertEquals(listOf("2026-03", "2026-03", "2026-03", "2026-01", "2026-01"), slots.map { it.month })
    }

    @Test
    fun 找得到某個月的標題在第幾格() {
        val loaded = HomeStore.appendPage(
            HomeState(),
            Page(listOf(row(3, "2026-03-05"), row(1, "2026-01-09")), null),
        )
        val slots = HomeStore.slots(loaded)
        assertEquals(0, HomeStore.headerIndexOf(slots, "2026-03"))
        assertEquals(2, HomeStore.headerIndexOf(slots, "2026-01"))
        assertEquals(null, HomeStore.headerIndexOf(slots, "2025-05"))
    }

    @Test
    fun 依月份分組() {
        val loaded = HomeStore.appendPage(
            HomeState(),
            Page(listOf(row(3, "2026-03-05"), row(2, "2026-03-01"), row(1, "2026-01-09")), null),
        )
        val groups = HomeStore.groups(loaded)
        assertEquals(listOf("2026年3月", "2026年1月"), groups.map { it.label })
        assertEquals(listOf(2, 1), groups.map { it.items.size })
    }
}
