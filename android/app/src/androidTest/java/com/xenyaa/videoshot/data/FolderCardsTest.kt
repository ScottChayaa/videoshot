package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.library.entity.ShotEntity
import com.xenyaa.videoshot.data.library.entity.ShotFolderEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderCardsTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    /** 回傳新建的 shot id。同一支影片重複呼叫沒關係，`upsert` 會處理。 */
    private suspend fun shot(n: Int, eventDate: String = "2026-03-01"): Long {
        db.videoDao().upsert(VideoEntity("v1", "t", "c", "2026-03-01T00:00:00Z", 600, "public", null, 1L))
        return db.shotDao().insert(
            ShotEntity(
                id = 0, videoId = "v1", atSec = n.toDouble(), source = "storyboard",
                frameIndex = n, sbLevel = 3, eventDate = eventDate, place = null,
                description = null, aiTranscript = null, aiVisualDesc = null, aiRaw = null,
                createdAt = 1L,
            )
        )
    }

    private suspend fun put(shotId: Long, folderId: Long, addedAt: Long) {
        db.folderDao().link(ShotFolderEntity(shotId, folderId, addedAt))
    }

    @Test
    fun 只列出指定那一層的資料夾() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        repo.createFolder(旅行, "宜蘭")
        repo.createFolder(null, "貓")

        assertEquals(listOf("旅行", "貓"), repo.folderCards(null).map { it.name }.sorted())
        assertEquals(listOf("宜蘭"), repo.folderCards(旅行).map { it.name })
    }

    @Test
    fun 張數含子孫層() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 宜蘭 = repo.createFolder(旅行, "宜蘭")
        val 礁溪 = repo.createFolder(宜蘭, "礁溪")
        put(shot(1), 旅行, 100)
        put(shot(2), 宜蘭, 200)
        put(shot(3), 礁溪, 300)

        assertEquals(3, repo.folderCards(null).single().shotCount)
    }

    /** 同一張圖同時放在父與子只算一次 —— 不然計數會比實際張數多。 */
    @Test
    fun 同一張圖在父與子只算一次() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 宜蘭 = repo.createFolder(旅行, "宜蘭")
        val id = shot(1)
        put(id, 旅行, 100)
        put(id, 宜蘭, 200)

        val card = repo.folderCards(null).single()
        assertEquals(1, card.shotCount)
        assertEquals(1, card.preview.size)
    }

    @Test
    fun 預覽是含子孫最近加入的四張() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 宜蘭 = repo.createFolder(旅行, "宜蘭")
        val ids = (1..5).map { shot(it) }
        put(ids[0], 旅行, 100)
        put(ids[1], 宜蘭, 200)
        put(ids[2], 旅行, 300)
        put(ids[3], 宜蘭, 400)
        put(ids[4], 旅行, 500)

        val preview = repo.folderCards(null).single().preview
        assertEquals(4, preview.size)
        assertEquals(listOf(ids[4], ids[3], ids[2], ids[1]), preview.map { it.id })
    }

    @Test
    fun 最近加入時間取含子孫的最大值() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 宜蘭 = repo.createFolder(旅行, "宜蘭")
        put(shot(1), 旅行, 100)
        put(shot(2), 宜蘭, 900)

        assertEquals(900L, repo.folderCards(null).single().lastActivityAt)
    }

    /** 沒有圖的資料夾用建立時間當「最近活動」，排序才不會把它們全部擠在同一格。 */
    @Test
    fun 空資料夾的最近活動是建立時間() = runTest {
        repo.createFolder(null, "空的")

        val card = repo.folderCards(null).single()
        assertEquals(0, card.shotCount)
        assertTrue(card.preview.isEmpty())
        assertTrue("建立時間應該是最近的秒數，拿到 ${card.lastActivityAt}", card.lastActivityAt > 1_700_000_000L)
    }
}
