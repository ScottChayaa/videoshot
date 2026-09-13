package com.xenyaa.videoshot.core.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeysetTest {

    @Test
    fun `編碼後再解碼得到同一個游標`() {
        val c = ShotCursor("2026-09-13", 4821L)
        assertEquals(c, decodeShotCursor(c.encode()))
    }

    @Test
    fun `格式不對回傳 null`() {
        assertNull(decodeShotCursor(""))
        assertNull(decodeShotCursor("沒有分隔符"))
        assertNull(decodeShotCursor("2026-09-13|不是數字"))
    }

    @Test
    fun `日期裡就算出現分隔符也不會解錯`() {
        assertEquals(ShotCursor("2026-09-13", 7L), decodeShotCursor("2026-09-13|7"))
    }
}
