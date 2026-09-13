package com.xenyaa.videoshot.youtube

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.core.youtube.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OkHttpYoutubeTest {

    private lateinit var server: MockWebServer
    private lateinit var youtube: OkHttpYoutube

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        youtube = OkHttpYoutube(
            // 明確給短逾時：測試不該因為網路層的預設 10 秒而拖長，連不上的案例也要快速失敗
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build(),
            io = Dispatchers.IO,
            watchUrl = { id -> server.url("/watch?v=$id").toString() },
        )
    }

    @After fun tearDown() { server.close() }

    private val okBody = """
        <!doctype html><html><body><script>var ytInitialPlayerResponse =
        {"playabilityStatus":{"status":"OK","playableInEmbed":true},
         "videoDetails":{"videoId":"v1","title":"測試片","author":"頻道","lengthSeconds":"60"},
         "microformat":{"playerMicroformatRenderer":{"publishDate":"2026-01-01T00:00:00Z","isUnlisted":false}},
         "storyboards":{"playerStoryboardSpecRenderer":{"spec":"https://i.ytimg.com/sb/v1/x|48#27#100#10#10#0#d#s"}}};
        </script></body></html>
    """.trimIndent()

    @Test
    fun 正常抓得到就回_OK() = runTest {
        server.enqueue(MockResponse(code = 200, body = okBody))
        val r = youtube.watchPage("v1")
        assertEquals(FetchResult.OK, r.result)
        assertEquals("測試片", r.meta!!.title)
    }

    @Test
    fun 帶了桌機版的_User_Agent() = runTest {
        server.enqueue(MockResponse(code = 200, body = okBody))
        youtube.watchPage("v1")
        // takeRequest() 沒有逾時參數時會無限期阻塞 —— 請求沒到就整個測試掛住
        val ua = server.takeRequest(5, TimeUnit.SECONDS)!!.headers["User-Agent"]
        assertTrue("UA=$ua", ua!!.contains("Mozilla/5.0"))
    }

    @Test
    fun HTTP_錯誤回_fetch_failed_而不是丟例外() = runTest {
        server.enqueue(MockResponse(code = 500))
        assertEquals(FetchResult.FETCH_FAILED, youtube.watchPage("v1").result)
    }

    @Test
    fun 連不上也回_fetch_failed() = runTest {
        server.close()
        assertEquals(FetchResult.FETCH_FAILED, youtube.watchPage("v1").result)
    }

    @Test
    fun sheet_下載得到位元組() = runTest {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        // 二進位 body 只能走 Builder —— MockResponse 的公開建構子第三個參數是 String
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(bytes)).build())
        assertArrayEquals(bytes, youtube.sheet(server.url("/sb/x.jpg").toString()))
    }

    @Test
    fun sheet_回_403_時丟_SheetForbidden() = runTest {
        server.enqueue(MockResponse(code = 403))
        val e = runCatching { youtube.sheet(server.url("/sb/x.jpg").toString()) }.exceptionOrNull()
        assertTrue("實際是 $e", e is SheetForbidden)
    }

    @Test
    fun sheet_其他錯誤丟一般例外() = runTest {
        server.enqueue(MockResponse(code = 500))
        val e = runCatching { youtube.sheet(server.url("/sb/x.jpg").toString()) }.exceptionOrNull()
        assertTrue("實際是 $e", e != null && e !is SheetForbidden)
    }
}
