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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

        override suspend fun folderCards(parentId: Long?) = cards
        override suspend fun createFolder(parentId: Long?, name: String): Long {
            createThrows?.let { throw IllegalArgumentException(it) }
            created += parentId to name
            return 9
        }
        override suspend fun deleteFolder(id: Long) { deleted += id }
    }

    private class Settings(val sort: MutableStateFlow<FolderSort>) : ShellSettings {
        override val filterStrength: Flow<FilterStrength> = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen: Flow<Boolean> = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen: Flow<Boolean> = flowOf(true)
        override suspend fun markLightboxHintSeen() = Unit
        override val folderSort: Flow<FolderSort> = sort
        override suspend fun setFolderSort(value: FolderSort) { sort.value = value }
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

    @Test
    fun 刪除之後關掉確認框並重新載入() = runTest {
        val repo = Repo()
        val model = vm(repo)
        advanceUntilIdle()
        val card = model.state.value.cards.single()

        model.askDelete(card)
        repo.cards = emptyList()
        model.delete(card)
        advanceUntilIdle()

        assertEquals(listOf(1L), repo.deleted)
        assertNull(model.state.value.deleting)
        assertEquals(emptyList<FolderCard>(), model.state.value.cards)
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
}
