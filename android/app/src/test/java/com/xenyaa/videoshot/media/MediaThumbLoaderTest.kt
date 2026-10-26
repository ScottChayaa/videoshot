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

    @Test
    fun 第一個呼叫者在解碼中被取消後等待者不會卡住而且之後能重新解碼() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val loader = MediaThumbLoader(decode = {
            calls++
            if (calls == 1) gate.await()
            ImageBitmap(1, 1)
        })
        val first = async { loader.load(item(5)) }
        val waiter = async { loader.load(item(5)) }
        runCurrent()
        first.cancel()
        runCurrent()
        // 等待者拿到 null（預留色塊）就返回，不是永遠 await
        assertNull(waiter.await())
        assertNull(loader.peek(5))
        // 同一個 id 再要一次會重新解碼成功，不會拿到孤兒的 deferred
        assertNotNull(loader.load(item(5)))
        assertEquals(2, calls)
        assertNotNull(loader.peek(5))
    }

    @Test
    fun decode丟例外時不寫進快取而且下次會重試() = runTest {
        var calls = 0
        val loader = MediaThumbLoader(decode = {
            calls++
            if (calls == 1) throw IllegalStateException("壞圖")
            ImageBitmap(1, 1)
        })
        assertNull(loader.load(item(7)))
        assertNull(loader.peek(7))
        assertNotNull(loader.load(item(7)))
        assertEquals(2, calls)
    }
}
