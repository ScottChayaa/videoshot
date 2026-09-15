package com.xenyaa.videoshot.thumbs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.core.storyboard.StoryboardLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class LocalSheetCropperTest {

    private lateinit var root: File
    private lateinit var sheetsDir: File
    private lateinit var thumbs: Thumbs
    private lateinit var cropper: LocalSheetCropper

    /** 2×2 每格 10×10、共 4 格的最小層級 —— 數學算得出來，不必動到真的 storyboard。 */
    private val level = StoryboardLevel(
        level = 3, width = 10, height = 10, frameCount = 4,
        cols = 2, rows = 2, intervalMs = 1000, sigh = "x",
    )

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(ctx.cacheDir, "thumbs-test-${System.nanoTime()}")
        sheetsDir = File(ctx.cacheDir, "sheets-test-${System.nanoTime()}")
        sheetsDir.mkdirs()
        thumbs = FileThumbs(root, Dispatchers.IO) { null }
        cropper = LocalSheetCropper(thumbs, Dispatchers.IO, Dispatchers.Default)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        sheetsDir.deleteRecursively()
    }

    private fun writeSheet(name: String) {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.BLUE)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        File(sheetsDir, name).writeBytes(out.toByteArray())
    }

    @Test
    fun 從本機sheet裁出每一格並寫進thumbs() = runTest {
        writeSheet("M0.jpg")
        val result = cropper.crop("v1", level, sheetsDir, listOf(0, 1, 2, 3))
        assertEquals(4, result.written.size)
        assertEquals(emptyList<Int>(), result.missing)
        assertTrue(thumbs.exists(ThumbKey("v1", 3, 0)))
        assertTrue(thumbs.exists(ThumbKey("v1", 3, 3)))
    }

    @Test
    fun 已經存在的格子跳過不重寫() = runTest {
        writeSheet("M0.jpg")
        cropper.crop("v1", level, sheetsDir, listOf(0))
        val stamp = thumbs.fileOf(ThumbKey("v1", 3, 0)).lastModified()
        val again = cropper.crop("v1", level, sheetsDir, listOf(0))
        assertEquals(emptyList<ThumbKey>(), again.written)
        assertEquals(stamp, thumbs.fileOf(ThumbKey("v1", 3, 0)).lastModified())
    }

    @Test
    fun sheet不見了就記成missing不當機() = runTest {
        val result = cropper.crop("v1", level, sheetsDir, listOf(0, 1))
        assertEquals(emptyList<ThumbKey>(), result.written)
        assertEquals(listOf(0, 1), result.missing)
    }

    @Test
    fun sheet內容壞掉也只是missing() = runTest {
        File(sheetsDir, "M0.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val result = cropper.crop("v1", level, sheetsDir, listOf(0))
        assertEquals(listOf(0), result.missing)
    }

    @Test
    fun 進度回報數得出做到第幾張() = runTest {
        writeSheet("M0.jpg")
        val seen = mutableListOf<Pair<Int, Int>>()
        cropper.crop("v1", level, sheetsDir, listOf(0, 1, 2)) { done, total -> seen += done to total }
        assertEquals(3 to 3, seen.last())
        assertTrue(seen.all { it.second == 3 })
    }

    @Test
    fun 手動圖的格號不在storyboard範圍內會被忽略() = runTest {
        writeSheet("M0.jpg")
        // 4 是手動格（frameCount = 4，格號從 0 起算）—— 它的圖在 shot_image，不走 thumbs/
        val result = cropper.crop("v1", level, sheetsDir, listOf(0, 4))
        assertEquals(1, result.written.size)
        assertEquals(emptyList<Int>(), result.missing)
    }

    @Test
    fun sheet不見了時已經裁好的格子不算missing() = runTest {
        writeSheet("M0.jpg")
        cropper.crop("v1", level, sheetsDir, listOf(0))
        // sheet 被清掉（草稿過期、使用者清資料），但第 0 格的檔案還在
        File(sheetsDir, "M0.jpg").delete()
        val again = cropper.crop("v1", level, sheetsDir, listOf(0, 1))
        assertEquals(emptyList<ThumbKey>(), again.written)
        // 第 0 格已經有縮圖了，不該排進回填；只有第 1 格真的缺
        assertEquals(listOf(1), again.missing)
    }
}
