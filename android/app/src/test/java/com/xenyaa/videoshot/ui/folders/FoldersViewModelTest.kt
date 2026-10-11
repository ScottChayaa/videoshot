package com.xenyaa.videoshot.ui.folders

import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.settings.ShellSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private open class Repo : FakeLibraryRepo() {
        var cards = listOf(FolderCard(1, "旅行", 3, 100, emptyList()))
        var createThrows: String? = null
        val created = mutableListOf<Pair<Long?, String>>()
        val deleted = mutableListOf<Long>()
        val archivedCalls = mutableListOf<Pair<Long, Boolean>>()
        var archiveThrows = false
        /** 第幾本（從 0 算）開始失敗——模擬多本操作做到一半壞掉。 */
        var archiveFailFrom: Int? = null
        var deleteFailFrom: Int? = null

        var cardsCalls = 0
        override suspend fun folderCards(parentId: Long?): List<FolderCard> { cardsCalls++; return cards }
        override suspend fun createFolder(parentId: Long?, name: String): Long {
            createThrows?.let { throw IllegalArgumentException(it) }
            created += parentId to name
            return 9
        }
        override suspend fun deleteFolder(id: Long) {
            deleteFailFrom?.let { if (deleted.size >= it) error("模擬刪除失敗") }
            deleted += id
        }
        override suspend fun setFolderArchived(id: Long, archived: Boolean) {
            if (archiveThrows) error("模擬寫入失敗")
            archiveFailFrom?.let { if (archivedCalls.size >= it) error("模擬寫入失敗") }
            archivedCalls += id to archived
        }
    }

    private class Settings(val sort: MutableStateFlow<FolderSort>) : ShellSettings {
        override val filterStrength: Flow<FilterStrength> = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen: Flow<Boolean> = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen: Flow<Boolean> = flowOf(true)
        override suspend fun markLightboxHintSeen() = Unit
        override val folderSort: Flow<FolderSort> = sort
        override suspend fun setFolderSort(value: FolderSort) { sort.value = value }
        override val restoreDecisionMade: Flow<Boolean> = flowOf(true)
        override suspend fun markRestoreDecisionMade() = Unit
    }

    private fun vm(repo: Repo = Repo(), sort: MutableStateFlow<FolderSort> = MutableStateFlow(FolderSort.NAME_ASC)) =
        FoldersViewModel(repo, Settings(sort))

    @Test
    fun 一建好就載入卡片() = runTest {
        val repo = Repo()
        val model = vm(repo)
        advanceUntilIdle()

        assertEquals(listOf("旅行"), model.state.value.cards.map { it.name })
        assertNull(model.state.value.error)
    }

    @Test
    fun 排序偏好會帶進狀態也會存回去() = runTest {
        val sort = MutableStateFlow(FolderSort.NAME_ASC)
        val model = vm(sort = sort)
        advanceUntilIdle()

        model.setSort(FolderSort.RECENT)
        advanceUntilIdle()

        assertEquals(FolderSort.RECENT, sort.value)
        assertEquals(FolderSort.RECENT, model.state.value.sort)
    }

    @Test
    fun 新增成功會關掉對話框並重新載入() = runTest {
        val repo = Repo()
        val model = vm(repo)
        advanceUntilIdle()

        model.startCreate()
        model.create("宜蘭")
        advanceUntilIdle()

        assertEquals(listOf<Pair<Long?, String>>(null to "宜蘭"), repo.created)
        assertNull("成功就關掉", model.state.value.editor)
    }

    /** 同層重名：對話框不關，訊息留在欄位旁邊（repo 丟 IllegalArgumentException）。 */
    @Test
    fun 新增失敗把理由留在對話框() = runTest {
        val repo = Repo().apply { createThrows = "同一層已經有「旅行」了" }
        val model = vm(repo)
        advanceUntilIdle()

        model.startCreate()
        model.create("旅行")
        advanceUntilIdle()

        assertNotNull(model.state.value.editor)
        assertEquals("同一層已經有「旅行」了", model.state.value.editor?.error)
    }

    /** repo 丟非預期的例外不能把 process 帶走，也不能讓 loading 卡在 true（階段 7 的教訓）。 */
    @Test
    fun 讀取失敗會攤成錯誤訊息() = runTest {
        val repo = object : Repo() {
            override suspend fun folderCards(parentId: Long?): List<FolderCard> = throw RuntimeException("壞了")
        }
        val model = vm(repo)
        advanceUntilIdle()

        assertEquals("載入失敗，請再試一次", model.state.value.error)
        assertEquals(false, model.state.value.loading)
    }

    private val three = listOf(
        FolderCard(1, "旅行", 3, 100, emptyList()),
        FolderCard(2, "貓", 1, 100, emptyList()),
        FolderCard(3, "工作", 2, 100, emptyList()),
    )

    private fun collectNotices(model: FoldersViewModel, scope: kotlinx.coroutines.test.TestScope): MutableList<String> {
        val notices = mutableListOf<String>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { model.notices.collect { notices += it } }
        return notices
    }

    @Test
    fun 切換與清除選取() = runTest {
        val model = vm(Repo().apply { cards = three })
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(3)
        assertEquals(setOf(1L, 3L), model.state.value.selection)
        model.toggleSelect(1)
        assertEquals(setOf(3L), model.state.value.selection)
        model.clearSelection()
        assertEquals(emptySet<Long>(), model.state.value.selection)
    }

    @Test
    fun 封存多本_逐本寫入_清空選取_重查_提示本數() = runTest {
        val repo = Repo().apply { cards = three }
        val model = vm(repo)
        val notices = collectNotices(model, this)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(2)
        val callsBefore = repo.cardsCalls
        model.archiveSelected()
        advanceUntilIdle()

        assertEquals(listOf(1L to true, 2L to true), repo.archivedCalls)
        assertEquals(emptySet<Long>(), model.state.value.selection)
        assertTrue("封存後要重新載入清單", repo.cardsCalls > callsBefore)
        assertEquals(listOf("已封存 2 本相簿"), notices)
    }

    @Test
    fun 封存一本時提示說得出名稱() = runTest {
        val repo = Repo().apply { cards = three }
        val model = vm(repo)
        val notices = collectNotices(model, this)
        advanceUntilIdle()

        model.toggleSelect(2)
        model.archiveSelected()
        advanceUntilIdle()

        assertEquals(listOf(2L to true), repo.archivedCalls)
        assertEquals(listOf("已封存「貓」"), notices)
    }

    @Test
    fun 取消封存多本與一本的提示() = runTest {
        val archived = three.map { it.copy(archivedAt = 100) }
        val repo = Repo().apply { cards = archived }
        val model = vm(repo)
        val notices = collectNotices(model, this)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(3)
        val callsBefore = repo.cardsCalls
        model.unarchiveSelected()
        advanceUntilIdle()
        // 依封存相簿畫面的順序（封存時間相同時 id 大的在前）
        assertEquals(listOf(3L to false, 1L to false), repo.archivedCalls)
        assertEquals(emptySet<Long>(), model.state.value.selection)
        assertTrue("取消封存後要重新載入清單", repo.cardsCalls > callsBefore)

        model.toggleSelect(2)
        model.unarchiveSelected()
        advanceUntilIdle()

        assertEquals(listOf("已取消封存 2 本相簿", "已取消封存「貓」"), notices)
    }

    /** 做到一半失敗：已完成的留著，發失敗提示，選取照樣清空、清單照樣重查；不把清單變成「載入失敗」。 */
    @Test
    fun 封存中途失敗_保留已完成的並發失敗提示() = runTest {
        val repo = Repo().apply { cards = three; archiveFailFrom = 1 }
        val model = vm(repo)
        val notices = collectNotices(model, this)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(2)
        model.toggleSelect(3)
        val callsBefore = repo.cardsCalls
        model.archiveSelected()
        advanceUntilIdle()

        assertEquals("第一本寫進去了、之後的沒有", 1, repo.archivedCalls.size)
        assertEquals(listOf("封存失敗，請再試一次"), notices)
        assertEquals(emptySet<Long>(), model.state.value.selection)
        assertTrue("失敗也要重查", repo.cardsCalls > callsBefore)
        assertNull(model.state.value.error)
    }

    @Test
    fun 取消封存失敗的提示() = runTest {
        val repo = Repo().apply { cards = three.map { it.copy(archivedAt = 100) }; archiveThrows = true }
        val model = vm(repo)
        val notices = collectNotices(model, this)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.unarchiveSelected()
        advanceUntilIdle()

        assertEquals(listOf("取消封存失敗，請再試一次"), notices)
        assertEquals(emptySet<Long>(), model.state.value.selection)
    }

    /** 選多本按【刪除】：先開多本的確認框，確認後每本呼叫一次 repo.deleteFolder；刪除不發提示。 */
    @Test
    fun 刪除多本_先確認_確認後逐本刪除() = runTest {
        val repo = Repo().apply { cards = three }
        val model = vm(repo)
        val notices = collectNotices(model, this)
        advanceUntilIdle()

        model.toggleSelect(3)
        model.toggleSelect(1)
        model.askDeleteSelected()
        assertEquals(listOf(1L, 3L), model.state.value.deletingMany?.map { it.id }?.sorted())
        assertNull("多本不走單本的確認框", model.state.value.deleting)
        assertEquals("還沒確認不能刪", emptyList<Long>(), repo.deleted)

        repo.cards = listOf(three[1])
        model.deleteSelected()
        advanceUntilIdle()

        assertEquals(listOf(1L, 3L), repo.deleted.sorted())
        assertNull(model.state.value.deletingMany)
        assertEquals(emptySet<Long>(), model.state.value.selection)
        assertEquals(listOf("貓"), model.state.value.cards.map { it.name })
        assertEquals(emptyList<String>(), notices)
    }

    @Test
    fun 只選一本時刪除沿用單本確認框() = runTest {
        val repo = Repo().apply { cards = three }
        val model = vm(repo)
        advanceUntilIdle()

        model.toggleSelect(2)
        model.askDeleteSelected()
        assertEquals(2L, model.state.value.deleting?.id)
        assertNull(model.state.value.deletingMany)

        model.deleteSelected()
        advanceUntilIdle()
        assertEquals(listOf(2L), repo.deleted)
        assertNull(model.state.value.deleting)
        assertEquals(emptySet<Long>(), model.state.value.selection)
    }

    @Test
    fun 關掉刪除確認框不刪_選取保留() = runTest {
        val repo = Repo().apply { cards = three }
        val model = vm(repo)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(2)
        model.askDeleteSelected()
        model.dismissDelete()

        assertNull(model.state.value.deletingMany)
        assertEquals(setOf(1L, 2L), model.state.value.selection)
        assertEquals(emptyList<Long>(), repo.deleted)
    }

    @Test
    fun 刪除中途失敗發失敗提示_已刪的保留() = runTest {
        val repo = Repo().apply { cards = three; deleteFailFrom = 1 }
        val model = vm(repo)
        val notices = collectNotices(model, this)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(2)
        model.askDeleteSelected()
        model.deleteSelected()
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.deleted)
        assertEquals(listOf("刪除失敗，請再試一次"), notices)
        assertEquals(emptySet<Long>(), model.state.value.selection)
        assertNull(model.state.value.deletingMany)
        assertNull(model.state.value.error)
    }

    @Test
    fun 只選一本時重新命名開編輯框並離開多選() = runTest {
        val model = vm(Repo().apply { cards = three })
        advanceUntilIdle()

        model.toggleSelect(2)
        model.startRenameSelected()

        assertEquals(FolderEditor(2, "貓"), model.state.value.editor)
        assertEquals(emptySet<Long>(), model.state.value.selection)
    }

    @Test
    fun 選兩本時重新命名不動作() = runTest {
        val model = vm(Repo().apply { cards = three })
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(2)
        model.startRenameSelected()

        assertNull(model.state.value.editor)
        assertEquals(setOf(1L, 2L), model.state.value.selection)
    }

    /** 沒經過確認框就不能刪：確認框沒開時呼叫 deleteSelected 什麼都不做。 */
    @Test
    fun 沒開確認框就呼叫刪除不會刪任何東西() = runTest {
        val repo = Repo().apply { cards = three }
        val model = vm(repo)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.toggleSelect(2)
        model.deleteSelected()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), repo.deleted)
        assertEquals(setOf(1L, 2L), model.state.value.selection)
    }

    /** 寫入期間（還沒重查完）忽略切換選取，不然會選到下一輪就消失的卡片；完成後解除。 */
    @Test
    fun 封存寫入期間忽略切換選取_完成後解除() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = object : Repo() {
            override suspend fun setFolderArchived(id: Long, archived: Boolean) {
                gate.await()
                super.setFolderArchived(id, archived)
            }
        }.apply { cards = three }
        val model = vm(repo)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.archiveSelected()
        advanceUntilIdle()
        assertTrue("寫入中", model.state.value.busy)

        model.toggleSelect(2)
        assertEquals("寫入中切換選取無效", emptySet<Long>(), model.state.value.selection)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, model.state.value.busy)
        model.toggleSelect(2)
        assertEquals(setOf(2L), model.state.value.selection)
    }

    @Test
    fun 刪除失敗也會解除寫入中() = runTest {
        val repo = Repo().apply { cards = three; deleteFailFrom = 0 }
        val model = vm(repo)
        advanceUntilIdle()

        model.toggleSelect(1)
        model.askDeleteSelected()
        model.deleteSelected()
        advanceUntilIdle()

        assertEquals(false, model.state.value.busy)
        model.toggleSelect(3)
        assertEquals(setOf(3L), model.state.value.selection)
    }
}
