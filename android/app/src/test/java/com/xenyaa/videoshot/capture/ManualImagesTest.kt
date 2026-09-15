package com.xenyaa.videoshot.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManualImagesTest {

    private val dir: File
        get() = File(
            ApplicationProvider.getApplicationContext<Context>().filesDir,
            "drafts/v1/manual",
        )

    private fun bitmap(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    @Test
    fun 編碼後一定是320x180() {
        val webp = encodeManualWebp(bitmap(1920, 1080))
        assertNotNull(webp)
        val decoded = BitmapFactory.decodeByteArray(webp, 0, webp!!.size)
        assertEquals(320, decoded.width)
        assertEquals(180, decoded.height)
    }

    @Test
    fun 直式的圖也縮成320x180() {
        // 直式影片同樣要能存 —— 這裡只保證尺寸固定，不做 letterbox
        val webp = encodeManualWebp(bitmap(1080, 1920))
        val decoded = BitmapFactory.decodeByteArray(webp, 0, webp!!.size)
        assertEquals(320, decoded.width)
        assertEquals(180, decoded.height)
    }

    @Test
    fun 存檔會建目錄而且副檔名是webp() {
        val store = ManualImageStore(dir)
        val file = store.save(byteArrayOf(1, 2, 3))
        assertTrue(file.path, file.exists())
        assertTrue(file.name, file.name.endsWith(".webp"))
        assertEquals(dir.canonicalPath, file.parentFile!!.canonicalPath)
    }

    @Test
    fun 每次存檔都是新檔名() {
        val store = ManualImageStore(dir)
        val a = store.save(byteArrayOf(1))
        val b = store.save(byteArrayOf(2))
        assertTrue("$a 與 $b 撞名了", a.name != b.name)
    }

    @Test
    fun clear會把整個目錄刪掉() {
        val store = ManualImageStore(dir)
        store.save(byteArrayOf(1))
        store.clear()
        assertFalse(dir.exists())
    }
}
