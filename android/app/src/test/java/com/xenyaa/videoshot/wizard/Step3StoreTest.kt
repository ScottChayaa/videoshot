package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun 全部取消與全選() {
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
    fun 沒有勾選時抽屜收合() {
        val s = store()
        s.selectNone()
        assertEquals(Common.None, s.state.value.placeField)
        assertEquals(0, s.state.value.selected.size)
    }

    // ---- 套用與【完成】的條件 ----

    @Test
    fun 套用之後清掉抽屜的差異() {
        val s = store()
        s.editPlace("冬山河")
        s.applyPatch()
        assertEquals(DetailsPatch(), s.state.value.patch)
    }

    @Test
    fun 換勾選會清掉還沒套用的編輯() {
        val s = store()
        s.editPlace("冬山河")
        s.toggle(0)
        assertEquals(DetailsPatch(), s.state.value.patch)
    }

    /** 沒改欄位也能套用：照現值確認，一樣算已完成（不然只想沿用預設時間的那幾張永遠填不完）。 */
    @Test
    fun 沒改欄位按套用就把勾選的標成已完成_值不變() {
        val s = store()
        s.selectNone(); s.toggle(1); s.toggle(2)
        s.applyPatch()
        assertEquals(setOf(0, 3), s.state.value.unappliedCells)
        assertEquals("2026-01-01", s.state.value.details[1]?.eventDate)
        assertEquals("已套用到 2 張", s.state.value.appliedNotice)
    }

    @Test
    fun 沒有勾選時套用什麼都不做() {
        val s = store()
        s.selectNone()
        s.applyPatch()
        assertEquals(0, s.state.value.appliedCount)
        assertEquals(null, s.state.value.appliedNotice)
    }

    @Test
    fun 還有沒套用的就不算全部填完() {
        assertFalse(store().state.value.allFilled)
    }

    @Test
    fun 全部套用過就算全部填完() {
        val s = store()
        s.applyPatch()
        assertTrue(s.state.value.allFilled)
    }

    /** 「未填 N」歸 0 才算：還沒套用的修改涵蓋剩下的也不算（2026-10-17 scott）。 */
    @Test
    fun 還有修改沒套用時就算涵蓋剩下的也不算全部填完() {
        val s = store()
        s.selectNone(); s.toggle(0); s.toggle(1)
        s.applyPatch()
        s.selectNone(); s.toggle(2); s.toggle(3)
        s.editPlace("冬山河")
        assertFalse(s.state.value.allFilled)
        s.applyPatch()
        assertTrue(s.state.value.allFilled)
    }

    @Test
    fun 修改沒涵蓋剩下的就不算全部填完() {
        val s = store()
        s.selectNone(); s.toggle(0)
        s.editPlace("冬山河")
        assertFalse(s.state.value.allFilled)
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

    /** 設計文件決定 6：套用後提示「已套用到 N 張」，N 是剛剛那一下套用的張數。 */
    @Test
    fun 套用後出現已套用到幾張() {
        val s = store(3)
        s.editPlace("加勒比海")
        s.applyPatch()
        assertEquals("已套用到 3 張", s.state.value.appliedNotice)
    }

    @Test
    fun 一動欄位或勾選就清掉已套用提示() {
        val s = store(3)
        s.editPlace("加勒比海"); s.applyPatch()
        s.toggle(s.state.value.cells.first().cell)
        assertNull(s.state.value.appliedNotice)
        s.selectAll(); s.editPlace("南寧"); s.applyPatch()
        s.editDescription("x")
        assertNull(s.state.value.appliedNotice)
        // 其餘會改變勾選的操作同理
        s.editPlace("y"); s.applyPatch()
        s.selectNone()
        assertNull(s.state.value.appliedNotice)
        s.selectAll(); s.editPlace("z"); s.applyPatch()
        s.invert()
        assertNull(s.state.value.appliedNotice)
        s.selectAll(); s.editPlace("w"); s.applyPatch()
        s.selectUnapplied()
        assertNull(s.state.value.appliedNotice)
    }

    @Test
    fun 還沒套用過沒有提示() {
        assertNull(store().state.value.appliedNotice)
    }

    @Test
    fun 套用時累積抽屜裡填的地點與標籤_沒動過的欄位不算() {
        val store = Step3Store(listOf(Step3Cell(0, 0.0, false), Step3Cell(1, 10.0, false)), defaultEventDate = "2026-01-01")
        store.selectNone(); store.toggle(0)
        store.editPlace(" 宜蘭 ")
        store.applyPatch()
        store.selectNone(); store.toggle(1) // 換勾第二張
        store.editTags(listOf("露營", "玩水"))
        store.applyPatch()
        store.editEventDate("2026-02-02") // 只改時間
        store.applyPatch()
        assertEquals(setOf("宜蘭"), store.state.value.usedPlaces)
        assertEquals(setOf("玩水", "露營"), store.state.value.usedTags)
    }

    @Test
    fun 清空地點不算使用() {
        val store = Step3Store(listOf(Step3Cell(0, 0.0, false)), defaultEventDate = "2026-01-01")
        store.selectNone(); store.toggle(0)
        store.editPlace("")
        store.applyPatch()
        assertEquals(emptySet<String>(), store.state.value.usedPlaces)
    }

    private fun storeWith(vararg initial: com.xenyaa.videoshot.core.details.ShotDetails): Step3Store {
        val s = Step3Store(initial.indices.map { Step3Cell(it, it * 10.0, false) }, defaultEventDate = "2026-01-01")
        s.restore(initial.indices.associateWith { initial[it] }, initial.indices.toSet())
        return s
    }

    private fun d(place: String? = null, tags: List<String> = emptyList()) =
        com.xenyaa.videoshot.core.details.ShotDetails("2026-01-01", place = place, tags = tags)

    @Test
    fun 抽屜送整份標籤時_圖上原本就有的不算使用() {
        val s = storeWith(d(tags = listOf("A", "B")))
        s.editTags(listOf("A", "B", "C"))
        s.applyPatch()
        assertEquals(setOf("C"), s.state.value.usedTags)
    }

    @Test
    fun 勾選的格子各有不同標籤_聯集裡有的都不算() {
        val s = storeWith(d(tags = listOf("A")), d(tags = listOf("B")))
        s.editTags(listOf("A", "B", "C"))
        s.applyPatch()
        assertEquals(setOf("C"), s.state.value.usedTags)
    }

    @Test
    fun 所有勾選格子地點都已經是它_不算使用() {
        val s = storeWith(d(place = "宜蘭"), d(place = "宜蘭"))
        s.editPlace("宜蘭")
        s.applyPatch()
        assertEquals(emptySet<String>(), s.state.value.usedPlaces)
    }

    @Test
    fun 至少一張勾選格子的地點不同_算使用() {
        val s = storeWith(d(place = "宜蘭"), d(place = "花蓮"))
        s.editPlace("宜蘭")
        s.applyPatch()
        assertEquals(setOf("宜蘭"), s.state.value.usedPlaces)
    }

    // ---- 欄位按鈕＋選擇器（2026-10-18 改版）----

    @Test
    fun 套用到勾選的那幾張_其他不變() {
        val s = store()
        s.selectNone(); s.toggle(0); s.toggle(1)
        s.applyToSelected(DetailsPatch(place = "冬山河"))
        val d = s.state.value.details
        assertEquals("冬山河", d[0]!!.place)
        assertEquals("冬山河", d[1]!!.place)
        assertNull(d[2]!!.place)
    }

    @Test
    fun 套用後勾選不變() {
        val s = store()
        s.selectNone(); s.toggle(2)
        s.applyToSelected(DetailsPatch(place = "冬山河"))
        assertEquals(setOf(2), s.state.value.selected)
    }

    @Test
    fun 沒動過的欄位維持各張原值() {
        val s = store()
        s.selectNone(); s.toggle(0)
        s.applyToSelected(DetailsPatch(place = "冬山河"))
        s.selectAll()
        s.applyToSelected(DetailsPatch(tags = listOf("玩水")))
        assertEquals("冬山河", s.state.value.details[0]!!.place)
        assertNull(s.state.value.details[1]!!.place)
        assertTrue(s.state.value.details.values.all { it.tags == listOf("玩水") })
    }

    @Test
    fun 沒有勾選時套用什麼都不做_新入口() {
        val s = store()
        s.selectNone()
        val before = s.state.value
        s.applyToSelected(DetailsPatch(place = "冬山河"))
        assertEquals(before, s.state.value)
    }

    @Test
    fun 空的修改什麼都不做() {
        val s = store()
        val before = s.state.value
        s.applyToSelected(DetailsPatch())
        assertEquals(before, s.state.value)
    }

    @Test
    fun 套用過的標成改過_批次編輯靠它決定寫回哪幾張() {
        val s = store()
        s.selectNone(); s.toggle(3)
        s.applyToSelected(DetailsPatch(description = "第一次下水"))
        assertTrue(s.state.value.details[3]!!.applied)
        assertFalse(s.state.value.details[0]!!.applied)
    }

    @Test
    fun 空字串是清除() {
        val s = store()
        s.applyToSelected(DetailsPatch(place = "冬山河", description = "x"))
        s.applyToSelected(DetailsPatch(place = "", description = ""))
        assertTrue(s.state.value.details.values.all { it.place == null && it.description == null })
    }

    @Test
    fun 時間一進來就每一張都有_其他欄位沒有() {
        val st = store().state.value
        assertTrue(st.isComplete(DetailField.DATE))
        assertFalse(st.isComplete(DetailField.PLACE))
        assertFalse(st.isComplete(DetailField.TAGS))
        assertFalse(st.isComplete(DetailField.DESCRIPTION))
    }

    @Test
    fun 只設了其中幾張不算每一張都有() {
        val s = store()
        s.selectNone(); s.toggle(0)
        s.applyToSelected(DetailsPatch(place = "冬山河", tags = listOf("玩水")))
        assertFalse(s.state.value.isComplete(DetailField.PLACE))
        assertFalse(s.state.value.isComplete(DetailField.TAGS))
        s.selectAll()
        s.applyToSelected(DetailsPatch(place = "冬山河", tags = listOf("玩水")))
        assertTrue(s.state.value.isComplete(DetailField.PLACE))
        assertTrue(s.state.value.isComplete(DetailField.TAGS))
    }

    @Test
    fun 空白描述不算有值() {
        assertFalse(ShotDetails("2026-01-01", description = "  ").has(DetailField.DESCRIPTION))
        assertTrue(ShotDetails("2026-01-01", description = "x").has(DetailField.DESCRIPTION))
    }

    @Test
    fun 每一張都有時間與地點才能完成() {
        val s = store()
        assertFalse(s.state.value.canFinish)
        s.selectNone(); s.toggle(0); s.toggle(1); s.toggle(2)
        s.applyToSelected(DetailsPatch(place = "冬山河"))
        assertFalse(s.state.value.canFinish)
        s.selectNone(); s.toggle(3)
        s.applyToSelected(DetailsPatch(place = "羅東"))
        assertTrue(s.state.value.canFinish)
    }

    @Test
    fun 沒有格子不能完成() {
        val s = Step3Store(emptyList(), defaultEventDate = "2026-01-01")
        assertFalse(s.state.value.canFinish)
    }

    @Test
    fun 新入口也累積這一輪用過的地點與標籤() {
        val s = store()
        s.applyToSelected(DetailsPatch(place = "冬山河", tags = listOf("玩水")))
        s.applyToSelected(DetailsPatch(description = "x"))
        assertEquals(setOf("冬山河"), s.state.value.usedPlaces)
        assertEquals(setOf("玩水"), s.state.value.usedTags)
    }

    @Test
    fun 新入口清除地點不算使用() {
        val s = store()
        s.applyToSelected(DetailsPatch(place = ""))
        assertEquals(emptySet<String>(), s.state.value.usedPlaces)
    }

    @Test
    fun 新入口_圖上原本就有的標籤不算使用() {
        val s = store()
        s.applyToSelected(DetailsPatch(tags = listOf("玩水")))
        val fresh = Step3Store(listOf(Step3Cell(0, 0.0, false)), defaultEventDate = "2026-01-01")
        fresh.restore(mapOf(0 to ShotDetails("2026-01-01", tags = listOf("玩水"))), setOf(0))
        fresh.applyToSelected(DetailsPatch(tags = listOf("玩水", "阿明")))
        assertEquals(setOf("阿明"), fresh.state.value.usedTags)
    }
}
