package com.xenyaa.videoshot.core.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderCursorTest {

    @Test
    fun 編碼後再解碼得到同一個游標() {
        val c = FolderCursor(1_763_000_000L, 4821L)
        assertEquals(c, decodeFolderCursor(c.encode()))
    }

    @Test
    fun 格式不對回傳_null() {
        assertNull(decodeFolderCursor(""))
        assertNull(decodeFolderCursor("沒有分隔符"))
        assertNull(decodeFolderCursor("不是數字|7"))
        assertNull(decodeFolderCursor("1763000000|不是數字"))
    }
}
