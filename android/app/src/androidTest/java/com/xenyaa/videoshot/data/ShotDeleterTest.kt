package com.xenyaa.videoshot.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.ShotFolderEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.thumbs.FileThumbs
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShotDeleterTest {

    private lateinit var libraryDb: LibraryDatabase
    private lateinit var cacheDb: CacheDatabase
    private lateinit var thumbsRoot: File
    private lateinit var deleter: ShotDeleter
    private lateinit var library: RoomLibraryRepo
    private lateinit var cache: RoomCacheRepo

    private val video = VideoEntity("v1", "片名", "頻道", "2026-02-02T10:00:00Z", 600, "public", null, 1L)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        libraryDb = inMemoryLibraryDb()
        cacheDb = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).build()
        thumbsRoot = File(context.cacheDir, "thumbs-test-${System.nanoTime()}")
        library = RoomLibraryRepo(libraryDb, Dispatchers.IO)
        cache = RoomCacheRepo(cacheDb, Dispatchers.IO)
        val thumbs = FileThumbs(thumbsRoot, Dispatchers.IO) { library.shotImage(it) }
        deleter = ShotDeleter(library, thumbs, cache, Dispatchers.IO)
    }

    @After fun tearDown() {
        libraryDb.close()
        cacheDb.close()
        thumbsRoot.deleteRecursively()
    }

    private fun pick(frameIndex: Int) = NewShot(
        atSec = frameIndex.toDouble(), source = "storyboard", frameIndex = frameIndex, sbLevel = 3,
        eventDate = "2026-02-02", place = null, description = null, webp = null,
        tagNames = listOf("露營"),
    )

    private fun writeThumb(frameIndex: Int): File =
        File(thumbsRoot, ThumbKey("v1", 3, frameIndex).let { "${it.videoId}/L${it.level}/${it.frameIndex}.webp" })
            .also { it.parentFile?.mkdirs(); it.writeBytes(byteArrayOf(1, 2, 3)) }

    /** 案例 23：刪除 shot → 它在所有資料夾中的關聯一併消失。 */
    @Test
    fun 刪一張會一起清掉標籤與資料夾關聯() = runTest {
        val ids = library.commitPicks(video, listOf(pick(0), pick(1)))
        val folder = library.createFolder(null, "露營")
        libraryDb.folderDao().link(ShotFolderEntity(ids[0], folder, 1L))

        deleter.delete(ids[0])

        assertNull(library.shotById(ids[0]))
        assertEquals(0, libraryDb.tagDao().linkCountOfShot(ids[0]))
        assertEquals(0, libraryDb.folderDao().linkCountOfShot(ids[0]))
        // 另一張不受影響
        assertEquals(1, libraryDb.tagDao().linkCountOfShot(ids[1]))
    }

    @Test
    fun 手動圖的_webp_一起刪() = runTest {
        val manual = NewShot(12.5, "manual", null, null, "2026-02-02", null, null, byteArrayOf(9))
        val ids = library.commitPicks(video, listOf(manual))
        deleter.delete(ids.single())
        assertNull(libraryDb.shotDao().imageOf(ids.single()))
    }

    @Test
    fun 縮圖檔一起刪() = runTest {
        val ids = library.commitPicks(video, listOf(pick(0), pick(1)))
        val kept = writeThumb(1)
        val gone = writeThumb(0)
        deleter.delete(ids[0])
        assertFalse("刪掉那一張的縮圖檔要消失", gone.exists())
        assertTrue("別張的縮圖檔不能被波及", kept.exists())
    }

    /** 不清 thumb_state 的話，回填作業會把剛刪掉的那一格又抓回來。 */
    @Test
    fun thumb_state_一起清掉() = runTest {
        val ids = library.commitPicks(video, listOf(pick(0), pick(1)))
        cache.putThumbStates(
            listOf(
                ThumbStateEntity("v1", 3, 0, "missing", 0, 0, null),
                ThumbStateEntity("v1", 3, 1, "ok", 0, 0, null),
            )
        )
        deleter.delete(ids[0])
        assertNull(cache.thumbState("v1", 3, 0))
        assertEquals("ok", cache.thumbState("v1", 3, 1)?.state)
    }

    /** 規格第六節：最後一張被刪掉時，video 列跟著走。 */
    @Test
    fun 最後一張刪掉時影片列與整個縮圖目錄一起消失() = runTest {
        val ids = library.commitPicks(video, listOf(pick(0)))
        writeThumb(0)
        cache.putThumbStates(listOf(ThumbStateEntity("v1", 3, 0, "ok", 0, 0, null)))

        deleter.delete(ids.single())

        assertNull(libraryDb.videoDao().byId("v1"))
        assertFalse(File(thumbsRoot, "v1").exists())
        assertEquals(0, cacheDb.thumbStateDao().countOfVideo("v1"))
    }

    @Test
    fun 刪不存在的_id_不會爆() = runTest {
        deleter.delete(9999L)
    }

    /**
     * 見階段 7 全盤覆查第 2 點：KDoc 說「檔案或 cache 清失敗只是留下垃圾，圖庫仍然一致」，
     * 但改之前這段沒接例外，清檔失敗會直接把整個 delete() 炸掉，DB 那半邊看起來也像失敗了。
     * 這裡故意讓縮圖檔清除失敗，驗證 DB 的刪除照樣算數、delete() 本身不會往外丟例外。
     */
    @Test
    fun 縮圖清除失敗不影響_DB_已經刪除的部分() = runTest {
        val ids = library.commitPicks(video, listOf(pick(0)))
        val failingThumbs = object : Thumbs {
            override suspend fun thumbFor(shot: com.xenyaa.videoshot.data.repo.model.ShotRow) =
                throw UnsupportedOperationException("測試不用到")
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
            override suspend fun delete(key: ThumbKey): Unit = throw java.io.IOException("模擬刪檔失敗")
            override suspend fun deleteVideo(videoId: String): Unit = throw java.io.IOException("模擬刪檔失敗")
        }
        val failingDeleter = ShotDeleter(library, failingThumbs, cache, Dispatchers.IO)

        failingDeleter.delete(ids.single())

        assertNull(library.shotById(ids.single()))
    }
}
