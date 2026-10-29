package com.xenyaa.videoshot.publish

import com.xenyaa.videoshot.core.backup.UploadChunk
import com.xenyaa.videoshot.core.publish.UploadErrorKind
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OkHttpYoutubeUploaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var uploader: OkHttpYoutubeUploader

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        uploader = OkHttpYoutubeUploader(
            http = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                // 關掉 OkHttp 的連線失敗透明重試，斷線才會原樣丟出 IOException（正式環境保留預設值）
                .retryOnConnectionFailure(false)
                .build(),
            io = Dispatchers.IO,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After fun tearDown() { server.close() }

    private fun sessionUrl() = server.url("/session/abc").toString()

    @Test
    fun 開工作階段送非公開與清洗後的標題() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Location", sessionUrl()).build())

        val url = uploader.startSession("tok", "a＜b＞", 1234L)

        assertEquals(sessionUrl(), url)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", recorded.method)
        assertEquals("/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status", recorded.target)
        assertEquals("Bearer tok", recorded.headers["Authorization"])
        assertEquals("video/mp4", recorded.headers["X-Upload-Content-Type"])
        assertEquals("1234", recorded.headers["X-Upload-Content-Length"])
        val body = Json.parseToJsonElement(recorded.body!!.utf8()).jsonObject
        val snippet = body["snippet"]!!.jsonObject
        val status = body["status"]!!.jsonObject
        assertEquals("a＜b＞", snippet["title"]!!.jsonPrimitive.content)
        assertEquals("", snippet["description"]!!.jsonPrimitive.content)
        assertEquals("22", snippet["categoryId"]!!.jsonPrimitive.content)
        assertEquals("unlisted", status["privacyStatus"]!!.jsonPrimitive.content)
        assertFalse(status["selfDeclaredMadeForKids"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun 查詢送空body與Content_Range() = runBlocking {
        server.enqueue(MockResponse.Builder().code(308).build())

        uploader.query(sessionUrl(), "tok", 5000L)

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("PUT", recorded.method)
        assertEquals("bytes */5000", recorded.headers["Content-Range"])
        assertEquals("Bearer tok", recorded.headers["Authorization"])
        assertEquals(0, recorded.body?.size ?: 0)
    }

    @Test
    fun `308回已收到的位元組`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(308).addHeader("Range", "bytes=0-8388607").build())

        assertEquals(UploadStatus.Partial(8_388_608L), uploader.query(sessionUrl(), "tok", 20_000_000L))
    }

    @Test
    fun `308沒有Range是從0開始`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(308).build())

        assertEquals(UploadStatus.Partial(0L), uploader.query(sessionUrl(), "tok", 100L))
    }

    @Test
    fun 查詢時已傳完回影片編號() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("""{"id":"abc"}""").build())

        assertEquals(UploadStatus.Done("abc"), uploader.query(sessionUrl(), "tok", 100L))
    }

    @Test
    fun 傳一段送對範圍與內容() = runBlocking {
        val file = tmp.newFile("v.mp4").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        server.enqueue(MockResponse.Builder().code(308).addHeader("Range", "bytes=0-49").build())

        val status = uploader.putChunk(sessionUrl(), "tok", file, UploadChunk(10, 59, 100))

        assertEquals(UploadStatus.Partial(50L), status)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes 10-59/100", recorded.headers["Content-Range"])
        assertEquals("Bearer tok", recorded.headers["Authorization"])
        assertEquals(ByteArray(50) { (it + 10).toByte() }.toList(), recorded.body!!.toByteArray().toList())
    }

    @Test
    fun 最後一段回201給影片編號() = runBlocking {
        val file = tmp.newFile("v.mp4").apply { writeBytes(ByteArray(10)) }
        server.enqueue(MockResponse.Builder().code(201).body("""{"id":"vid1"}""").build())

        assertEquals(UploadStatus.Done("vid1"), uploader.putChunk(sessionUrl(), "tok", file, UploadChunk(0, 9, 10)))
    }

    @Test
    fun 沒頻道丟NO_CHANNEL() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401)
            .body("""{"error":{"errors":[{"reason":"youtubeSignupRequired"}]}}""").build())

        val e = runCatching { uploader.startSession("tok", "t", 1L) }.exceptionOrNull() as UploadHttpException
        assertEquals(UploadErrorKind.NO_CHANNEL, e.kind)
        assertEquals(401, e.code)
    }

    @Test
    fun 額度丟QUOTA() = runBlocking {
        server.enqueue(MockResponse.Builder().code(403)
            .body("""{"error":{"errors":[{"reason":"quotaExceeded"}]}}""").build())

        val e = runCatching { uploader.startSession("tok", "t", 1L) }.exceptionOrNull() as UploadHttpException
        assertEquals(UploadErrorKind.QUOTA, e.kind)
    }

    @Test
    fun 其他狀態碼照分類丟例外() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(503).build())

        val gone = runCatching { uploader.query(sessionUrl(), "tok", 1L) }.exceptionOrNull() as UploadHttpException
        val transient = runCatching { uploader.query(sessionUrl(), "tok", 1L) }.exceptionOrNull() as UploadHttpException
        assertEquals(UploadErrorKind.SESSION_GONE, gone.kind)
        assertEquals(UploadErrorKind.TRANSIENT, transient.kind)
    }

    @Test
    fun 斷線丟IOException() = runBlocking {
        val file = tmp.newFile("v.mp4").apply { writeBytes(ByteArray(2_000_000)) }
        server.enqueue(MockResponse.Builder().onRequestBody(SocketEffect.CloseSocket()).build())

        val e = runCatching {
            uploader.putChunk(sessionUrl(), "tok", file, UploadChunk(0, 1_999_999, 2_000_000))
        }.exceptionOrNull()
        assertEquals(true, e is IOException)
    }
}
