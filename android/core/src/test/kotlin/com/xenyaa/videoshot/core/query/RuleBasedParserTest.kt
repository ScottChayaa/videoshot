package com.xenyaa.videoshot.core.query

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedParserTest {

    private val vocab = QueryVocabulary(
        places = listOf("加勒比海", "佛羅里達"),
        tags = listOf(
            TagAlias("夜潛"),
            TagAlias("露營"),
            TagAlias("小橘", aliases = listOf("我家的貓", "橘貓")),
        ),
    )

    @Test
    fun 地點與標籤都命中_剩餘字串當關鍵字() {
        val p = RuleBasedParser.parse("加勒比海夜潛看到的大蝦", vocab)
        assertEquals(listOf("加勒比海"), p.places)
        assertEquals(listOf("夜潛"), p.tags)
        // 規則式不懂語意，剩餘字串是機械式的「扣掉命中子字串之後還剩下什麼」——
        // 跟規格第八節那個示範例子裡 Gemini 解出的 ["大蝦"] 不同，這是預期的落差（見本檔案 Global Constraints）
        assertEquals(listOf("看到的大蝦"), p.keywords)
        assertEquals(null, p.dateFrom)
        assertEquals(null, p.dateTo)
    }

    @Test
    fun 別名視同本名() {
        val p = RuleBasedParser.parse("我家的貓在睡覺", vocab)
        assertEquals(listOf("小橘"), p.tags)
    }

    @Test
    fun 最長比對優先_不會被較短的子字串搶先() {
        // 「小橘」本身也是候選詞之一，但「我家的貓」比它長，長度優先比對出來的應該是別名整串
        val p = RuleBasedParser.parse("我家的貓", vocab)
        assertEquals(listOf("小橘"), p.tags)
        assertEquals(emptyList<String>(), p.keywords)
    }

    @Test
    fun 沒有任何地點或標籤命中_整句當關鍵字() {
        val p = RuleBasedParser.parse("大蝦", vocab)
        assertEquals(emptyList<String>(), p.places)
        assertEquals(emptyList<String>(), p.tags)
        assertEquals(listOf("大蝦"), p.keywords)
    }

    @Test
    fun 兩個字的關鍵字也解得出來_對應手冊驗收案例24() {
        val p = RuleBasedParser.parse("大蝦", vocab)
        assertEquals(listOf("大蝦"), p.keywords)
    }

    @Test
    fun 空白字串沒有關鍵字() {
        val p = RuleBasedParser.parse("   ", vocab)
        assertEquals(emptyList<String>(), p.keywords)
    }

    @Test
    fun 多個標籤都命中() {
        val p = RuleBasedParser.parse("夜潛露營", vocab)
        assertEquals(setOf("夜潛", "露營"), p.tags.toSet())
    }

    @Test
    fun 只命中一次地點_即使地點字串重複出現() {
        // place 欄位在 shot 只有一個值，找到第一個地點候選就夠，不必再找第二個地點候選
        val p = RuleBasedParser.parse("加勒比海到加勒比海的旅程", vocab)
        assertEquals(listOf("加勒比海"), p.places)
    }

    @Test
    fun 地點別名對應到本名() {
        val vocab = QueryVocabulary(
            places = listOf("宜蘭礁溪"),
            tags = emptyList(),
            placeAliases = mapOf("宜蘭礁溪" to listOf("礁溪")),
        )
        val parsed = RuleBasedParser.parse("礁溪的溫泉", vocab)
        assertEquals(listOf("宜蘭礁溪"), parsed.places)
        assertEquals(listOf("溫泉"), parsed.keywords)
    }
}
