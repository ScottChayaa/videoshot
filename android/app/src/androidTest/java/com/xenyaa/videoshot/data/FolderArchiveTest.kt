package com.xenyaa.videoshot.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.data.library.LibraryDatabase
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.RoomLibraryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 相簿封存（規格第六節「封存相簿」）：寫 `folder.archived_at`，只有根層能封存。 */
@RunWith(AndroidJUnit4::class)
class FolderArchiveTest {

    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepo

    @Before fun setUp() {
        db = inMemoryLibraryDb()
        repo = RoomLibraryRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    @Test
    fun 新建的相簿沒有封存() = runTest {
        repo.createFolder(null, "旅行")
        assertNull(repo.folderCards(null).single().archivedAt)
    }

    @Test
    fun 封存後卡片帶封存時間_取消封存後變回null() = runTest {
        val id = repo.createFolder(null, "旅行")

        repo.setFolderArchived(id, true)
        assertNotNull(repo.folderCards(null).single().archivedAt)

        repo.setFolderArchived(id, false)
        assertNull(repo.folderCards(null).single().archivedAt)
    }

    @Test
    fun 封存的相簿仍然列在根層卡片裡_由畫面自己分() = runTest {
        val a = repo.createFolder(null, "旅行")
        repo.createFolder(null, "貓")
        repo.setFolderArchived(a, true)

        assertEquals(listOf("旅行", "貓"), repo.folderCards(null).map { it.name }.sorted())
    }

    @Test
    fun 新建撞到封存相簿的名字_訊息指向封存相簿() = runTest {
        val id = repo.createFolder(null, "旅行")
        repo.setFolderArchived(id, true)
        val e = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.createFolder(null, "旅行") }
        }
        assertEquals("封存相簿裡已經有「旅行」了", e.message)
    }

    @Test
    fun 改名撞到封存相簿的名字_訊息指向封存相簿() = runTest {
        val archived = repo.createFolder(null, "旅行")
        val other = repo.createFolder(null, "貓")
        repo.setFolderArchived(archived, true)
        val e = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.renameFolder(other, "旅行") }
        }
        assertEquals("封存相簿裡已經有「旅行」了", e.message)
    }

    @Test
    fun 子相簿不能封存() = runTest {
        val parent = repo.createFolder(null, "旅行")
        val child = repo.createFolder(parent, "宜蘭")
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.setFolderArchived(child, true) }
        }
    }

    @Test
    fun 找不到的相簿不能封存() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.setFolderArchived(999, true) }
        }
    }

    @Test
    fun 樹上的子相簿跟著根層一起是封存的() = runTest {
        val parent = repo.createFolder(null, "旅行")
        repo.createFolder(parent, "宜蘭")
        repo.createFolder(null, "貓")
        repo.setFolderArchived(parent, true)

        val tree = repo.folderTree().associateBy { it.name }
        assertTrue(tree.getValue("旅行").archived)
        assertTrue(tree.getValue("宜蘭").archived)
        assertFalse(tree.getValue("貓").archived)
    }

    @Test
    fun 封存的相簿照樣開得起來() = runTest {
        val id = repo.createFolder(null, "旅行")
        repo.setFolderArchived(id, true)

        val node = repo.folderNode(id)
        assertEquals("旅行", node?.name)
        assertTrue(node!!.archived)
    }
}
