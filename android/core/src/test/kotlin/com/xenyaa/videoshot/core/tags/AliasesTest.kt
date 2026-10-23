package com.xenyaa.videoshot.core.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class AliasesTest {

    @Test
    fun 別名輸入去空白去重丟空字串() {
        assertEquals(listOf("我家的貓", "橘貓"), parseAliases(" 我家的貓 ,橘貓,,我家的貓"))
    }

    @Test
    fun 別名輸入保留原始順序不排序() {
        assertEquals(listOf("橘貓", "阿橘"), parseAliases("橘貓,阿橘"))
    }

    @Test
    fun 別名輸入空字串或只有逗號得到空清單() {
        assertEquals(emptyList<String>(), parseAliases(""))
        assertEquals(emptyList<String>(), parseAliases(" , , "))
    }
}
