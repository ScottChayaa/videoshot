package com.xenyaa.videoshot.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** [MediaStoreLibrary] 的讀取行為：提供者出錯不外洩例外、掃描途中可被取消。MediaStore 用假的內容提供者代替。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStoreLibraryTest {

    /** 兩張表都回同一份資料；[onRow] 在每次 `moveToNext` 讀到一列時呼叫（第 n 列）。 */
    private class FakeMediaProvider(
        private val rows: Int,
        private val fail: Boolean = false,
        /** 只讓網址符合的那張表出錯（例如只有影片表）。 */
        private val failOn: (Uri) -> Boolean = { false },
        private val onRow: (Int) -> Unit = {},
    ) : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
            if (fail || failOn(uri)) throw IllegalStateException("cursor window 暫時出錯")
            val cols = projection ?: emptyArray()
            val cursor = MatrixCursor(cols)
            repeat(rows) { i ->
                cursor.addRow(cols.map { col ->
                    when (col) {
                        "_id" -> (i + 1).toLong()
                        "date_modified" -> 1_700_000_000L + i
                        "width", "height" -> 100
                        else -> null
                    }
                })
            }
            var n = 0
            return object : CursorWrapper(cursor) {
                override fun moveToNext(): Boolean {
                    val has = super.moveToNext()
                    if (has) onRow(++n)
                    return has
                }
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    }

    private fun library(provider: ContentProvider): MediaStoreLibrary {
        ShadowContentResolver.registerProviderInternal("media", provider)
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        return MediaStoreLibrary(resolver, Dispatchers.Unconfined)
    }

    @Test
    fun 內容提供者丟出非權限例外時回空清單而不是丟出去() = runBlocking {
        val lib = library(FakeMediaProvider(rows = 3, fail = true))
        assertEquals(emptyList<MediaItem>(), lib.list(MediaFilter.ALL))
    }

    @Test
    fun 全部時只有影片表出錯整份回空清單而不是只剩照片() = runBlocking {
        val lib = library(FakeMediaProvider(rows = 3, failOn = { "video" in it.toString() }))
        assertEquals(emptyList<MediaItem>(), lib.list(MediaFilter.ALL))
    }

    @Test
    fun 全部時只有照片表出錯整份回空清單而不是只剩影片() = runBlocking {
        val lib = library(FakeMediaProvider(rows = 3, failOn = { "images" in it.toString() }))
        assertEquals(emptyList<MediaItem>(), lib.list(MediaFilter.ALL))
    }

    @Test
    fun 只讀影片分頁時影片表出錯也回空清單() = runBlocking {
        val lib = library(FakeMediaProvider(rows = 3, failOn = { "video" in it.toString() }))
        assertEquals(emptyList<MediaItem>(), lib.list(MediaFilter.VIDEO))
        // 照片分頁不受影片表影響
        assertEquals(3, lib.list(MediaFilter.PHOTO).size)
    }

    @Test
    fun 正常時兩張表的列都讀到() = runBlocking {
        val lib = library(FakeMediaProvider(rows = 3))
        assertEquals(6, lib.list(MediaFilter.ALL).size)
    }

    @Test
    fun 掃描途中被取消就不再往下讀() = runBlocking {
        var job: Job? = null
        var lastRow = 0
        val lib = library(FakeMediaProvider(rows = 1000) { n ->
            lastRow = n
            if (n == 250) job?.cancel()
        })
        // LAZY：先拿到 job 再開始，取消才打得到
        val deferred = async(start = CoroutineStart.LAZY) { lib.list(MediaFilter.PHOTO) }
        job = deferred
        deferred.start()
        try {
            deferred.await()
            fail("應該被取消")
        } catch (_: CancellationException) {
            // 預期
        }
        // 第 250 列取消，下一次檢查在第 400 列（每 200 列一次），不會讀到 1000 列
        assertTrue("最後讀到第 $lastRow 列", lastRow < 1000)
    }
}
