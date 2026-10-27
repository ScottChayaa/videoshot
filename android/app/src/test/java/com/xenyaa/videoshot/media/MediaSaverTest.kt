package com.xenyaa.videoshot.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** 相簿檔名規則與權限，以及 [MediaStoreSaver] 的寫入流程（MediaStore 用假的內容提供者代替）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaSaverTest {

    @Test fun 斜線換成連字號() =
        assertEquals("videoshot 2026-10-20～10-27.mp4", galleryFileName("videoshot 2026/10/20～10/27"))

    @Test fun 其他不合法字元也換掉() =
        assertEquals("a-b-c-d-e-f-g-h-i.mp4", galleryFileName("a\\b:c*d?e\"f<g>h|i"))

    @Test fun 沒有不合法字元時只加副檔名() =
        assertEquals("我的影片.mp4", galleryFileName("我的影片"))

    @Test fun Android9以下要寫入權限() {
        val expected = listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE")
        assertEquals(expected, mediaPermissionsFor(28))
        assertEquals(expected, mediaPermissionsFor(26))
    }

    @Test fun Android10起不要寫入權限() {
        for (sdk in listOf(29, 33, 34)) {
            assertFalse("android.permission.WRITE_EXTERNAL_STORAGE" in mediaPermissionsFor(sdk))
        }
    }

    @Test fun 多要寫入權限不影響存取判定() {
        // 只授權讀取就算完整存取；寫入權限不參與判定
        assertEquals(MediaAccess.FULL, mediaAccessOf(28, setOf("android.permission.READ_EXTERNAL_STORAGE")))
    }

    /** 記下每次呼叫。內容怎麼寫由各測試用 `registerOutputStream` 指定（Robolectric 不經過提供者開檔）。 */
    private class FakeVideoProvider(
        private val failInsert: Boolean = false,
        private val returnNullInsert: Boolean = false,
    ) : ContentProvider() {
        val inserted = mutableListOf<ContentValues>()
        val updated = mutableListOf<ContentValues>()
        val deleted = mutableListOf<Uri>()
        val rowUri: Uri = Uri.parse("content://media/external/video/media/42")

        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (failInsert) throw IllegalStateException("提供者壞了")
            if (returnNullInsert) return null
            inserted += ContentValues(values)
            return rowUri
        }
        override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int {
            deleted += uri
            return 1
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int {
            updated += ContentValues(values)
            return 1
        }
    }

    private fun tempFile(content: ByteArray? = null): File =
        File.createTempFile("saver", ".mp4").apply {
            deleteOnExit()
            if (content != null) writeBytes(content)
        }

    private fun saver(provider: FakeVideoProvider, sdk: Int = 35, stream: OutputStream? = ByteArrayOutputStream()): MediaStoreSaver {
        ShadowContentResolver.registerProviderInternal("media", provider)
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        if (stream != null) shadowOf(resolver).registerOutputStream(provider.rowUri, stream)
        return MediaStoreSaver(resolver, sdk, Dispatchers.Unconfined)
    }

    @Test fun 寫入成功回網址() = runBlocking {
        val source = tempFile(ByteArray(1000) { it.toByte() })
        val target = ByteArrayOutputStream()
        val provider = FakeVideoProvider()

        val result = saver(provider, stream = target).saveVideo(source, "videoshot 2026/10/20～10/27")

        assertEquals(provider.rowUri.toString(), result)
        assertTrue(source.readBytes().contentEquals(target.toByteArray()))
        val row = provider.inserted.single()
        assertEquals("videoshot 2026-10-20～10-27.mp4", row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("video/mp4", row.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals("Movies/videoshot", row.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(1, row.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertEquals(0, provider.updated.single().getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertTrue(provider.deleted.isEmpty())
    }

    @Test fun 提供者丟例外時回null() = runBlocking {
        val provider = FakeVideoProvider(failInsert = true)
        assertNull(saver(provider).saveVideo(tempFile(ByteArray(10)), "標題"))
    }

    @Test fun 提供者回不出網址時回null() = runBlocking {
        val provider = FakeVideoProvider(returnNullInsert = true)
        assertNull(saver(provider).saveVideo(tempFile(ByteArray(10)), "標題"))
    }

    @Test fun 寫入失敗時刪掉插入的那列() = runBlocking {
        val provider = FakeVideoProvider()
        val broken = object : OutputStream() {
            override fun write(b: Int) = throw IOException("磁碟滿了")
        }
        assertNull(saver(provider, stream = broken).saveVideo(tempFile(ByteArray(10)), "標題"))
        assertEquals(listOf(provider.rowUri), provider.deleted)
        assertTrue(provider.updated.isEmpty())
    }

    @Test fun 來源檔不存在時回null並刪掉插入的那列() = runBlocking {
        val provider = FakeVideoProvider()
        assertNull(saver(provider).saveVideo(File("/nonexistent/out.mp4"), "標題"))
        assertEquals(listOf(provider.rowUri), provider.deleted)
    }

    @Test
    fun Android9以下複製到影片資料夾並用路徑插入() = runBlocking {
        val source = tempFile(ByteArray(500) { 7 })
        val provider = FakeVideoProvider()

        val result = saver(provider, sdk = 28, stream = null).saveVideo(source, "a/b")

        assertEquals(provider.rowUri.toString(), result)
        val row = provider.inserted.single()
        val path = row.getAsString(MediaStore.MediaColumns.DATA)
        assertNotNull(path)
        assertTrue(path, path.endsWith("/videoshot/a-b.mp4"))
        val written = File(path)
        assertTrue(written.readBytes().contentEquals(source.readBytes()))
        assertNull(row.get(MediaStore.MediaColumns.RELATIVE_PATH))
        written.delete()
        Unit
    }
}
