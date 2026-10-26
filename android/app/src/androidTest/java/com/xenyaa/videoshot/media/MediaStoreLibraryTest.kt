package com.xenyaa.videoshot.media

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在實機上只讀 MediaStore，不新增、修改或刪除任何檔案，也不替手機授權。
 * 需要先在手機上讓 videoshot 完整存取相片和影片，沒有的話整組略過。
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreLibraryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireFullAccess() {
        val sdk = Build.VERSION.SDK_INT
        val granted = mediaPermissionsFor(sdk)
            .filter { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            .toSet()
        assumeTrue(
            "需要先在手機上允許 videoshot 存取相片和影片",
            mediaAccessOf(sdk, granted) == MediaAccess.FULL,
        )
    }

    @Test
    fun 全部等於照片加影片而且新的在前() = runBlocking {
        val lib = MediaStoreLibrary(context.contentResolver, Dispatchers.IO)
        val all = lib.list(MediaFilter.ALL)
        val photos = lib.list(MediaFilter.PHOTO)
        val videos = lib.list(MediaFilter.VIDEO)
        assertEquals(photos.size + videos.size, all.size)
        assertTrue(all.zipWithNext().all { (a, b) -> a.takenAtMs >= b.takenAtMs })
        assertTrue(videos.all { it.kind == MediaKind.VIDEO && (it.durationMs ?: -1) >= 0 })
        assertTrue(photos.all { it.kind == MediaKind.PHOTO && it.durationMs == null })
    }

    @Test
    fun 沒有任何拍攝時間是1970() = runBlocking {
        val all = MediaStoreLibrary(context.contentResolver, Dispatchers.IO).list(MediaFilter.ALL)
        // 2000-01-01；退回修改時間後不會更早
        val tooOld = all.filter { it.takenAtMs < 946_684_800_000L }
        assertTrue("太早的素材：" + tooOld.joinToString { "id=${it.id} takenAtMs=${it.takenAtMs}" }, tooOld.isEmpty())
    }
}
