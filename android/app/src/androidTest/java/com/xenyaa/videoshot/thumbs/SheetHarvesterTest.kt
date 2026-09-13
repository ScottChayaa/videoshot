package com.xenyaa.videoshot.thumbs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.storyboard.Storyboard
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.youtube.SheetForbidden
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class SheetHarvesterTest {

    /** 3×3 格、每格 320×180 的假 sheet；每一格填不同顏色，才驗得出裁對了沒有。 */
    private fun fakeSheet(): ByteArray {
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        val colors = listOf(
            Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA,
            Color.WHITE, Color.GRAY, Color.BLACK,
        )
        for (i in 0 until 9) {
            paint.color = colors[i]
            val x = (i % 3) * 320f
            val y = (i / 3) * 180f
            canvas.drawRect(x, y, x + 320f, y + 180f, paint)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private class FakeYoutube(private val sheets: MutableList<Result<ByteArray>>) : Youtube {
        val requestedUrls = mutableListOf<String>()
        override suspend fun watchPage(videoId: String): WatchPage = error("這個測試不會用到")
        override suspend fun sheet(url: String): ByteArray {
            requestedUrls += url
            return sheets.removeAt(0).getOrThrow()
        }
    }

    private val spec = Storyboard.parse(
        "https://i.ytimg.com/sb/v1/storyboard3_L\$L/\$N.jpg?sqp=x" +
            "|48#27#100#10#10#0#default#rs\$A" +
            "|320#180#9#3#3#1000#M\$M#rs\$B"
    )!!
    private val level = Storyboard.pickLevel(spec, preferred = 1)!!

    private lateinit var root: File
    private lateinit var thumbs: Thumbs

    @Before fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "harvest-test-${System.nanoTime()}",
        )
        thumbs = FileThumbs(root, Dispatchers.IO) { null }
    }

    @Test
    fun 裁出指定的格子並存成_320x180_的_WebP() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(fakeSheet())))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0, 4, 8)) { null }

        assertEquals(3, r.written.size)
        assertTrue(r.failed.isEmpty())
        for (key in r.written) {
            val f = thumbs.fileOf(key)
            assertTrue("${key.relativePath()} 不存在", f.exists())
            val decoded = BitmapFactory.decodeFile(f.path)
            assertEquals(320, decoded.width)
            assertEquals(180, decoded.height)
        }
    }

    @Test
    fun 裁到的是正確的那一格() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(fakeSheet())))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)
        harvester.harvest("v1", spec, level, listOf(4)) { null }

        val decoded = BitmapFactory.decodeFile(thumbs.fileOf(ThumbKey("v1", 1, 4)).path)
        val px = decoded.getPixel(160, 90)
        // WebP q75 是失真壓縮，不能比位元相等 —— 驗的是「裁到的是第 4 格（青色）」而不是編碼器的精度
        assertChannelsClose("第 4 格應該是青色，實際 ${Integer.toHexString(px)}", Color.CYAN, px)
    }

    /** 每個色彩通道相差不超過 tolerance 就算同一個顏色。 */
    private fun assertChannelsClose(message: String, expected: Int, actual: Int, tolerance: Int = 8) {
        for (shift in listOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue("$message（通道位移 $shift：期望 $e 實際 $a）", kotlin.math.abs(e - a) <= tolerance)
        }
    }

    @Test
    fun 同一張_sheet_只下載一次() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(fakeSheet())))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)
        harvester.harvest("v1", spec, level, listOf(0, 1, 2, 3)) { null }
        assertEquals(1, yt.requestedUrls.size)
    }

    @Test
    fun 已經有檔案的格子直接跳過不重寫() = runTest {
        val key = ThumbKey("v1", 1, 0)
        thumbs.fileOf(key).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val yt = FakeYoutube(mutableListOf())
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { null }

        assertTrue(r.written.isEmpty())
        assertTrue(r.failed.isEmpty())
        assertEquals(0, yt.requestedUrls.size)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(thumbs.fileOf(key).readBytes()))
    }

    @Test
    fun sheet_回_403_時重抓一次_spec_再試() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.failure(SheetForbidden("x")), Result.success(fakeSheet())))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)
        var refreshed = 0

        val r = harvester.harvest("v1", spec, level, listOf(0)) { refreshed++; spec }

        assertEquals(1, refreshed)
        assertEquals(1, r.written.size)
        assertEquals(false, r.degradedToCover)
    }

    @Test
    fun 重抓之後仍然_403_就退回封面圖() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.failure(SheetForbidden("x")), Result.failure(SheetForbidden("x"))))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { spec }

        assertTrue(r.written.isEmpty())
        assertEquals(listOf(0), r.failed)
        assertEquals(true, r.degradedToCover)
    }

    @Test
    fun 重抓_spec_也拿不到時不會無限重試() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.failure(SheetForbidden("x"))))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { null }

        assertEquals(listOf(0), r.failed)
        assertEquals(true, r.degradedToCover)
        assertEquals(1, yt.requestedUrls.size)
    }

    @Test
    fun sheet_解不出圖片時記為失敗而不是當機() = runTest {
        val yt = FakeYoutube(mutableListOf(Result.success(byteArrayOf(1, 2, 3))))
        val harvester = SheetHarvester(yt, thumbs, Dispatchers.Default)

        val r = harvester.harvest("v1", spec, level, listOf(0)) { null }

        assertEquals(listOf(0), r.failed)
    }

    @Test
    fun 灰階取樣是_9x8_共_72_個值() = runTest {
        val bytes = fakeSheet()
        val sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val pos = Storyboard.framePosition(level, 0)
        val gray = grayscale9x8(sheet, pos)
        assertEquals(72, gray.size)
        assertTrue(gray.all { it in 0..255 })
    }
}
