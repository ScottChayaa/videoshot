package com.xenyaa.videoshot.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGroupingTest {

    @Test
    fun 取月份與月份標籤() {
        assertEquals("2026-03", monthOf("2026-03-14"))
        assertEquals("2026年3月", monthLabel("2026-03"))
        // 個位數的月份不補零：原型的 fmtMonthLabel 用的是 parseInt
        assertEquals("2026年9月", monthLabel("2026-09"))
        assertEquals("2026年12月", monthLabel("2026-12"))
    }

    @Test
    fun 下個月的第一天_跨年也對() {
        assertEquals("2026-04-01", nextMonthStart("2026-03"))
        assertEquals("2027-01-01", nextMonthStart("2026-12"))
        assertEquals("2026-02-01", nextMonthStart("2026-01"))
    }

    /** 「只顯示 2026年3月 以前」＝ event_date < 2026-04-01，**含**該月整月。 */
    @Test
    fun 篩選邊界含當月最後一天() {
        val bound = nextMonthStart("2026-03")
        assert("2026-03-31" < bound)
        assert("2026-04-01" >= bound)
    }

    @Test
    fun 依月份分組並保持原順序() {
        val rows = listOf("2026-03-14", "2026-03-02", "2026-01-31", "2026-01-01")
        val groups = groupByMonth(rows) { monthOf(it) }
        assertEquals(listOf("2026-03", "2026-01"), groups.map { it.month })
        assertEquals(listOf("2026年3月", "2026年1月"), groups.map { it.label })
        assertEquals(listOf("2026-03-14", "2026-03-02"), groups.first().items)
    }

    /** 同一個月被中斷後再出現（理論上不會發生，但不能因此把資料吃掉）。 */
    @Test
    fun 不連續的同月份會各自成組() {
        val groups = groupByMonth(listOf("2026-03-14", "2026-01-31", "2026-03-02")) { monthOf(it) }
        assertEquals(listOf("2026-03", "2026-01", "2026-03"), groups.map { it.month })
    }

    @Test
    fun 空清單沒有任何分組() {
        assertEquals(emptyList<MonthGroup<String>>(), groupByMonth(emptyList<String>()) { it })
    }

    /** 手冊 §二「固定三欄」是手機；§零「600dp 以上會加欄」。 */
    @Test
    fun 欄數看寬度() {
        assertEquals(3, homeColumnsFor(360))
        assertEquals(3, homeColumnsFor(411))
        assertEquals(3, homeColumnsFor(599))
        assertEquals(5, homeColumnsFor(600))
        assertEquals(5, homeColumnsFor(800))
        assertEquals(6, homeColumnsFor(900))
    }
}
