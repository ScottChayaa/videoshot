package com.xenyaa.videoshot.query

import com.xenyaa.videoshot.core.query.ParsedQuery
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.core.query.TagAlias
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryResolverTest {

    private val vocab = QueryVocabulary(places = listOf("宜蘭"), tags = listOf(TagAlias("露營")))

    @Test
    fun 沒有金鑰時直接走規則式() = runTest {
        val resolver = QueryResolver(
            gemini = FakeGeminiClient(ParsedQuery(places = listOf("不該被用到"))),
            geminiKey = { null },
            vocabulary = { vocab },
        )
        val result = resolver.resolve("宜蘭露營")
        assertEquals(QuerySource.Rule, result.source)
        assertEquals(listOf("宜蘭"), result.parsed.places)
    }

    @Test
    fun 有金鑰且Gemini成功時用Gemini的結果() = runTest {
        val gemini = FakeGeminiClient(ParsedQuery(places = listOf("加勒比海"), tags = listOf("夜潛"), keywords = listOf("大蝦")))
        val resolver = QueryResolver(gemini = gemini, geminiKey = { "k" }, vocabulary = { vocab })
        val result = resolver.resolve("加勒比海夜潛看到的大蝦")
        assertEquals(QuerySource.Gemini, result.source)
        assertEquals(listOf("加勒比海"), result.parsed.places)
        assertEquals("k", gemini.lastKey)
    }

    @Test
    fun Gemini解不出來時退回規則式() = runTest {
        val resolver = QueryResolver(gemini = FakeGeminiClient(null), geminiKey = { "k" }, vocabulary = { vocab })
        val result = resolver.resolve("宜蘭露營")
        assertEquals(QuerySource.Rule, result.source)
        assertEquals(listOf("宜蘭"), result.parsed.places)
    }

    @Test
    fun Gemini逾時時退回規則式() = runTest {
        // delayMs 遠大於 timeoutMs：kotlinx-coroutines-test 的虛擬時間讓這個測試不會真的等待
        val resolver = QueryResolver(
            gemini = FakeGeminiClient(ParsedQuery(places = listOf("不該被用到")), delayMs = 60_000),
            geminiKey = { "k" },
            vocabulary = { vocab },
            timeoutMs = 5_000,
        )
        val result = resolver.resolve("宜蘭露營")
        assertEquals(QuerySource.Rule, result.source)
    }

    @Test
    fun 詞彙表為空時規則式退回純關鍵字() = runTest {
        val resolver = QueryResolver(gemini = FakeGeminiClient(null), geminiKey = { null }, vocabulary = { QueryVocabulary(emptyList(), emptyList()) })
        val result = resolver.resolve("大蝦")
        assertEquals(listOf("大蝦"), result.parsed.keywords)
    }
}
