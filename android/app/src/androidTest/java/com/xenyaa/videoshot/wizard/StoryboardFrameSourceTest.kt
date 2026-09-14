package com.xenyaa.videoshot.wizard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import com.xenyaa.videoshot.core.storyboard.StoryboardSpec
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.wizard.frames.StoryboardFrameSource
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 真 bitmap 解碼與裁切跑在實機 —— Robolectric 的假 sheet 位元組解不出真的像素，
 * 而這一組測試要驗的正是「裁到的是不是該格」。
 */
@RunWith(AndroidJUnit4::class)
class StoryboardFrameSourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sheetsDir: File

    /** 3×3 一張、每格 320×180；每一格塗不同顏色，才驗得出裁到的是哪一格。 */
    private val level = StoryboardLevel(
        level = 3, width = 320, height = 180, frameCount = 12,
        cols = 3, rows = 3, intervalMs = 10_000, sigh = "sig",
    )
    private val spec = StoryboardSpec(baseUrl = "https://example.invalid/sb", sqp = "sqp", levels = listOf(level))

    private fun fakeSheet(sheetIndex: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                paint.color = Color.rgb((sheetIndex * 60 + row * 40) % 256, col * 80 % 256, 90)
                canvas.drawRect(
                    (col * 320).toFloat(), (row * 180).toFloat(),
                    ((col + 1) * 320).toFloat(), ((row + 1) * 180).toFloat(), paint,
                )
            }
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** 真實作同時抓 4 張，所以這個假的會被並行呼叫 —— 狀態要鎖起來，否則測試會偶發性紅。 */
    private class FakeYoutube(
        val onSheet: (String) -> ByteArray,
    ) : Youtube {
        private val lock = Mutex()
        val requested = mutableListOf<String>()
        override suspend fun watchPage(videoId: String): WatchPage = error("這組測試不抓 watch page")
        override suspend fun sheet(url: String): ByteArray = lock.withLock {
            requested += url
            onSheet(url)
        }
    }

    private fun source(
        youtube: Youtube,
        refreshSpec: suspend () -> StoryboardSpec? = { null },
    ) = StoryboardFrameSource(
        videoId = "vid",
        spec = spec,
        level = level,
        sheetsDir = sheetsDir,
        youtube = youtube,
        refreshSpec = refreshSpec,
        io = Dispatchers.IO,
        compute = Dispatchers.Default,
    )

    @Before
    fun setUp() {
        sheetsDir = File(context.filesDir, "drafts/vid/sheets")
        sheetsDir.deleteRecursively()
    }

    @Test
    fun plan_的格數與秒數來自level() {
        val s = source(FakeYoutube { fakeSheet(0) })
        assertEquals(12, s.plan.frameCount)
        assertEquals(0.0, s.plan.atSec[0], 0.001)
        assertEquals(10.0, s.plan.atSec[1], 0.001)
        assertEquals(110.0, s.plan.atSec[11], 0.001)
        assertEquals(false, s.plan.lowQuality)
    }

    @Test
    fun load_一張sheet發一批而且格號連續() = runBlocking {
        val s = source(FakeYoutube { fakeSheet(0) })
        val batches = s.load().toList()

        assertEquals("12 格 / 每張 9 格 = 2 張", 2, batches.size)
        assertEquals((0..8).toList(), batches[0].frameIndexes)
        assertEquals((9..11).toList(), batches[1].frameIndexes)
    }

    @Test
    fun load_每一格都帶指紋而且不同顏色的格子指紋不同() = runBlocking {
        val s = source(FakeYoutube { fakeSheet(0) })
        val first = s.load().toList().first()

        assertEquals(9, first.fingerprints.size)
        assertEquals((0..8).toList(), first.fingerprints.map { it.frameIndex })
        assertTrue("一張 sheet 上九個顏色不該全算出同一個指紋",
            first.fingerprints.map { it.hash }.distinct().size > 1)
    }

    @Test
    fun sheet_會寫進drafts目錄() = runBlocking {
        val s = source(FakeYoutube { fakeSheet(0) })
        s.load().toList()
        assertTrue(File(sheetsDir, "M0.jpg").exists())
        assertTrue(File(sheetsDir, "M1.jpg").exists())
    }

    @Test
    fun bitmapOf_從磁碟上的sheet裁得出該格() = runBlocking {
        val s = source(FakeYoutube { fakeSheet(0) })
        s.load().toList()

        val bitmap = s.bitmapOf(4)
        assertNotNull(bitmap)
        assertEquals(320, bitmap!!.width)
        assertEquals(180, bitmap.height)
    }

    @Test
    fun bitmapOf_超出範圍回null() = runBlocking {
        val s = source(FakeYoutube { fakeSheet(0) })
        s.load().toList()
        assertEquals(null, s.bitmapOf(99))
    }

    @Test
    fun 第一次403會重抓spec再試一次() = runBlocking {
        var refreshed = 0
        var firstCall = true
        val youtube = FakeYoutube { url ->
            if (firstCall) { firstCall = false; throw SheetForbidden(url) }
            fakeSheet(0)
        }
        val s = source(youtube, refreshSpec = { refreshed++; spec })
        val batches = s.load().toList()

        assertEquals("重抓過一次", 1, refreshed)
        assertEquals("重抓後拿得到圖，不算降級", false, batches.first().degradedToCover)
    }

    @Test
    fun 重抓之後仍然403就退回封面圖() = runBlocking {
        val youtube = FakeYoutube { url ->
            if (url.contains("hqdefault")) fakeSheet(0) else throw SheetForbidden(url)
        }
        val s = source(youtube, refreshSpec = { spec })
        val batches = s.load().toList()

        assertTrue("每一批都要標記得出來", batches.all { it.degradedToCover })
        assertTrue("封面圖是無簽章的 hqdefault", youtube.requested.any { it.contains("hqdefault") })
        assertNotNull("降級後每一格都拿得到封面圖", s.bitmapOf(0))
    }

    @Test
    fun close之後drafts裡的sheet還在_草稿要留著() = runBlocking {
        val s = source(FakeYoutube { fakeSheet(0) })
        s.load().toList()
        s.close()
        assertTrue("sheet 綁在草稿上，由精靈完成或捨棄時才刪（規格第四節）",
            File(sheetsDir, "M0.jpg").exists())
    }
}
