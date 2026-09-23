package com.xenyaa.videoshot.ui.folders

import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.FolderPage
import com.xenyaa.videoshot.data.repo.model.ShotRow
import kotlinx.coroutines.CompletableDeferred
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

        /** 只卡「下一次」`folderShots` 呼叫，取用後自動歸零 —— 手法同 `HomeViewModelTest`。 */
        var folderShotsGate: CompletableDeferred<Unit>? = null

        override suspend fun folderNode(id: Long): FolderNode? = node
        override suspend fun folderCards(parentId: Long?): List<FolderCard> = children
        override suspend fun folderShotCount(folderId: Long): Int = total
        override suspend fun folderShots(folderId: Long, after: FolderCursor?, limit: Int): FolderPage {
            val index = folderShotsCalls.coerceAtMost(pages.size - 1)
            folderShotsCalls++
            folderShotsGate?.let { gate ->
                folderShotsGate = null
                gate.await()
            }
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

    /**
     * N1 的回歸測試：`FolderDao.cardsIn` 沒有 `ORDER BY`，回傳順序是 SQLite 未定義的順序。
     * `FolderSort` 的 KDoc 明寫清單頁與資料夾頁共用同一組排序，子資料夾列要固定名稱升冪，
     * 不能原樣把 repo 回傳的順序畫出來。
     */
    @Test
    fun 子資料夾固定用名稱升冪排序不管repo回傳的順序() = runTest {
        val repo = Repo().apply {
            children = listOf(
                FolderCard(3, "貓", 1, 100, emptyList()),
                FolderCard(2, "宜蘭", 3, 200, emptyList()),
                FolderCard(4, "工作", 9, 300, emptyList()),
            )
        }
        val model = vm(repo)
        advanceUntilIdle()

        assertEquals(listOf("工作", "宜蘭", "貓"), model.state.value.children.map { it.name })
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

    /**
     * 審查 Important 1 的回歸測試：前一次 `loadMore()` 還沒完成（`loading == true`）時
     * 再呼叫一次，不能因為沒擋 `loading` 而讓兩個協程各打一次 `repo.folderShots`——
     * 那會用同一個 `cursor` 各撈一次，同一頁被接兩次、清單出現重複縮圖。
     */
    @Test
    fun loadMore重入保護_上一次還沒完成時再呼叫只會打一次repo() = runTest {
        val repo = Repo().apply {
            pages = mutableListOf(
                FolderPage(listOf(shot(20)), FolderCursor(200, 20)),
                FolderPage(listOf(shot(21)), null),
            )
        }
        val model = vm(repo)
        advanceUntilIdle() // reload() 內建的第一頁已經跑完（folderShotsCalls == 1）

        val gate = CompletableDeferred<Unit>()
        repo.folderShotsGate = gate
        model.loadMore() // 觸發續載，協程卡在 gate.await() 之前
        advanceUntilIdle() // 讓它真的跑起來、卡住

        assertEquals("協程卡住時 loading 應該已經是 true", true, model.state.value.loading)
        val callsWhileLoading = repo.folderShotsCalls

        model.loadMore() // 前一次還沒完成，這次應該被 loading 擋掉
        advanceUntilIdle()
        assertEquals("loading 還沒回到 false，不該再打第二次 repo", callsWhileLoading, repo.folderShotsCalls)

        gate.complete(Unit) // 讓第一次真的完成
        advanceUntilIdle()

        assertEquals(false, model.state.value.loading)
        assertEquals("只完成過一次續載，不該有重複的頁", listOf(20L, 21L), model.state.value.items.map { it.id })
        assertEquals("gate 完成不算新的一次呼叫", callsWhileLoading, repo.folderShotsCalls)
    }

    /**
     * N3 的回歸測試：Lightbox 在資料夾裡刪圖不該整頁重灌（`reload()` 會把 `items` 清空、
     * 從第一頁重撈 50 筆）——超過一頁、使用者已經往下捲過時，這會把分頁跳回第一頁，
     * 手冊 §三「刪除後自動停在下一張」在這條路徑上就不成立了。改成外科手術式地拔掉那一列，
     * `cursor`／`endReached` 都不能動。
     */
    @Test
    fun onShotDeleted就地移除那一列並減總數不動游標() = runTest {
        val cursor = FolderCursor(200, 20)
        val repo = Repo().apply {
            total = 3
            pages = mutableListOf(FolderPage(listOf(shot(20), shot(21)), cursor))
        }
        val model = vm(repo)
        advanceUntilIdle()
        assertEquals(listOf(20L, 21L), model.state.value.items.map { it.id })
        assertEquals(3, model.state.value.total)
        assertFalse(model.state.value.endReached)

        model.onShotDeleted(20)
        advanceUntilIdle()

        assertEquals(listOf(21L), model.state.value.items.map { it.id })
        assertEquals(2, model.state.value.total)
        assertEquals("刪除不該動到游標", cursor, model.state.value.cursor)
        assertFalse("刪除不該動到 endReached", model.state.value.endReached)
    }

    /** N3：刪掉的圖如果同時也在子資料夾裡，子資料夾卡片的張數與預覽拼貼要跟著變。 */
    @Test
    fun onShotDeleted也重查子資料夾的張數與預覽() = runTest {
        val repo = Repo().apply {
            children = listOf(FolderCard(2, "宜蘭", 3, 100, emptyList()))
            pages = mutableListOf(FolderPage(listOf(shot(20)), null))
        }
        val model = vm(repo)
        advanceUntilIdle()

        repo.children = listOf(FolderCard(2, "宜蘭", 2, 100, emptyList()))
        model.onShotDeleted(20)
        advanceUntilIdle()

        assertEquals(2, model.state.value.children.single().shotCount)
    }

    /**
     * N3 已知 #12 的回歸測試：Lightbox 捲到底時本來就會發 `loadMore()`，跟刪除觸發的
     * `reload()` 撞在一起——不取消前一個 job 的話，晚回來的 `loadMore()` 會把 `reload()`
     * 剛設好的狀態蓋掉。手法同 `HomeViewModelTest.換篩選會取消還沒回來的舊請求_不會被它蓋掉`。
     */
    @Test
    fun reload會取消還沒回來的loadMore_不會被它蓋掉() = runTest {
        val cursor = FolderCursor(200, 20)
        val repo = Repo().apply {
            pages = mutableListOf(
                FolderPage(listOf(shot(20)), cursor), // reload 內建的第一頁
                FolderPage(listOf(shot(21)), null), // loadMore 續載頁——會被卡住、之後要被取消
                FolderPage(listOf(shot(30)), null), // reload 重新叫的新第一頁
            )
        }
        val model = vm(repo)
        advanceUntilIdle()
        assertEquals(listOf(20L), model.state.value.items.map { it.id })

        val gate = CompletableDeferred<Unit>()
        repo.folderShotsGate = gate
        model.loadMore() // 卡在 gate.await() 之前
        advanceUntilIdle()
        assertEquals(true, model.state.value.loading)

        model.reload() // 應該取消還在跑的 loadMore
        advanceUntilIdle()

        gate.complete(Unit) // 讓被取消的 loadMore 協程恢復；真的被取消的話，這裡不該再寫入狀態
        advanceUntilIdle()

        assertEquals(
            "items 應該是 reload 的結果，不能被取消的 loadMore 晚到覆蓋",
            listOf(30L),
            model.state.value.items.map { it.id },
        )
        assertEquals(false, model.state.value.loading)
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
