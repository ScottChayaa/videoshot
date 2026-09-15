package com.xenyaa.videoshot.core.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotDetailsTest {

    private val base = ShotDetails(eventDate = "2026-01-01")

    // ---- 案例 9：沒動過的欄位不被覆蓋 ----

    @Test
    fun 只改標籤時地點與描述維持原值() {
        val d = base.copy(place = "冬山河", description = "下水前")
        val after = d.apply(DetailsPatch(tags = listOf("玩水")))
        assertEquals("冬山河", after.place)
        assertEquals("下水前", after.description)
        assertEquals(listOf("玩水"), after.tags)
    }

    @Test
    fun 標籤是整組覆蓋不是疊加() {
        val d = base.copy(tags = listOf("阿明", "露營"))
        assertEquals(listOf("玩水"), d.apply(DetailsPatch(tags = listOf("玩水"))).tags)
    }

    @Test
    fun 空字串代表清空而不是沒動過() {
        val d = base.copy(place = "冬山河", description = "下水前")
        val after = d.apply(DetailsPatch(place = ""))
        assertEquals(null, after.place)
        assertEquals("下水前", after.description)
    }

    @Test
    fun 套用過就有綠點空的patch不算套用() {
        assertFalse(base.apply(DetailsPatch()).applied)
        assertTrue(base.apply(DetailsPatch(place = "冬山河")).applied)
    }

    // ---- 案例 8：兩輪套用互不影響 ----

    @Test
    fun 只有勾選中的那幾張會變() {
        val all = (0..3).associateWith { base }
        val after = applyToCells(all, setOf(0, 1), DetailsPatch(place = "冬山河"))
        assertEquals("冬山河", after[0]!!.place)
        assertEquals("冬山河", after[1]!!.place)
        assertEquals(null, after[2]!!.place)
        assertEquals(null, after[3]!!.place)
    }

    @Test
    fun 第二輪套用不動到第一輪的那幾張() {
        val all = (0..3).associateWith { base }
        val round1 = applyToCells(all, setOf(0, 1), DetailsPatch(tags = listOf("露營")))
        val round2 = applyToCells(round1, setOf(2, 3), DetailsPatch(tags = listOf("玩水")))
        assertEquals(listOf("露營"), round2[0]!!.tags)
        assertEquals(listOf("玩水"), round2[2]!!.tags)
    }

    // ---- 案例 10：抽屜顯示共同值 ----

    @Test
    fun 值一致時給那個值() {
        assertEquals(Common.One("冬山河"), commonOf(listOf("冬山河", "冬山河")))
    }

    @Test
    fun 值不一致時是多個值() {
        assertEquals(Common.Mixed, commonOf(listOf("冬山河", "羅東")))
    }

    @Test
    fun 沒有勾選時是None() {
        assertEquals(Common.None, commonOf(emptyList<String>()))
    }

    @Test
    fun 只勾一張時直接給它現有的值() {
        assertEquals(Common.One<String?>(null), commonOf(listOf<String?>(null)))
    }

    @Test
    fun 標籤順序不同視為同一組() {
        val a = normalizeTags(listOf("玩水", "阿明"))
        val b = normalizeTags(listOf("阿明", "玩水"))
        assertEquals(Common.One(a), commonOf(listOf(a, b)))
    }

    @Test
    fun 標籤會去掉重複與空白() {
        // 排序後的順序照字元碼位：玩(U+73A9) 在 阿(U+963F) 前面
        assertEquals(listOf("玩水", "阿明"), normalizeTags(listOf("阿明", " 玩水 ", "阿明", "")))
    }

    // ---- 提示行與主按鈕 ----

    @Test
    fun 將更新的欄位依抽屜由上到下排列() {
        val patch = DetailsPatch(place = "冬山河", tags = listOf("玩水"))
        assertEquals(listOf("地點", "標籤"), patch.changedLabels)
    }

    @Test
    fun 全部沒動過就是空的patch() {
        assertTrue(DetailsPatch().isEmpty)
        assertFalse(DetailsPatch(description = "").isEmpty)
    }

    // ---- 時間欄位的預設值 ----

    @Test
    fun 時間預設取上傳日期的日期部分() {
        assertEquals("2014-11-10", eventDateOf("2014-11-10T06:05:55-08:00", fallback = "2026-01-01"))
    }

    @Test
    fun 上傳日期缺漏或看不懂時用備援值() {
        assertEquals("2026-01-01", eventDateOf("", fallback = "2026-01-01"))
        assertEquals("2026-01-01", eventDateOf("不是日期", fallback = "2026-01-01"))
    }
}
