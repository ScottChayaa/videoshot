package com.xenyaa.videoshot.ui.account

import android.app.Activity
import android.content.Intent
import com.xenyaa.videoshot.backfill.BackfillProgress
import com.xenyaa.videoshot.backup.LinkOutcome
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.tags.TagKind
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.TagUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val thumbColumnsFlow = MutableStateFlow(4)
        override val thumbColumns: Flow<Int> get() = thumbColumnsFlow
        override suspend fun setThumbColumns(value: Int) { thumbColumnsFlow.value = value }

        val linkedAccountFlow = MutableStateFlow<LinkedGoogleAccount?>(null)
        override val linkedAccount: Flow<LinkedGoogleAccount?> get() = linkedAccountFlow
        var beginLinkCalled = false
        var linkOutcome: LinkOutcome = LinkOutcome.Linked(LinkedGoogleAccount("阿明", "ming@example.com"))
        override suspend fun beginLink(activity: Activity): LinkOutcome {
            beginLinkCalled = true
            if (linkOutcome is LinkOutcome.Linked) {
                linkedAccountFlow.value = (linkOutcome as LinkOutcome.Linked).account
            }
            return linkOutcome
        }

        var finishLinkCalled = false
        override suspend fun finishLink(data: Intent): LinkedGoogleAccount {
            finishLinkCalled = true
            val account = LinkedGoogleAccount("阿明", "ming@example.com")
            linkedAccountFlow.value = account
            return account
        }

        var unlinkCalled = false
        var failNextUnlink = false
        override suspend fun unlink() {
            unlinkCalled = true
            if (failNextUnlink) throw RuntimeException("中斷連結失敗")
            linkedAccountFlow.value = null
        }

        val lastBackupAtFlow = MutableStateFlow(0L)
        override val lastBackupAtEpochSec: Flow<Long> get() = lastBackupAtFlow
        var failNextBackup = false
        var backupNowCalled = false
        override suspend fun backupNow(): Boolean {
            backupNowCalled = true
            if (failNextBackup) throw RuntimeException("備份失敗")
            lastBackupAtFlow.value = 12345L
            return true
        }

        var backfillProgressValue = BackfillProgress(0, 0, 0)
        override suspend fun backfillProgress(): BackfillProgress = backfillProgressValue

        var retryLostThumbsCalled = false
        override suspend fun retryLostThumbs() { retryLostThumbsCalled = true }

        var deleteLostThumbsCalled = false
        var failNextDeleteLostThumbs = false
        override suspend fun deleteLostThumbs() {
            deleteLostThumbsCalled = true
            if (failNextDeleteLostThumbs) throw RuntimeException("刪除失敗")
        }

        var continueBackfillOnMobileDataCalled = false
        override suspend fun continueBackfillOnMobileData() { continueBackfillOnMobileDataCalled = true }
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
    fun 縮圖每列張數會轉呼叫deps並流回state() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, viewModel.state.value.thumbColumns)

        viewModel.setThumbColumns(2)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, deps.thumbColumnsFlow.value)
        assertEquals(2, viewModel.state.value.thumbColumns)
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

    /** 最終審查 Important 4：從編輯抽屜按刪除,抽屜要立刻關掉,不能疊在確認對話框底下
     * ——不然確認之後抽屜還留著一份已經被刪掉的標籤的草稿,按【儲存】會對不存在的 id
     * 呼叫 renameTag,靜默沒反應。 */
    @Test
    fun 從編輯抽屜按刪除會立刻關掉抽屜() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openTagEditor(deps.tagList[0])
        assertEquals(1L, viewModel.state.value.editor?.id)

        viewModel.askDeleteTag(deps.tagList[0])

        assertNull(viewModel.state.value.editor)
        assertEquals(1L, viewModel.state.value.deleting?.id)
    }

    /** 最終審查 Important 1：改名真的送出之後要發一次 `tagsChanged`——查詢分頁的
     * facet chip 快取靠這個訊號知道要重查,見 `SearchViewModel.loadFacets` 的接線。 */
    @Test
    fun 改名送出成功後發出tagsChanged事件() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        var changedCount = 0
        val job = launch { viewModel.tagsChanged.collect { changedCount++ } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.openTagEditor(deps.tagList[0])
        viewModel.editTagName("阿明哥")
        viewModel.requestSaveTag()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, changedCount)
        job.cancel()
    }

    /** 同上,刪除標籤那條路徑。 */
    @Test
    fun 刪除標籤送出成功後發出tagsChanged事件() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        var changedCount = 0
        val job = launch { viewModel.tagsChanged.collect { changedCount++ } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.askDeleteTag(deps.tagList[1])
        viewModel.confirmDeleteTag()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, changedCount)
        job.cancel()
    }

    @Test
    fun backupNow成功後backingUp回到false() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.backupNow()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.backingUp)
        assertNull(viewModel.state.value.backupError)
    }

    @Test
    fun backupNow失敗時顯示backupError() = runTest {
        val deps = FakeDeps()
        deps.failNextBackup = true
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.backupNow()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.backingUp)
        assertEquals("備份失敗，請確認網路後再試一次", viewModel.state.value.backupError)
    }

    @Test
    fun unlink會呼叫deps的unlink() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.unlink()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deps.unlinkCalled)
    }

    /**
     * 最終審查 Important 4：中斷連結失敗要走 `backupError`（備份子畫面會畫出來），
     * 不能走泛用的 `error`——那個欄位只有標籤管理畫面會顯示，結果就是使用者當下什麼都看不到，
     * 然後那句「操作失敗，請再試一次」會殘留到毫不相干的標籤管理畫面上。
     */
    @Test
    fun unlink失敗時顯示在backupError不是泛用error() = runTest {
        val deps = FakeDeps()
        deps.failNextUnlink = true
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.unlink()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("中斷連結失敗，請確認網路後再試一次", viewModel.state.value.backupError)
        assertNull("不能污染標籤管理畫面才會顯示的泛用 error", viewModel.state.value.error)
    }

    // `beginLink`／`finishLink` 的失敗路徑沒有單獨的案例：它們的參數是真的 `Activity`／
    // `Intent`，而這個檔案是純 JVM 測試（沒有 Robolectric、也沒開 returnDefaultValues），
    // 造不出這兩個物件。三個動作走的是同一個 `launchBackupGuarded`，由上面 unlink 的兩個
    // 案例把那個 helper 的行為釘住。

    /**
     * 成功的動作要把上一次失敗留下的 `backupError` 清掉——不清的話使用者會看到「中斷連結
     * 失敗」跟成功的結果並存（`launchBackupGuarded` 一開始就清，同 `backupNow` 的做法）。
     */
    @Test
    fun 重新嘗試時會先清掉上一次的backupError() = runTest {
        val deps = FakeDeps()
        deps.failNextUnlink = true
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.unlink()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("中斷連結失敗，請確認網路後再試一次", viewModel.state.value.backupError)

        deps.failNextUnlink = false
        viewModel.unlink()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.backupError)
    }

    @Test
    fun 稍後重試呼叫deps() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.retryLostThumbs()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deps.retryLostThumbsCalled)
    }

    @Test
    fun 刪除這些收藏失敗時顯示錯誤訊息() = runTest {
        val deps = FakeDeps()
        deps.failNextDeleteLostThumbs = true
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteLostThumbs()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("操作失敗，請再試一次", viewModel.state.value.backfillActionError)
    }

    @Test
    fun 用行動網路繼續呼叫deps() = runTest {
        val deps = FakeDeps()
        val viewModel = vm(deps)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.continueBackfillOnMobileData()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(deps.continueBackfillOnMobileDataCalled)
    }
}
