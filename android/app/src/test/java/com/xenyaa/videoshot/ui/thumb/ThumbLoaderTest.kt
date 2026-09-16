package com.xenyaa.videoshot.ui.thumb

import androidx.compose.ui.graphics.ImageBitmap
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ThumbLoaderTest {

    private class FakeThumbs(var source: (ShotRow) -> ThumbSource) : Thumbs {
        override suspend fun thumbFor(shot: ShotRow): ThumbSource = source(shot)
        override fun fileOf(key: ThumbKey): File = File("/unused")
        override fun exists(key: ThumbKey): Boolean = false
        override suspend fun delete(key: ThumbKey) = Unit
        override suspend fun deleteVideo(videoId: String) = Unit
    }

    private fun row(id: Long, videoId: String = "v1") = ShotRow(
        id = id, videoId = videoId, atSec = 12.0, source = "storyboard",
        frameIndex = 3, sbLevel = 3, eventDate = "2026-03-01", place = null, description = null,
    )

    @Test
    fun 本機檔案走檔案解碼() = runTest {
        var files = 0
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = { files++; ImageBitmap(1, 1) },
            decodeBytes = { null },
            cover = { null },
        )
        assertNotNull(loader.load(row(1)))
        assertEquals(1, files)
    }

    @Test
    fun 手動圖走_bytes_解碼() = runTest {
        var bytes = 0
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.Bytes(byteArrayOf(1)) },
            decodeFile = { null },
            decodeBytes = { bytes++; ImageBitmap(1, 1) },
            cover = { null },
        )
        assertNotNull(loader.load(row(1)))
        assertEquals(1, bytes)
    }

    @Test
    fun 缺圖時退回封面() = runTest {
        var covers = mutableListOf<String>()
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.Cover(it.videoId) },
            decodeFile = { null },
            decodeBytes = { null },
            cover = { covers += it; ImageBitmap(1, 1) },
        )
        assertNotNull(loader.load(row(1, "abc")))
        assertEquals(listOf("abc"), covers)
    }

    /** 無法取回的圖回 null，畫面自己畫中性預留圖（手冊 §一「不是破圖」）。 */
    @Test
    fun 預留圖回_null() = runTest {
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.Placeholder },
            decodeFile = { null }, decodeBytes = { null }, cover = { null },
        )
        assertNull(loader.load(row(1)))
    }

    @Test
    fun 同一張只解碼一次_而且拿到同一個_bitmap() = runTest {
        var files = 0
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = { files++; ImageBitmap(1, 1) },
            decodeBytes = { null }, cover = { null },
        )
        val first = loader.load(row(1))
        val second = loader.load(row(1))
        assertEquals(1, files)
        assertSame(first, second)
    }

    /**
     * 捲動時同一格會被問很多次；重複的請求要合併，不能各自去解碼一次。
     *
     * `decodeFile` 卡在 [gate] 上真的掛起，逼八個呼叫者同時卡住——不這樣的話
     * `StandardTestDispatcher` 會讓第一個呼叫者跑到底、寫完快取，剩下七個全部走快取命中，
     * 完全沒摸到 `inFlight` 的合併邏輯，測試就算拿掉合併機制也一樣會過。
     */
    @Test
    fun 併發請求同一張只解碼一次() = runTest {
        var files = 0
        val gate = CompletableDeferred<Unit>()
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = { files++; gate.await(); ImageBitmap(1, 1) },
            decodeBytes = { null }, cover = { null },
        )
        val jobs = (1..8).map { async { loader.load(row(1)) } }
        // 讓排進去的八個呼叫者全部真的跑到掛起點：第一個卡在 gate.await()，
        // 其餘七個卡在同一張 pending deferred 上——這就是「證明八個都同時在等」。
        advanceUntilIdle()
        assertEquals(1, files)
        gate.complete(Unit)
        val results = jobs.awaitAll()
        assertEquals(1, files)
        assertEquals(1, results.distinct().size)
    }

    /**
     * 捲動讓一格離開畫面時，`ThumbImage` 的 `produceState` coroutine 會被取消——這是正常操作，
     * 不是「這張圖解不出來」。兩件事都要驗證：
     *
     * 1. 取消要**往外丟**，不能被吞成 `null` 悄悄回傳——這正是分辨新／舊實作的地方：
     *    舊版 `catch (t: Throwable) { null }` 會把 `CancellationException` 一起接住、不重新丟出，
     *    `load()` 會直接回 `null`，這裡的 `fail(...)` 就會被跳過、測試對新舊兩版都通過，起不到防呆作用；
     *    新版多了 `catch (c: CancellationException) { ...; throw c }`，一定會讓這次 `load()` 往外丟例外。
     * 2. 快取不能被這次取消污染——下一次 `load` 要重新解碼、拿得到圖，不能因為上一次是被取消的
     *    就被誤判成「這張圖壞了」而卡住。
     */
    @Test
    fun 捲出畫面的取消要往外丟_而且快取不會被污染() = runTest {
        var calls = 0
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = {
                calls++
                if (calls == 1) throw CancellationException("捲出畫面")
                ImageBitmap(1, 1)
            },
            decodeBytes = { null }, cover = { null },
        )
        try {
            loader.load(row(1))
            fail("捲出畫面的取消要往外丟，不能被當成解碼失敗吞掉")
        } catch (expected: CancellationException) {
            // 預期中：coroutine 要以「已取消」正常收尾
        }
        val second = loader.load(row(1))
        assertNotNull(second)
        assertEquals(2, calls)
    }

    @Test
    fun 超過容量會淘汰最久沒用到的() = runTest {
        var files = 0
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = { files++; ImageBitmap(1, 1) },
            decodeBytes = { null }, cover = { null },
            maxEntries = 2,
        )
        loader.load(row(1)); loader.load(row(2)); loader.load(row(3))
        assertEquals(3, files)
        loader.load(row(1))          // 1 已經被擠掉，要重解一次
        assertEquals(4, files)
        loader.load(row(3))          // 3 還在
        assertEquals(4, files)
    }

    @Test
    fun 解碼失敗回_null_而不是往上丟() = runTest {
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = { error("壞掉的檔案") },
            decodeBytes = { null }, cover = { null },
        )
        assertNull(loader.load(row(1)))
    }

    /** 刪掉一張圖之後，同一個 id 不該還在快取裡。 */
    @Test
    fun evict_之後會重新載入() = runTest {
        var files = 0
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = { files++; ImageBitmap(1, 1) },
            decodeBytes = { null }, cover = { null },
        )
        loader.load(row(1))
        loader.evict(1)
        loader.load(row(1))
        assertEquals(2, files)
    }
}
