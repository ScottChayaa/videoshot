package com.xenyaa.videoshot.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** [MediaStoreDeleter]：哪些素材還在、什麼版本能刪、媒體管理權限的設定頁。MediaStore 用假的內容提供者代替。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStoreDeleterTest {

    /** 只有編號在 [ids] 裡的查得到；[fail] 時每次查詢都丟例外。 */
    private class FakeMediaProvider(private val ids: Set<Long>, private val fail: Boolean = false) : ContentProvider() {
        var queries = 0
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
            queries++
            if (fail) throw IllegalStateException("cursor window 暫時出錯")
            val cursor = MatrixCursor(arrayOf("_id"))
            val id = uri.lastPathSegment?.toLongOrNull()
            if (id != null && id in ids) cursor.addRow(arrayOf(id))
            return cursor
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun deleter(provider: ContentProvider? = null, sdkInt: Int = 35): MediaStoreDeleter {
        if (provider != null) ShadowContentResolver.registerProviderInternal("media", provider)
        return MediaStoreDeleter(context, sdkInt, Dispatchers.Unconfined)
    }

    private fun uri(id: Int) = "content://media/external/images/media/$id"

    @Test
    fun existing_只回查得到的且照原順序() = runBlocking {
        val d = deleter(FakeMediaProvider(setOf(1L, 3L)))
        assertEquals(listOf(uri(3), uri(1)), d.existing(listOf(uri(3), uri(2), uri(1))))
    }

    @Test
    fun existing_提供者出錯的當作不在且不外洩例外() = runBlocking {
        val d = deleter(FakeMediaProvider(setOf(1L), fail = true))
        assertEquals(emptyList<String>(), d.existing(listOf(uri(1))))
    }

    @Test
    fun existing_空清單不查() = runBlocking {
        val provider = FakeMediaProvider(setOf(1L))
        assertEquals(emptyList<String>(), deleter(provider).existing(emptyList()))
        assertEquals(0, provider.queries)
    }

    @Test
    fun canDelete_29是false_30是true() {
        assertFalse(deleter(sdkInt = 29).canDelete)
        assertTrue(deleter(sdkInt = 30).canDelete)
    }

    @Test
    fun deleteRequest_不能刪或空清單回null() {
        assertNull(deleter(sdkInt = 29).deleteRequest(listOf(uri(1))))
        assertNull(deleter(sdkInt = 35).deleteRequest(emptyList()))
    }

    @Test
    fun manageMediaSettingsIntent_30回null_31帶package網址() {
        assertNull(deleter(sdkInt = 30).manageMediaSettingsIntent())
        val intent = deleter(sdkInt = 31).manageMediaSettingsIntent()!!
        assertEquals(Settings.ACTION_REQUEST_MANAGE_MEDIA, intent.action)
        assertEquals(Uri.parse("package:${context.packageName}"), intent.data)
    }

    @Test
    fun suggestManageMedia_30是false() {
        assertFalse(deleter(sdkInt = 30).suggestManageMedia())
    }
}
