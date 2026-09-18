package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderContentTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo
    private var changes = 0

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        changes = 0
        repo = RoomLibraryRepo(db, Dispatchers.IO, onChanged = { changes++ })
    }

    @After fun tearDown() { db.close() }

    private suspend fun shot(n: Int): Long {
        db.videoDao().upsert(VideoEntity("v1", "t", "c", "2026-03-01T00:00:00Z", 600, "public", null, 1L))
        return db.shotDao().insert(
            ShotEntity(
                id = 0, videoId = "v1", atSec = n.toDouble(), source = "storyboard",
                frameIndex = n, sbLevel = 3, eventDate = "2026-03-01", place = null,
                description = null, aiTranscript = null, aiVisualDesc = null, aiRaw = null,
                createdAt = 1L,
            )
        )
    }

    @Test
    fun 加入與移出都會標記有變更() = runTest {
        val f = repo.createFolder(null, "旅行")
        val s = shot(1)
        changes = 0

        repo.addShotToFolder(s, f)
        assertEquals(setOf(f), repo.foldersOf(s))
        assertEquals(1, changes)

        repo.removeShotFromFolder(s, f)
        assertEquals(emptySet<Long>(), repo.foldersOf(s))
        assertEquals(2, changes)
    }

    /** 一張圖可以同時在多個資料夾（規格第六節：不限）。 */
    @Test
    fun 一張圖可以進多個資料夾() = runTest {
        val a = repo.createFolder(null, "旅行")
        val b = repo.createFolder(null, "貓")
        val s = shot(1)

        repo.addShotToFolder(s, a)
        repo.addShotToFolder(s, b)

        assertEquals(setOf(a, b), repo.foldersOf(s))
    }

    /** 重複加入不該爆炸，也不該把原本的加入時間洗掉。 */
    @Test
    fun 重複加入同一個資料夾不改變加入時間() = runTest {
        val f = repo.createFolder(null, "旅行")
        val s = shot(1)

        repo.addShotToFolder(s, f)
        val first = repo.folderShots(f, after = null, limit = 10).items.single().id
        repo.addShotToFolder(s, f)

        assertEquals(1, repo.folderShotCount(f))
        assertEquals(first, repo.folderShots(f, after = null, limit = 10).items.single().id)
    }

    @Test
    fun 本層的圖新加入在前() = runTest {
        val f = repo.createFolder(null, "旅行")
        val a = shot(1)
        val b = shot(2)
        val c = shot(3)
        repo.addShotToFolder(a, f)
        repo.addShotToFolder(b, f)
        repo.addShotToFolder(c, f)

        assertEquals(listOf(c, b, a), repo.folderShots(f, after = null, limit = 10).items.map { it.id })
    }

    /** 子資料夾的圖**不**出現在父資料夾頁（規格第六節：下半是本層的圖）。 */
    @Test
    fun 只列本層不含子資料夾的圖() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 宜蘭 = repo.createFolder(旅行, "宜蘭")
        val a = shot(1)
        val b = shot(2)
        repo.addShotToFolder(a, 旅行)
        repo.addShotToFolder(b, 宜蘭)

        assertEquals(listOf(a), repo.folderShots(旅行, after = null, limit = 10).items.map { it.id })
        assertEquals(1, repo.folderShotCount(旅行))
    }

    @Test
    fun keyset_分頁接得起來且不重複不漏() = runTest {
        val f = repo.createFolder(null, "旅行")
        val ids = (1..5).map { shot(it) }
        ids.forEach { repo.addShotToFolder(it, f) }

        val first = repo.folderShots(f, after = null, limit = 2)
        val second = repo.folderShots(f, after = first.next, limit = 2)
        val third = repo.folderShots(f, after = second.next, limit = 2)

        assertEquals(ids.reversed(), first.items.map { it.id } + second.items.map { it.id } + third.items.map { it.id })
        assertNull("撈不滿就是到底了", third.next)
    }

    /** 同一秒加入的多張圖：游標要靠 shot_id 分出先後，否則會卡住或跳過。 */
    @Test
    fun 同一秒加入的圖分頁不會卡住() = runTest {
        val f = repo.createFolder(null, "旅行")
        val ids = (1..4).map { shot(it) }
        ids.forEach { repo.addShotToFolder(it, f, atSec = 1_763_000_000L) }

        val first = repo.folderShots(f, after = null, limit = 2)
        val second = repo.folderShots(f, after = first.next, limit = 2)

        assertEquals(4, (first.items + second.items).map { it.id }.distinct().size)
        assertTrue(first.items.map { it.id }.none { it in second.items.map { s -> s.id } })
    }

    @Test
    fun 刪掉圖之後歸屬也不見了() = runTest {
        val f = repo.createFolder(null, "旅行")
        val s = shot(1)
        repo.addShotToFolder(s, f)

        repo.deleteShot(s)

        assertEquals(0, repo.folderShotCount(f))
        assertEquals(emptySet<Long>(), repo.foldersOf(s))
    }
}
