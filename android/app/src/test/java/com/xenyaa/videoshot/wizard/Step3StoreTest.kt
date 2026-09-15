package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.details.DetailsPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step3StoreTest {

    private fun store(count: Int = 4) = Step3Store(
        cells = List(count) { Step3Cell(cell = it, atSec = it * 70.0, manual = false) },
        defaultEventDate = "2026-01-01",
    )

    @Test
    fun 進場預設全選() {
        val s = store()
        assertEquals(setOf(0, 1, 2, 3), s.state.value.selected)
    }

    @Test
    fun 每一張的時間預設帶入上傳日期() {
        assertTrue(store().state.value.details.values.all { it.eventDate == "2026-01-01" })
    }

    // ---- 快捷列（案例 11）----

    @Test
    fun 全不選與全選() {
        val s = store()
        s.selectNone()
        assertEquals(emptySet<Int>(), s.state.value.selected)
        s.selectAll()
        assertEquals(setOf(0, 1, 2, 3), s.state.value.selected)
    }

    @Test
    fun 反選() {
        val s = store()
        s.selectNone()
        s.toggle(1)
        s.invert()
        assertEquals(setOf(0, 2, 3), s.state.value.selected)
    }

    @Test
    fun 未填的只勾沒有綠點的() {
        val s = store()
        s.selectNone()
        s.toggle(0)
        s.toggle(1)
        s.editPlace("冬山河")
        s.applyPatch()
        s.selectUnapplied()
        assertEquals(setOf(2, 3), s.state.value.selected)
    }

    // ---- 抽屜（案例 10）----

    @Test
    fun 勾選多張且值一致時顯示那個值() {
        val s = store()
        s.editPlace("冬山河")
        s.applyPatch()
        assertEquals(Common.One("冬山河"), s.state.value.placeField)
    }

    @Test
    fun 值不一致時是多個值() {
        val s = store()
        s.selectNone()
        s.toggle(0)
        s.editPlace("冬山河")
        s.applyPatch()
        s.selectAll()
        assertEquals(Common.Mixed, s.state.value.placeField)
    }

    @Test
    fun 只勾一張時抽屜標題帶時間() {
        val s = store()
        s.selectNone()
        s.toggle(1)
        assertEquals("套用到已選的 1 張 · 01:10", s.state.value.drawerTitle)
    }

    @Test
    fun 勾多張時抽屜標題只有張數() {
        assertEquals("套用到已選的 4 張", store().state.value.drawerTitle)
    }

    @Test
    fun 沒有勾選時抽屜收合() {
        val s = store()
        s.selectNone()
        assertEquals(Common.None, s.state.value.placeField)
        assertEquals(0, s.state.value.selected.size)
    }

    // ---- 主按鈕與提示行 ----

    @Test
    fun 沒動過欄位時主按鈕是完成() {
        assertEquals("完成", store().state.value.mainButtonLabel)
        assertEquals(null, store().state.value.hintLine)
    }

    @Test
    fun 動了欄位主按鈕變成套用到N張() {
        val s = store()
        s.editPlace("冬山河")
        assertEquals("套用到 4 張", s.state.value.mainButtonLabel)
        assertEquals("將更新：地點　其他欄位維持各張原值", s.state.value.hintLine)
    }

    @Test
    fun 主按鈕的字與它真正做的事永遠一致() {
        val s = store()
        assertTrue(s.state.value.mainActionIsFinish)
        assertEquals("完成", s.state.value.mainButtonLabel)
        s.editPlace("冬山河")
        assertFalse(s.state.value.mainActionIsFinish)
        assertEquals("套用到 4 張", s.state.value.mainButtonLabel)
    }

    @Test
    fun 套用之後主按鈕變回完成() {
        val s = store()
        s.editPlace("冬山河")
        s.applyPatch()
        assertEquals("完成", s.state.value.mainButtonLabel)
        assertEquals(DetailsPatch(), s.state.value.patch)
    }

    @Test
    fun 換勾選會清掉還沒套用的編輯() {
        val s = store()
        s.editPlace("冬山河")
        s.toggle(0)
        assertEquals(DetailsPatch(), s.state.value.patch)
        assertEquals("完成", s.state.value.mainButtonLabel)
    }

    // ---- 標題與進度 ----

    @Test
    fun 標題數得出總張數與已完成() {
        val s = store()
        s.selectNone()
        s.toggle(0)
        s.editPlace("冬山河")
        s.applyPatch()
        assertEquals("4 張 · 1 已完成", s.state.value.headerText)
    }

    @Test
    fun 裁圖進度講得出幾分之幾() {
        val s = store()
        s.setCropProgress(done = 2, total = 4)
        assertEquals("處理縮圖 2/4", s.state.value.progressText)
        s.finishCropping()
        assertEquals(null, s.state.value.progressText)
    }

    @Test
    fun 還原草稿會蓋掉預設的勾選與圖資() {
        val s = store()
        s.restore(
            details = mapOf(0 to com.xenyaa.videoshot.core.details.ShotDetails("2025-07-12", place = "冬山河", applied = true)),
            selected = setOf(0),
        )
        assertEquals(setOf(0), s.state.value.selected)
        assertEquals("冬山河", s.state.value.details[0]!!.place)
        // 草稿沒記到的格子仍要有預設值，否則入庫時 event_date 會是 null
        assertEquals("2026-01-01", s.state.value.details[3]!!.eventDate)
    }
}
