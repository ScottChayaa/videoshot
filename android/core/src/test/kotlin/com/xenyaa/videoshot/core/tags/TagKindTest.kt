package com.xenyaa.videoshot.core.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class TagKindTest {

    @Test
    fun 認得的id轉回對應的kind() {
        assertEquals(TagKind.PERSON, TagKind.byId("person"))
        assertEquals(TagKind.PET, TagKind.byId("pet"))
        assertEquals(TagKind.TOPIC, TagKind.byId("topic"))
        assertEquals(TagKind.OTHER, TagKind.byId("other"))
    }

    @Test
    fun 認不得的id退回其他() {
        // "place" 是地點（shot.place），不是 tag.kind 的合法值——規格第四節
        assertEquals(TagKind.OTHER, TagKind.byId("place"))
        assertEquals(TagKind.OTHER, TagKind.byId("不存在的值"))
    }

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
