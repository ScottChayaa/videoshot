package com.xenyaa.videoshot.ui.thumb

import androidx.compose.ui.graphics.ImageBitmap
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ThumbLoaderTest {

    private class FakeThumbs(var source: (ShotRow) -> ThumbSource) : Thumbs {
        override suspend fun thumbFor(shot: ShotRow): ThumbSource = source(shot)
        override fun fileOf(key: ThumbKey): File = File("/unused")
        override fun exists(key: ThumbKey): Boolean = false
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

    /** 捲動時同一格會被問很多次；重複的請求要合併，不能各自去解碼一次。 */
    @Test
    fun 併發請求同一張只解碼一次() = runTest {
        var files = 0
        val loader = ThumbLoader(
            thumbs = FakeThumbs { ThumbSource.LocalFile(File("/a.webp")) },
            decodeFile = { files++; ImageBitmap(1, 1) },
            decodeBytes = { null }, cover = { null },
        )
        val results = (1..8).map { async { loader.load(row(1)) } }.awaitAll()
        assertEquals(1, files)
        assertEquals(1, results.distinct().size)
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
