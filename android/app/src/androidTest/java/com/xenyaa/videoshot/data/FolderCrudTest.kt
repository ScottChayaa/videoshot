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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderCrudTest {

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
    fun 改名會生效並標記有變更() = runTest {
        val id = repo.createFolder(null, "旅行")
        changes = 0

        repo.renameFolder(id, "旅遊")

        assertEquals("旅遊", repo.folderNode(id)?.name)
        assertEquals("改名也是 library.db 的變更，備份要知道", 1, changes)
    }

    @Test
    fun 改名撞到同層的名字會被擋() = runTest {
        repo.createFolder(null, "旅行")
        val 貓 = repo.createFolder(null, "貓")

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.renameFolder(貓, "旅行") }
        }
        assertEquals("貓", repo.folderNode(貓)?.name)
    }

    /** 改成自己原本的名字不算重名 —— 使用者只是打開對話框又按了儲存。 */
    @Test
    fun 改成自己原本的名字不算重名() = runTest {
        val id = repo.createFolder(null, "旅行")
        repo.renameFolder(id, "旅行")
        assertEquals("旅行", repo.folderNode(id)?.name)
    }

    /** 不同層可以有同名資料夾（「旅行/宜蘭」與「工作/宜蘭」）。 */
    @Test
    fun 不同層同名沒問題() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 工作 = repo.createFolder(null, "工作")
        repo.createFolder(旅行, "宜蘭")
        repo.createFolder(工作, "宜蘭")

        assertEquals(listOf("宜蘭"), repo.folderCards(工作).map { it.name })
    }

    @Test
    fun 名稱空白或超過五十字會被擋() = runTest {
        val id = repo.createFolder(null, "旅行")
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.renameFolder(id, "   ") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.renameFolder(id, "字".repeat(51)) }
        }
        assertEquals("旅行", repo.folderNode(id)?.name)
    }

    @Test
    fun 刪除連子資料夾與關聯一起消失但圖還在() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 宜蘭 = repo.createFolder(旅行, "宜蘭")
        val a = shot(1)
        val b = shot(2)
        db.folderDao().link(ShotFolderEntity(a, 旅行, 100))
        db.folderDao().link(ShotFolderEntity(b, 宜蘭, 200))
        changes = 0

        repo.deleteFolder(旅行)

        assertNull(repo.folderNode(旅行))
        assertNull("子資料夾要跟著消失", repo.folderNode(宜蘭))
        assertEquals(0, db.folderDao().count())
        assertEquals("關聯要跟著消失", 0, db.folderDao().linkCountOfShot(a) + db.folderDao().linkCountOfShot(b))
        assertEquals("圖不動（規格第六節）", 2, repo.shotsOfVideo("v1").size)
        assertEquals(1, changes)
    }

    @Test
    fun 深度上限五層() = runTest {
        var parent: Long? = null
        repeat(5) { level -> parent = repo.createFolder(parent, "第 ${level + 1} 層") }

        assertEquals(5, repo.folderNode(parent!!)?.depth)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.createFolder(parent, "第 6 層") }
        }
    }

    @Test
    fun 整棵樹帶著層數回來() = runTest {
        val 旅行 = repo.createFolder(null, "旅行")
        val 宜蘭 = repo.createFolder(旅行, "宜蘭")
        repo.createFolder(null, "貓")

        val tree = repo.folderTree().associateBy { it.name }
        assertEquals(3, tree.size)
        assertEquals(1, tree.getValue("旅行").depth)
        assertEquals(2, tree.getValue("宜蘭").depth)
        assertEquals(旅行, tree.getValue("宜蘭").parentId)
        assertTrue(tree.getValue("貓").parentId == null)
        assertEquals(宜蘭, tree.getValue("宜蘭").id)
    }
}
