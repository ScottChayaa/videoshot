package com.xenyaa.videoshot.query

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OkHttpGeminiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpGeminiClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpGeminiClient(
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build(),
            io = Dispatchers.IO,
            apiUrl = { model -> server.url("/v1beta/models/$model:generateContent").toString() },
        )
    }

    @After fun tearDown() { server.close() }

    private fun geminiBody(innerJson: String) = """
        {"candidates":[{"content":{"parts":[{"text": ${okhttp3internalQuote(innerJson)} }]}}]}
    """.trimIndent()

    // MockResponse 的 body 是最外層 JSON 字串；inner text 本身也是 JSON，要手動跳脫
    private fun okhttp3internalQuote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun 解析成功時回傳ParsedQuery() = runTest {
        server.enqueue(MockResponse(code = 200, body = geminiBody(
            """{"date_from": null, "date_to": null, "places": ["加勒比海"], "tags": ["夜潛"], "keywords": ["大蝦"]}"""
        )))
        val result = client.parse("加勒比海夜潛看到的大蝦", "fake-key")
        assertEquals(listOf("加勒比海"), result?.places)
        assertEquals(listOf("夜潛"), result?.tags)
        assertEquals(listOf("大蝦"), result?.keywords)
    }

    @Test
    fun 金鑰放在query參數裡() = runTest {
        server.enqueue(MockResponse(code = 200, body = geminiBody("""{"places":[],"tags":[],"keywords":[]}""")))
        client.parse("x", "my-secret-key")
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        // 這個版本的 mockwebserver3（5.5.0）RecordedRequest 只有 `target`，沒有 `path` 屬性
        assertTrue(recorded.target?.contains("key=my-secret-key") == true)
    }

    @Test
    fun HTTP錯誤回null不丟例外() = runTest {
        server.enqueue(MockResponse(code = 403))
        assertNull(client.parse("x", "bad-key"))
    }

    @Test
    fun 回應不是預期的JSON時回null() = runTest {
        server.enqueue(MockResponse(code = 200, body = geminiBody("這不是 JSON")))
        assertNull(client.parse("x", "k"))
    }

    @Test
    fun 連不上回null() = runTest {
        server.close()
        assertNull(client.parse("x", "k"))
    }
}
