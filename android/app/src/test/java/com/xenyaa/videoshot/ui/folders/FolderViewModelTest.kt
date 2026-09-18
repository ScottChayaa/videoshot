package com.xenyaa.videoshot.ui.folders

import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.FolderPage
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun shot(id: Long) = ShotRow(
        id = id, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = "2026-03-01", place = null, description = null,
    )

    private open class Repo : FakeLibraryRepo() {
        var node: FolderNode? = FolderNode(1, null, "旅行", 1)
        var children: List<FolderCard> = listOf(FolderCard(2, "宜蘭", 3, 100, emptyList()))
        var total = 0
        var pages: MutableList<FolderPage> = mutableListOf(FolderPage(emptyList(), null))
        var folderShotsCalls = 0
        var createdParent: Long? = -1L
        val deleted = mutableListOf<Long>()

        override suspend fun folderNode(id: Long): FolderNode? = node
        override suspend fun folderCards(parentId: Long?): List<FolderCard> = children
        override suspend fun folderShotCount(folderId: Long): Int = total
        override suspend fun folderShots(folderId: Long, after: FolderCursor?, limit: Int): FolderPage {
            val index = folderShotsCalls.coerceAtMost(pages.size - 1)
            folderShotsCalls++
            return pages[index]
        }
        override suspend fun createFolder(parentId: Long?, name: String): Long {
            createdParent = parentId
            return 9
        }
        override suspend fun deleteFolder(id: Long) { deleted += id }
    }

    private fun vm(repo: Repo = Repo(), folderId: Long = 1, pageSize: Int = 50) =
        FolderViewModel(repo, folderId, pageSize)

    @Test
    fun 一建好就有節點子資料夾第一頁與總張數() = runTest {
        val repo = Repo().apply {
            total = 2
            pages = mutableListOf(FolderPage(listOf(shot(10), shot(11)), null))
        }
        val model = vm(repo)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals("旅行", state.node?.name)
        assertEquals(listOf("宜蘭"), state.children.map { it.name })
        assertEquals(listOf(10L, 11L), state.items.map { it.id })
        assertEquals(2, state.total)
        assertNull(state.error)
    }

    @Test
    fun loadMore接第二頁到底就不再打repo() = runTest {
        val cursor = FolderCursor(200, 20)
        val repo = Repo().apply {
            pages = mutableListOf(
                FolderPage(listOf(shot(20)), cursor),
                FolderPage(listOf(shot(21)), null),
            )
        }
        val model = vm(repo)
        advanceUntilIdle()
        assertEquals(listOf(20L), model.state.value.items.map { it.id })
        assertFalse(model.state.value.endReached)

        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(20L, 21L), model.state.value.items.map { it.id })
        assertEquals(true, model.state.value.endReached)

        val callsAfterEnd = repo.folderShotsCalls
        model.loadMore()
        advanceUntilIdle()
        assertEquals("到底之後 loadMore 不再打 repo", callsAfterEnd, repo.folderShotsCalls)
    }

    @Test
    fun 新增子資料夾parentId是這個資料夾的id不是null() = runTest {
        val repo = Repo()
        val model = vm(repo, folderId = 1)
        advanceUntilIdle()

        model.startCreateChild()
        model.editName("宜蘭二訪")
        model.confirmEditor()
        advanceUntilIdle()

        assertEquals(1L, repo.createdParent)
    }

    @Test
    fun `deleteSelf 會呼叫 repo deleteFolder 並執行回呼`() = runTest {
        val repo = Repo()
        val model = vm(repo, folderId = 1)
        advanceUntilIdle()

        model.askDeleteSelf()
        var deletedCalled = false
        model.confirmDelete(onDeleted = { deletedCalled = true })
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.deleted)
        assertEquals(true, deletedCalled)
        assertNull(model.state.value.deleting)
    }

    /**
     * 裁決 2 的回歸測試：刪的是**子資料夾**時，不能觸發 onDeleted（那是給「刪自己、退回上一層」用的），
     * 而是留在原地重新載入。
     */
    @Test
    fun 刪除子資料夾不觸發onDeleted而是留在原地重新載入() = runTest {
        val repo = Repo().apply { children = listOf(FolderCard(2, "宜蘭", 3, 100, emptyList())) }
        val model = vm(repo, folderId = 1)
        advanceUntilIdle()
        val child = model.state.value.children.single()

        model.askDeleteChild(child)
        repo.children = emptyList()
        var deletedCalled = false
        model.confirmDelete(onDeleted = { deletedCalled = true })
        advanceUntilIdle()

        assertEquals(listOf(2L), repo.deleted)
        assertEquals("刪的是子資料夾，不是這一頁自己，不該觸發退回上一層", false, deletedCalled)
        assertEquals(emptyList<FolderCard>(), model.state.value.children)
        assertNull(model.state.value.deleting)
    }

    /** 裁決 2 的回歸測試：`startRenameChild` 帶入的是子資料夾自己的名字與 id，不是這一頁（父資料夾）的。 */
    @Test
    fun startRenameChild帶入子資料夾自己的名字與id() = runTest {
        val repo = Repo().apply { children = listOf(FolderCard(2, "宜蘭", 3, 100, emptyList())) }
        val model = vm(repo, folderId = 1)
        advanceUntilIdle()
        val child = model.state.value.children.single()

        model.startRenameChild(child)

        assertEquals(2L, model.state.value.editor?.target)
        assertEquals("宜蘭", model.state.value.editor?.name)
    }

    @Test
    fun 讀取失敗會攤成錯誤訊息不卡住loading() = runTest {
        val repo = object : Repo() {
            override suspend fun folderNode(id: Long): FolderNode? = throw RuntimeException("壞了")
        }
        val model = vm(repo)
        advanceUntilIdle()

        assertEquals("載入失敗，請再試一次", model.state.value.error)
        assertEquals(false, model.state.value.loading)
    }
}
