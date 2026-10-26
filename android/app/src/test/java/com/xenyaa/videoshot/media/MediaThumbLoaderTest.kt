package com.xenyaa.videoshot.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class MediaThumbLoaderTest {

    private fun item(id: Long) = MediaItem(
        id = id, uri = "content://media/external/images/media/$id", kind = MediaKind.PHOTO,
        takenAtMs = 1_700_000_000_000L, durationMs = null, width = 100, height = 100, rotation = 0,
    )

    @Test
    fun 同一格同時要兩次只解碼一次() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val loader = MediaThumbLoader(decode = { calls++; gate.await(); ImageBitmap(1, 1) })
        val a = async { loader.load(item(5)) }
        val b = async { loader.load(item(5)) }
        runCurrent()
        gate.complete(Unit)
        assertNotNull(a.await())
        assertNotNull(b.await())
        assertEquals(1, calls)
    }

    @Test
    fun 超過容量淘汰最久沒用的() = runTest {
        val loader = MediaThumbLoader(decode = { ImageBitmap(1, 1) }, maxEntries = 2)
        loader.load(item(1))
        loader.load(item(2))
        loader.load(item(3))
        assertNull(loader.peek(1))
        assertNotNull(loader.peek(3))
    }

    @Test
    fun 解碼失敗回null而且下次會重試() = runTest {
        var fail = true
        val loader = MediaThumbLoader(decode = { if (fail) null else ImageBitmap(1, 1) })
        assertNull(loader.load(item(9)))
        fail = false
        assertNotNull(loader.load(item(9)))
    }
}
