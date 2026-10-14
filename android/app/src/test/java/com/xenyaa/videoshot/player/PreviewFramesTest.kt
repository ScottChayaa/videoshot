package com.xenyaa.videoshot.player

import android.graphics.Bitmap
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PreviewFramesTest {

    @get:Rule val tmp = TemporaryFolder()

    // 2×2 一張 sheet、每格 80×45、間隔 10 秒、共 8 格（兩張 sheet）
    private val level = StoryboardLevel(2, 80, 45, 8, 2, 2, 10_000, "sig")
    private val spec = StoryboardSpec("https://i.ytimg.com/sb/x/storyboard3_L\$L/\$N.jpg", "q", listOf(level))

    private fun jpeg(): ByteArray {
        val bmp = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    private class FakeYoutube(val onSheet: (String) -> ByteArray) : Youtube {
        val requested = mutableListOf<String>()
        override suspend fun watchPage(videoId: String): WatchPage = error("不該呼叫")
        override suspend fun sheet(url: String): ByteArray { requested += url; return onSheet(url) }
    }

    private fun frames(yt: Youtube, refresh: suspend () -> StoryboardSpec? = { null }) =
        PreviewFrames(spec, level, tmp.root, yt, refresh, Dispatchers.Unconfined, Dispatchers.Unconfined)

    @Test
    fun 拖到哪才下載哪一張_同一張只下載一次() = runTest {
        val yt = FakeYoutube { jpeg() }
        val f = frames(yt)
        val img = f.at(12.0)                // 第 1 格，在 M0
        assertNotNull(img)
        assertEquals(80, img!!.width)
        f.at(25.0)                          // 第 2 格，還是 M0
        assertEquals(1, yt.requested.size)
        f.at(45.0)                          // 第 4 格（4.5 四捨五入），M1
        assertEquals(2, yt.requested.size)
        assertEquals(true, yt.requested[1].contains("M1"))
    }

    @Test
    fun 下載過的_sheet_存在磁碟_下次直接讀() = runTest {
        val yt1 = FakeYoutube { jpeg() }
        frames(yt1).at(0.0)
        val yt2 = FakeYoutube { error("不該再下載") }
        assertNotNull(frames(yt2).at(0.0))
    }

    @Test
    fun 回_403_重抓一次_spec_仍失敗就回_null() = runTest {
        var refreshes = 0
        val yt = FakeYoutube { throw SheetForbidden(it) }
        val f = frames(yt) { refreshes++; spec }
        assertNull(f.at(0.0))
        assertEquals(1, refreshes)
        assertNull(f.at(0.0))
        assertEquals(1, refreshes)   // 只重抓一次
    }

    @Test
    fun 網路錯誤回_null_不丟例外() = runTest {
        assertNull(frames(FakeYoutube { throw java.io.IOException("斷線") }).at(0.0))
    }
}
