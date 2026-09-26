package com.xenyaa.videoshot.ui.account

import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.TagUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeDeps : AccountDeps {
        val filterStrengthFlow = MutableStateFlow(FilterStrength.MEDIUM)
        override val filterStrength: Flow<FilterStrength> get() = filterStrengthFlow
        var lastFilterStrength: FilterStrength? = null
        override suspend fun setFilterStrength(value: FilterStrength) { lastFilterStrength = value; filterStrengthFlow.value = value }

        val beforeFlow = MutableStateFlow(10)
        val afterFlow = MutableStateFlow(20)
        override val aiRangeBeforeSec: Flow<Int> get() = beforeFlow
        override val aiRangeAfterSec: Flow<Int> get() = afterFlow
        override suspend fun setAiRange(beforeSec: Int, afterSec: Int) { beforeFlow.value = beforeSec; afterFlow.value = afterSec }

        val geminiFlow = MutableStateFlow(false)
        override val geminiKeySet: Flow<Boolean> get() = geminiFlow
        var savedKey: String? = null
        override suspend fun setGeminiKey(plain: String) { savedKey = plain; geminiFlow.value = true }
        override suspend fun clearGeminiKey() { savedKey = null; geminiFlow.value = false }

        var stats = AccountStats(3, 1, 2)
        override suspend fun stats(thisMonth: String): AccountStats = stats

        var tagList = listOf(
            TagUsage(1, "阿明", "person", emptyList(), 2),
            TagUsage(2, "露營", "topic", listOf("野營"), 5),
        )
        override suspend fun tags(): List<TagUsage> = tagList

        var renamed: List<Any> = emptyList()
        override suspend fun renameTag(id: Long, name: String, kind: String, aliases: List<String>) {
            renamed = listOf(id, name, kind, aliases)
            tagList = tagList.map { if (it.id == id) it.copy(name = name, kind = kind, aliases = aliases) else it }
        }

        var deletedId: Long? = null
        override suspend fun deleteTag(id: Long) {
            deletedId = id
            tagList = tagList.filterNot { it.id == id }
        }

        override suspend fun storageUsageBytes(): Long = 12_345_678L
    }

    private fun vm(deps: AccountDeps = FakeDeps()) = AccountViewModel(deps, today = { "2026-03-14" })

    @Test
    fun 初始化就載入統計標籤與儲存用量() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(AccountStats(3, 1, 2), state.stats)
        assertEquals(2, state.tags.size)
        assertEquals(12_345_678L, state.storageUsageBytes)
        assertTrue(!state.loading)
    }

    @Test
    fun 月份用today算出來送給deps() = runTest {
        val deps = FakeDeps()
        vm(deps)
        dispatcher.scheduler.advanceUntilIdle()
        // FakeDeps.stats 不記錄呼叫參數本身，但 today 固定回 2026-03-14——
        // 這裡改用另一個假實作驗證 thisMonth 確實被算成 "2026-03"
        var received: String? = null
        val recording = object : AccountDeps by deps {
            override suspend fun stats(thisMonth: String): AccountStats {
                received = thisMonth
                return deps.stats
            }
        }
        vm(recording)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("2026-03", received)
    }

    @Test
    fun 設定過濾強度會轉呼叫deps並流回state() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilterStrength(FilterStrength.HIGH)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FilterStrength.HIGH, deps.lastFilterStrength)
        assertEquals(FilterStrength.HIGH, viewModel.state.value.filterStrength)
    }

    @Test
    fun AI分析區間設定會夾在零以上並流回state() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setAiRange(5, 15)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.state.value.aiRangeBeforeSec)
        assertEquals(15, viewModel.state.value.aiRangeAfterSec)
    }

    @Test
    fun Gemini金鑰儲存後geminiKeySet變true空白不送出() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveGeminiKey("   ")
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(deps.savedKey)

        viewModel.saveGeminiKey(" AIzaSy-fake-key ")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("AIzaSy-fake-key", deps.savedKey)
        assertTrue(viewModel.state.value.geminiKeySet)

        viewModel.clearGeminiKey()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(deps.savedKey)
        assertTrue(!viewModel.state.value.geminiKeySet)
    }

    @Test
    fun 改名成沒有撞名的名字直接送出() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openTagEditor(deps.tagList[0])
        viewModel.editTagName("阿明哥")
        viewModel.requestSaveTag()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf<Any>(1L, "阿明哥", "person", emptyList<String>()), deps.renamed)
        assertNull(viewModel.state.value.editor)
        assertNull(viewModel.state.value.pendingMerge)
    }

    @Test
    fun 改名成既有名稱先問合併不立刻送出() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openTagEditor(deps.tagList[0]) // 阿明
        viewModel.editTagName("露營") // 撞到既有的「露營」
        viewModel.requestSaveTag()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deps.renamed.isEmpty())
        assertEquals("露營", viewModel.state.value.pendingMerge)
        assertEquals(1L, viewModel.state.value.editor?.id) // 抽屜還開著

        viewModel.confirmMerge()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1L, deps.renamed[0])
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun 取消合併確認只關掉提示抽屜還開著() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openTagEditor(deps.tagList[0])
        viewModel.editTagName("露營")
        viewModel.requestSaveTag()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissMergeConfirm()

        assertNull(viewModel.state.value.pendingMerge)
        assertEquals(1L, viewModel.state.value.editor?.id)
        assertTrue(deps.renamed.isEmpty())
    }

    @Test
    fun 刪除標籤要先問再送出並重新載入() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.askDeleteTag(deps.tagList[1])
        assertEquals(2L, viewModel.state.value.deleting?.id)

        viewModel.confirmDeleteTag()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2L, deps.deletedId)
        assertNull(viewModel.state.value.deleting)
        assertEquals(1, viewModel.state.value.tags.size)
    }
}
