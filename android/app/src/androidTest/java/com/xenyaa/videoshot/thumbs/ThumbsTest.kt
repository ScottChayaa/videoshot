package com.xenyaa.videoshot.thumbs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThumbsTest {

    private lateinit var root: File
    private lateinit var thumbs: Thumbs
    private val images = mutableMapOf<Long, ByteArray>()

    @Before fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "thumbs-test-${System.nanoTime()}",
        )
        thumbs = FileThumbs(root, Dispatchers.IO) { id -> images[id] }
    }

    private fun shot(
        id: Long = 1,
        source: String = "storyboard",
        frameIndex: Int? = 7,
        sbLevel: Int? = 3,
        videoId: String = "v1",
    ) = ShotRow(id, videoId, 0.0, source, frameIndex, sbLevel, "2026-01-01", null, null)

    @Test
    fun 識別碼推導出的相對路徑() {
        assertEquals("v1/L3/7.webp", ThumbKey("v1", 3, 7).relativePath())
        assertEquals("abc-DEF_12/L2/0.webp", ThumbKey("abc-DEF_12", 2, 0).relativePath())
    }

    @Test
    fun 檔案在的時候回傳檔案() = runTest {
        val key = ThumbKey("v1", 3, 7)
        thumbs.fileOf(key).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val src = thumbs.thumbFor(shot())
        assertTrue("實際是 $src", src is ThumbSource.LocalFile)
        assertEquals(thumbs.fileOf(key), (src as ThumbSource.LocalFile).file)
    }

    @Test
    fun 檔案不在的時候退回封面圖() = runTest {
        val src = thumbs.thumbFor(shot())
        assertTrue("實際是 $src", src is ThumbSource.Cover)
        assertEquals("v1", (src as ThumbSource.Cover).videoId)
    }

    @Test
    fun 手動補圖讀_shot_image_的_BLOB() = runTest {
        images[42L] = byteArrayOf(9, 9, 9)
        val src = thumbs.thumbFor(shot(id = 42, source = "manual", frameIndex = null, sbLevel = null))
        assertTrue("實際是 $src", src is ThumbSource.Bytes)
        assertTrue(byteArrayOf(9, 9, 9).contentEquals((src as ThumbSource.Bytes).webp))
    }

    @Test
    fun 手動補圖的_BLOB_不見了就給預留圖() = runTest {
        val src = thumbs.thumbFor(shot(id = 99, source = "manual", frameIndex = null, sbLevel = null))
        assertEquals(ThumbSource.Placeholder, src)
    }

    @Test
    fun 欄位不全的_storyboard_shot_給預留圖而不是當機() = runTest {
        assertEquals(ThumbSource.Placeholder, thumbs.thumbFor(shot(frameIndex = null)))
        assertEquals(ThumbSource.Placeholder, thumbs.thumbFor(shot(sbLevel = null)))
    }

    @Test
    fun exists_反映檔案在不在() {
        val key = ThumbKey("v1", 3, 1)
        assertEquals(false, thumbs.exists(key))
        thumbs.fileOf(key).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        assertEquals(true, thumbs.exists(key))
    }

    @Test
    fun 不同層級各自分開放() {
        assertTrue(thumbs.fileOf(ThumbKey("v1", 3, 0)) != thumbs.fileOf(ThumbKey("v1", 2, 0)))
    }
}
