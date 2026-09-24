package com.xenyaa.videoshot.core.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCursorTest {

    @Test
    fun 編碼後解碼回原值() {
        val c = SearchCursor(relevance = 2, eventDate = "2026-03-01", id = 42L)
        assertEquals(c, decodeSearchCursor(c.encode()))
    }

    @Test
    fun 相關度可以是零或負一() {
        val c = SearchCursor(relevance = 0, eventDate = "2026-03-01", id = 1L)
        assertEquals(c, decodeSearchCursor(c.encode()))
    }

    @Test
    fun 格式不對回null() {
        assertNull(decodeSearchCursor("garbage"))
        assertNull(decodeSearchCursor("1|2026-03-01"))
        assertNull(decodeSearchCursor(""))
    }

    @Test
    fun relevance不是數字回null() {
        assertNull(decodeSearchCursor("x|2026-03-01|1"))
    }

    @Test
    fun id不是數字回null() {
        assertNull(decodeSearchCursor("1|2026-03-01|x"))
    }
}
