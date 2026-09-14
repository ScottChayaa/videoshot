package com.xenyaa.videoshot.wizard

import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.similarity.Fingerprint
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import com.xenyaa.videoshot.wizard.frames.FramePlan
import com.xenyaa.videoshot.wizard.frames.SheetReady
import com.xenyaa.videoshot.wizard.frames.FrameSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 狀態機**不依賴 Android**，所以用普通 JUnit 跑，不進 Robolectric ——
 * 這一組測試讀起來就是規格第五節「收斂」與「工具列」那幾條規則本身。
 */
/**
 * 刻意把後段的 sheet 先發出來 —— 模擬「同時下載 4 張、後面那張先回來」。
 * 真實作是 [com.xenyaa.videoshot.wizard.frames.StoryboardFrameSource]，這裡只要亂序這一個特性。
 */
private open class FrameSourceReordering(
    override val plan: FramePlan,
    private val hashes: List<Long>,
) : com.xenyaa.videoshot.wizard.frames.FrameSource {
    override fun load() = kotlinx.coroutines.flow.flow {
        // 先發 2、3 格，再發 0、1 格
        emit(SheetReady(listOf(2, 3), listOf(Fingerprint(2, hashes[2]), Fingerprint(3, hashes[3]))))
        emit(SheetReady(listOf(0, 1), listOf(Fingerprint(0, hashes[0]), Fingerprint(1, hashes[1]))))
    }
    override suspend fun bitmapOf(frameIndex: Int) = null
    override fun close() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class Step2StoreTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun store(
        source: FakeFrameSource,
        taken: Set<Int> = emptySet(),
        scope: TestScope,
    ) = Step2Store(source, taken, scope, dispatcher)

    // ---- 收斂 ----

    @Test
    fun 全部格子指紋都一樣時只留第一張() = runTest(dispatcher) {
        val source = FakeFrameSource(
            plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false),
            perSheet = 4,
            hashes = listOf(0L, 0L, 0L, 0L),
        )
        val s = store(source, scope = this)
        advanceUntilIdle()

        assertEquals(listOf(0), s.state.value.kept)
        assertEquals(3, s.state.value.hiddenCount)
    }

    @Test
    fun 指紋差很多時一張都不收斂() = runTest(dispatcher) {
        val s = store(FakeFrameSource.of(frameCount = 4, perSheet = 4), scope = this)
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2, 3), s.state.value.kept)
        assertEquals(0, s.state.value.hiddenCount)
    }

    @Test
    fun 收斂算完之前牆上是全部已就緒的格子() = runTest(dispatcher) {
        val source = FakeFrameSource(
            plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false),
            perSheet = 4,
            hashes = listOf(0L, 0L, 0L, 0L),
        )
        val s = store(source, scope = this)

        // 還沒 advance，什麼都還沒載
        assertTrue(s.state.value.converging)
        assertEquals(emptyList<Int>(), s.state.value.visible)

        advanceUntilIdle()
        assertFalse(s.state.value.converging)
        assertEquals(listOf(0), s.state.value.visible)
    }

    @Test
    fun 收斂中的狀態文案講的是正在過濾() {
        val state = Step2State(plan = FramePlan("v", 3, List(3) { 0.0 }, false), converging = true)
        assertEquals("正在過濾相似畫面…", state.statusText)
    }

    @Test
    fun 收斂完的狀態文案講候選數與被藏起來的數() {
        val state = Step2State(
            plan = FramePlan("v", 3, List(148) { 0.0 }, false),
            converging = false,
            ready = (0 until 148).toSet(),
            kept = (0 until 50).toList(),
            hiddenCount = 98,
        )
        assertEquals("已收斂成 50 張候選，隱藏了 98 張相似畫面", state.statusText)
    }

    @Test
    fun 沒有任何東西被藏起來時不講隱藏() {
        val state = Step2State(
            plan = FramePlan("v", 3, List(3) { 0.0 }, false),
            converging = false,
            ready = setOf(0, 1, 2),
            kept = listOf(0, 1, 2),
            hiddenCount = 0,
        )
        assertEquals("3 張候選", state.statusText)
    }

    @Test
    fun 解不出storyboard時講得出可以截圖補上() {
        val state = Step2State(plan = FramePlan("v", 3, emptyList(), false), converging = false)
        assertEquals("無法取得逐段縮圖，可以截圖補上", state.statusText)
    }

    @Test
    fun sheet亂序抵達時收斂仍照時間順序() = runTest(dispatcher) {
        // 格 1 的指紋差很多，格 2、3 的指紋跟格 1 很像。
        // 若不先照格號排序，後段先回來時會拿錯的基準比，收斂結果就不對了。
        // 使用 0、127、126、125 —— 0vs127 距離 7>6（保留），126vs127 距離 1<=6（藏），125vs127 距離 2<=6（藏）
        val source = object : FrameSourceReordering(
            plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false),
            hashes = listOf(0L, 127L, 126L, 125L),
        ) {}
        val s = Step2Store(source, emptySet(), this, dispatcher)
        advanceUntilIdle()

        assertEquals(listOf(0, 1), s.state.value.kept)
        assertEquals(2, s.state.value.hiddenCount)
    }

    // ---- 顯示全部／重新過濾 ----

    @Test
    fun 顯示全部之後看得到被藏起來的格子() = runTest(dispatcher) {
        val source = FakeFrameSource(
            plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false),
            perSheet = 4,
            hashes = listOf(0L, 0L, 0L, 0L),
        )
        val s = store(source, scope = this)
        advanceUntilIdle()

        s.setShowAll(true)
        assertEquals(listOf(0, 1, 2, 3), s.state.value.visible)

        s.setShowAll(false)
        assertEquals(listOf(0), s.state.value.visible)
    }

    @Test
    fun 切換顯示全部不會重算收斂() = runTest(dispatcher) {
        val source = FakeFrameSource(
            plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false),
            perSheet = 4,
            hashes = listOf(0L, 0L, 0L, 0L),
        )
        val s = store(source, scope = this)
        advanceUntilIdle()

        s.setShowAll(true)
        // kept 與 hiddenCount 是收斂的結果，跟現在顯示幾張無關
        assertEquals(listOf(0), s.state.value.kept)
        assertEquals(3, s.state.value.hiddenCount)
    }

    // ---- 強度 ----

    @Test
    fun 換強度是對原始全部指紋重算不是在結果上疊加() = runTest(dispatcher) {
        // 0 與 1 相差 4 bit：MEDIUM(≤6) 視為相似，LOW(≤3) 視為不相似
        val source = FakeFrameSource(
            plan = FramePlan("v", 3, List(2) { it * 10.0 }, lowQuality = false),
            perSheet = 2,
            hashes = listOf(0b0000L, 0b1111L),
        )
        val s = store(source, scope = this)
        advanceUntilIdle()
        assertEquals("中：藏起來", listOf(0), s.state.value.kept)

        s.setStrength(FilterStrength.LOW)
        advanceUntilIdle()
        assertEquals("低：兩張都留", listOf(0, 1), s.state.value.kept)

        s.setStrength(FilterStrength.MEDIUM)
        advanceUntilIdle()
        assertEquals("切回中要還原，不是在只剩一張的結果上再算", listOf(0), s.state.value.kept)
    }

    // ---- 勾選 ----

    @Test
    fun 點一下切換勾選() = runTest(dispatcher) {
        val s = store(FakeFrameSource.of(frameCount = 3, perSheet = 3), scope = this)
        advanceUntilIdle()

        assertTrue(s.toggle(1))
        assertEquals(setOf(1), s.state.value.selected)

        assertTrue(s.toggle(1))
        assertEquals(emptySet<Int>(), s.state.value.selected)
    }

    @Test
    fun 已收藏的格子點不動而且回傳false() = runTest(dispatcher) {
        val s = store(FakeFrameSource.of(frameCount = 3, perSheet = 3), taken = setOf(1), scope = this)
        advanceUntilIdle()

        assertFalse("回 false 讓畫面出提示", s.toggle(1))
        assertEquals(emptySet<Int>(), s.state.value.selected)
    }

    @Test
    fun 全部選取不把已收藏的算進去() = runTest(dispatcher) {
        val s = store(FakeFrameSource.of(frameCount = 4, perSheet = 4), taken = setOf(2), scope = this)
        advanceUntilIdle()

        s.selectAll()
        assertEquals(setOf(0, 1, 3), s.state.value.selected)
    }

    @Test
    fun 全部選取再按一次是全不選() = runTest(dispatcher) {
        val s = store(FakeFrameSource.of(frameCount = 3, perSheet = 3), scope = this)
        advanceUntilIdle()

        s.selectAll()
        assertEquals(setOf(0, 1, 2), s.state.value.selected)

        s.selectAll()
        assertEquals(emptySet<Int>(), s.state.value.selected)
    }

    @Test
    fun 全部選取只作用在看得到的格子上() = runTest(dispatcher) {
        val source = FakeFrameSource(
            plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false),
            perSheet = 4,
            hashes = listOf(0L, 0L, 0L, 0L),
        )
        val s = store(source, scope = this)
        advanceUntilIdle()

        // 收斂後只看得到第 0 格
        s.selectAll()
        assertEquals(setOf(0), s.state.value.selected)
    }

    // ---- 只看已選 ----

    @Test
    fun 只看已選會濾掉沒勾的() = runTest(dispatcher) {
        val s = store(FakeFrameSource.of(frameCount = 4, perSheet = 4), scope = this)
        advanceUntilIdle()

        s.toggle(1)
        s.toggle(3)
        s.setOnlySelected(true)
        assertEquals(listOf(1, 3), s.state.value.visible)

        s.setOnlySelected(false)
        assertEquals(listOf(0, 1, 2, 3), s.state.value.visible)
    }

    // ---- 播放中的格子 ----

    @Test
    fun 播放器停在哪一格記得住() = runTest(dispatcher) {
        val s = store(FakeFrameSource.of(frameCount = 3, perSheet = 3), scope = this)
        advanceUntilIdle()

        s.markPlaying(2)
        assertEquals(2, s.state.value.playingFrame)

        s.markPlaying(null)
        assertEquals(null, s.state.value.playingFrame)
    }

    // ---- 降級 ----

    @Test
    fun 降級成封面圖時狀態標記得出來() = runTest(dispatcher) {
        val s = store(
            FakeFrameSource.of(frameCount = 2, perSheet = 1, coverDegraded = true),
            scope = this,
        )
        advanceUntilIdle()
        assertTrue(s.state.value.degradedToCover)
    }

    @Test
    fun 降級成封面圖的格子沒有指紋也照樣留在牆上() = runTest(dispatcher) {
        // 真實作降級時發的是空指紋（拿不到 sheet 就算不出 dHash）——
        // converge 從來沒看過這些格號，不補回來的話它們會在收斂完成的那一刻消失
        val s = store(
            FakeFrameSource.of(frameCount = 4, perSheet = 2, coverDegraded = true),
            scope = this,
        )
        advanceUntilIdle()

        assertFalse(s.state.value.converging)
        assertEquals("降級的格子一律保留", listOf(0, 1, 2, 3), s.state.value.kept)
        assertEquals("牆上照樣有東西可看（規格第五節）", listOf(0, 1, 2, 3), s.state.value.visible)
        assertEquals(0, s.state.value.hiddenCount)
        assertEquals("4 張候選", s.state.value.statusText)
    }

    @Test
    fun 有指紋的照收斂沒指紋的照留() = runTest(dispatcher) {
        // 前半段拿得到 sheet（0、1 指紋相同 → 藏掉 1），後半段 403 降級（2、3 沒有指紋 → 都留）
        val source = object : FrameSource {
            override val plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false)
            override fun load() = flow {
                emit(SheetReady(listOf(0, 1), listOf(Fingerprint(0, 0L), Fingerprint(1, 0L))))
                emit(SheetReady(listOf(2, 3), emptyList(), degradedToCover = true))
            }
            override suspend fun bitmapOf(frameIndex: Int) = null
            override fun close() = Unit
        }
        val s = Step2Store(source, emptySet(), this, dispatcher)
        advanceUntilIdle()

        assertEquals(listOf(0, 2, 3), s.state.value.kept)
        assertEquals("只有第 1 格被藏起來", 1, s.state.value.hiddenCount)
    }

    // ---- 取消與失敗 ----

    /** 一批一批慢慢發的來源，用來驗「close 之後還會不會繼續發」。 */
    private class SlowFrameSource(frameCount: Int) : FrameSource {
        override val plan = FramePlan("v", 3, List(frameCount) { it * 10.0 }, lowQuality = false)
        var cancelled = false
            private set
        override fun load() = flow {
            try {
                for (i in 0 until plan.frameCount) {
                    emit(SheetReady(listOf(i), listOf(Fingerprint(i, FakeFrameSource.spreadHash(i)))))
                    delay(100)
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        override suspend fun bitmapOf(frameIndex: Int) = null
        override fun close() = Unit
    }

    /**
     * 「回第一步 → 貼另一支網址」時舊 store 會被 close。load 是 launch 進**共用的 viewModelScope**，
     * close 不取消它的話前一支影片整套 sheet 下載會繼續跑，跟新的那一套搶頻寬，
     * 還會繼續寫進舊影片的 drafts/。
     */
    @Test
    fun close_會取消還在跑的下載() = runTest(dispatcher) {
        val source = SlowFrameSource(frameCount = 6)
        val s = Step2Store(source, emptySet(), this, dispatcher)
        advanceTimeBy(150)
        val readyBeforeClose = s.state.value.ready
        assertTrue("測試前提：這時候還沒載完", readyBeforeClose.size < 6)

        s.close()
        advanceUntilIdle()

        assertEquals("close 之後不該再有新的批次進來", readyBeforeClose, s.state.value.ready)
        assertTrue("下載真的被取消了，不是自己跑完的", source.cancelled)
    }

    /** 中途丟例外的來源 —— 磁碟滿、草稿目錄不可寫都長這樣。 */
    private class FailingFrameSource(private val failure: Throwable) : FrameSource {
        override val plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false)
        override fun load() = flow<SheetReady> {
            emit(SheetReady(listOf(0, 1), listOf(Fingerprint(0, 0L), Fingerprint(1, 255L))))
            throw failure
        }
        override suspend fun bitmapOf(frameIndex: Int) = null
        override fun close() = Unit
    }

    /**
     * 功能降級，絕不當機（規格第三節設計原則第 6 條）。
     * 不接的話例外會從 `viewModelScope.launch` 傳出去、把 process 殺掉；
     * 就算沒當機，`converging` 也會永遠停在 true。
     */
    @Test
    fun 載入中途丟例外時停在已經拿到的那幾張而不是當機() = runTest(dispatcher) {
        val s = Step2Store(FailingFrameSource(java.io.IOException("磁碟滿了")), emptySet(), this, dispatcher)
        advanceUntilIdle()

        assertFalse("卡在 true 的話狀態列永遠是「正在過濾相似畫面…」", s.state.value.converging)
        assertTrue(s.state.value.loadFailed)
        assertEquals(setOf(0, 1), s.state.value.ready)
        assertEquals(listOf(0, 1), s.state.value.visible)
        assertEquals("只載入了 2 張縮圖就失敗了，其餘可以截圖補上", s.state.value.statusText)
    }

    @Test
    fun 解圖OOM也只是降級不是當機() = runTest(dispatcher) {
        // OOM 不是 Exception，要另外接
        val s = Step2Store(FailingFrameSource(OutOfMemoryError("sheet 太大")), emptySet(), this, dispatcher)
        advanceUntilIdle()

        assertFalse(s.state.value.converging)
        assertTrue(s.state.value.loadFailed)
    }

    @Test
    fun 一張都沒載到就失敗時狀態列講得出可以截圖補上() = runTest(dispatcher) {
        val source = object : FrameSource {
            override val plan = FramePlan("v", 3, List(4) { it * 10.0 }, lowQuality = false)
            override fun load() = flow<SheetReady> { throw java.io.IOException("草稿目錄不可寫") }
            override suspend fun bitmapOf(frameIndex: Int) = null
            override fun close() = Unit
        }
        val s = Step2Store(source, emptySet(), this, dispatcher)
        advanceUntilIdle()

        assertEquals("縮圖載入失敗，可以截圖補上", s.state.value.statusText)
    }
}
