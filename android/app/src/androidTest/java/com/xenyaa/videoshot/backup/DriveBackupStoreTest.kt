package com.xenyaa.videoshot.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveBackupStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var store: DriveBackupStore

    private fun newStore(chunkSize: Long = 8L * 1024 * 1024) = DriveBackupStore(
        http = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build(),
        accessToken = { "fake-token" },
        io = Dispatchers.IO,
        baseUrl = server.url("/").toString().trimEnd('/'),
        chunkSize = chunkSize,
    )

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        store = newStore()
    }

    @After fun tearDown() { server.close() }

    @Test
    fun list解析files陣列() = runTest {
        server.enqueue(MockResponse(code = 200, body = """
            {"files":[
                {"id":"f1","name":"library-20260101-0000.db.gz","createdTime":"2026-01-01T00:00:00.000Z","size":"1024",
                 "appProperties":{"schemaVersion":"1","shotCount":"42","deviceName":"Pixel","sha256":"abc"}}
            ]}
        """.trimIndent()))

        val result = store.list()

        assertEquals(1, result.size)
        assertEquals("f1", result[0].id)
        assertEquals(1024L, result[0].sizeBytes)
        assertEquals(42, result[0].shotCount)
        assertEquals("abc", result[0].sha256)
    }

    @Test
    fun list帶上appDataFolder與授權標頭() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"files":[]}"""))
        store.list()
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.target?.contains("spaces=appDataFolder") == true)
        assertEquals("Bearer fake-token", recorded.headers["Authorization"])
    }

    @Test
    fun 上傳一次PUT就完成的小檔案() = runTest {
        val source = tmp.newFile("a.db.gz").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        server.enqueue(MockResponse(code = 200, headers = Headers.Builder().add("Location", server.url("/upload-session-1").toString()).build()))
        server.enqueue(MockResponse(code = 200, body = """{"id":"drive-1"}"""))

        val result = store.upload(NewBackup(source, "sha-a", 1, 5, "Pixel", 100))

        assertEquals("drive-1", result.id)
        assertEquals(source.name, result.name)
        assertEquals(100L, result.sizeBytes)
    }

    @Test
    fun 上傳分成兩個chunk_第一個回308第二個完成() = runTest {
        store = newStore(chunkSize = 50)
        val source = tmp.newFile("b.db.gz").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        server.enqueue(MockResponse(code = 200, headers = Headers.Builder().add("Location", server.url("/upload-session-2").toString()).build()))
        server.enqueue(MockResponse(code = 308, headers = Headers.Builder().add("Range", "bytes=0-49").build()))
        server.enqueue(MockResponse(code = 200, body = """{"id":"drive-2"}"""))

        val result = store.upload(NewBackup(source, "sha-b", 1, 5, "Pixel", 100))

        assertEquals("drive-2", result.id)
        // 三個請求：初始化 + 兩個 chunk
        server.takeRequest(5, TimeUnit.SECONDS)
        val firstChunk = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes 0-49/100", firstChunk.headers["Content-Range"])
        val secondChunk = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes 50-99/100", secondChunk.headers["Content-Range"])
    }

    @Test
    fun 上傳時第一個chunk連線中斷_續傳查詢回報還沒收完_從斷點繼續() = runTest {
        store = newStore(chunkSize = 50)
        val source = tmp.newFile("c.db.gz").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        server.enqueue(MockResponse(code = 200, headers = Headers.Builder().add("Location", server.url("/upload-session-3").toString()).build()))
        // 第一個 chunk 的 PUT 送到一半就斷線——client 端連 response code 都讀不到，
        // 直接在 execute() 丟 IOException（模擬真正網路中斷，不是 Drive 回應 308）。
        server.enqueue(
            MockResponse.Builder()
                .code(308)
                .addHeader("Range", "bytes=0-49")
                .onResponseStart(SocketEffect.CloseSocket())
                .build()
        )
        // 續傳查詢：Drive 說「還沒收完，目前收到 0-49」——這是 queryResumeOffset 的 StillUploading 分支。
        server.enqueue(MockResponse(code = 308, headers = Headers.Builder().add("Range", "bytes=0-49").build()))
        server.enqueue(MockResponse(code = 200, body = """{"id":"drive-3"}"""))

        val result = store.upload(NewBackup(source, "sha-c", 1, 5, "Pixel", 100))

        assertEquals("drive-3", result.id)
        // 四個請求：初始化 + 中斷的第一個 chunk + 續傳查詢 + 真正送出的第二個 chunk
        server.takeRequest(5, TimeUnit.SECONDS) // 初始化
        server.takeRequest(5, TimeUnit.SECONDS) // 中斷的第一個 chunk（client 沒收到回應，但 server 端有記錄到這個請求）
        val resumeQuery = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes */100", resumeQuery.headers["Content-Range"])
        val secondChunk = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes 50-99/100", secondChunk.headers["Content-Range"])
    }

    @Test
    fun 上傳最後一個chunk讀回應時斷線_續傳查詢回報其實已經收完_視為上傳成功() = runTest {
        store = newStore(chunkSize = 50)
        val source = tmp.newFile("d.db.gz").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        server.enqueue(MockResponse(code = 200, headers = Headers.Builder().add("Location", server.url("/upload-session-4").toString()).build()))
        server.enqueue(MockResponse(code = 308, headers = Headers.Builder().add("Range", "bytes=0-49").build()))
        // 最後一個 chunk：Drive 其實已經收完、回了 200 帶檔案資源，
        // 但 client 讀 body 讀到一半斷線——這是修正前會讓 nextChunk(total, total, ...)
        // 丟 IllegalArgumentException、把「其實已經成功」的上傳誤判成失敗的那個邊界情況。
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"id":"drive-4"}""")
                .onResponseBody(SocketEffect.CloseSocket())
                .build()
        )
        // 續傳查詢：Drive 說「其實已經收完了」，直接回檔案資源——這是 queryResumeOffset 的 Completed 分支。
        server.enqueue(MockResponse(code = 200, body = """{"id":"drive-4"}"""))

        val result = store.upload(NewBackup(source, "sha-d", 1, 5, "Pixel", 100))

        assertEquals("drive-4", result.id)
        server.takeRequest(5, TimeUnit.SECONDS) // 初始化
        server.takeRequest(5, TimeUnit.SECONDS) // 第一個 chunk（正常 308）
        server.takeRequest(5, TimeUnit.SECONDS) // 中斷的最後一個 chunk
        val resumeQuery = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("bytes */100", resumeQuery.headers["Content-Range"])
        assertEquals("PUT", resumeQuery.method)
    }

    @Test
    fun 下載寫入目的檔案() = runTest {
        server.enqueue(MockResponse(code = 200, body = "gz-bytes"))
        val dest = tmp.newFile("dest.db.gz")

        store.download("f1", dest)

        assertEquals("gz-bytes", dest.readText())
    }

    @Test
    fun 刪除送出DELETE() = runTest {
        server.enqueue(MockResponse(code = 204))
        store.delete("f1")
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("DELETE", recorded.method)
    }
}
