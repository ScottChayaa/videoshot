package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import com.xenyaa.videoshot.wizard.frames.FramePlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import androidx.compose.ui.test.assertCountEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Step2GridScreenTest {

    @get:Rule val compose = createComposeRule()

    private val source = FakeFrameSource.of(frameCount = 4, intervalSec = 10.0)

    private fun show(
        state: Step2State,
        onToggle: (Int) -> Unit = {},
        onSelectAll: () -> Unit = {},
        onShowAll: (Boolean) -> Unit = {},
        onOnlySelected: (Boolean) -> Unit = {},
        onNext: () -> Unit = {},
        onTakeShot: () -> Unit = {},
        bitmapFor: suspend (Int) -> androidx.compose.ui.graphics.ImageBitmap? = { source.bitmapOf(it) },
    ) {
        compose.setContent {
            Step2GridScreen(
                state = state,
                bitmapFor = bitmapFor,
                haptics = FakeHaptics(),
                onToggle = onToggle,
                onTakenTap = {},
                onPlayFrame = {},
                onSelectAll = onSelectAll,
                onTakeShot = onTakeShot,
                onShowAll = onShowAll,
                onOnlySelected = onOnlySelected,
                onDismissHint = {},
                onNext = onNext,
            )
        }
    }

    private fun loaded(
        frameCount: Int = 4,
        kept: List<Int> = (0 until frameCount).toList(),
        hiddenCount: Int = 0,
        selected: Set<Int> = emptySet(),
        taken: Set<Int> = emptySet(),
        showAll: Boolean = false,
        lowQuality: Boolean = false,
    ) = Step2State(
        plan = FramePlan("v", 3, List(frameCount) { it * 10.0 }, lowQuality),
        ready = (0 until frameCount).toSet(),
        converging = false,
        kept = kept,
        hiddenCount = hiddenCount,
        selected = selected,
        taken = taken,
        showAll = showAll,
        hintSeen = true,
    )

    @Test
    fun 狀態列顯示收斂結果() {
        show(loaded(frameCount = 4, kept = listOf(0, 1), hiddenCount = 2))
        compose.onNodeWithText("已收斂成 2 張候選，隱藏了 2 張相似畫面").assertIsDisplayed()
    }

    @Test
    fun 有東西被藏起來時才出現顯示全部() {
        show(loaded(frameCount = 4, kept = listOf(0, 1), hiddenCount = 2))
        compose.onNodeWithText("顯示全部").assertIsDisplayed()
    }

    @Test
    fun 顯示全部之後按鈕變成重新過濾() {
        show(loaded(frameCount = 4, kept = listOf(0, 1), hiddenCount = 2, showAll = true))
        compose.onNodeWithText("重新過濾").assertIsDisplayed()
    }

    @Test
    fun 按顯示全部會回報true() {
        var got: Boolean? = null
        show(loaded(frameCount = 4, kept = listOf(0, 1), hiddenCount = 2), onShowAll = { got = it })
        compose.onNodeWithText("顯示全部").performClick()
        assertEquals(true, got)
    }

    @Test
    fun 一張都沒藏就不出現顯示全部() {
        show(loaded(frameCount = 4, hiddenCount = 0))
        compose.onNodeWithText("顯示全部").assertDoesNotExist()
    }

    @Test
    fun 工具列三顆按鈕依規格的順序排列() {
        // 規格第五節的線框：全部選取│截圖│只看已選。
        // 階段 4b 時這條測試斷言「沒有截圖」—— 那是當時的範圍，階段 4c 把它補上了
        show(loaded())
        compose.onNodeWithText("全部選取").assertIsDisplayed()
        compose.onNodeWithText("截圖").assertIsDisplayed()
        compose.onNodeWithText("只看已選").assertIsDisplayed()
    }

    @Test
    fun 按截圖會回報() {
        var called = 0
        show(loaded(), onTakeShot = { called++ })
        compose.onNodeWithText("截圖").performClick()
        assertEquals(1, called)
    }

    @Test
    fun 按全部選取會回報() {
        var called = 0
        show(loaded(), onSelectAll = { called++ })
        compose.onNodeWithText("全部選取").performClick()
        assertEquals(1, called)
    }

    @Test
    fun 底部一列顯示候選數與已選數() {
        show(loaded(frameCount = 4, kept = listOf(0, 1, 2), selected = setOf(0, 2)))
        compose.onNodeWithText("3 張候選 · 已選 2").assertIsDisplayed()
    }

    @Test
    fun 下一步按鈕帶張數() {
        show(loaded(frameCount = 4, selected = setOf(0, 2)))
        compose.onNodeWithText("下一步（2 張）").assertIsDisplayed()
    }

    @Test
    fun 一張都沒選時下一步是停用的() {
        show(loaded(selected = emptySet()))
        compose.onNodeWithText("下一步（0 張）").assertIsNotEnabled()
    }

    @Test
    fun 每一格都有時間標籤() {
        show(loaded(frameCount = 3))
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("00:10").assertIsDisplayed()
        compose.onNodeWithText("00:20").assertIsDisplayed()
    }

    @Test
    fun 每一格右上角都有播放鈕() {
        show(loaded(frameCount = 3))
        assertEquals(3, compose.onAllNodesWithContentDescription("跳到這一段").fetchSemanticsNodes().size)
    }

    @Test
    fun 降到較低畫質時會提示() {
        show(loaded(lowQuality = true))
        compose.onNodeWithText("這支影片只有較低畫質的縮圖").assertIsDisplayed()
    }

    @Test
    fun 沒降級就不提示畫質() {
        show(loaded(lowQuality = false))
        compose.onNodeWithText("這支影片只有較低畫質的縮圖").assertDoesNotExist()
    }

    @Test
    fun 解不出storyboard時是空牆但有說明而且不當機() {
        show(
            Step2State(
                plan = FramePlan("v", 3, emptyList(), false),
                converging = false,
                hintSeen = true,
            )
        )
        compose.onNodeWithText("無法取得逐段縮圖，可以截圖補上").assertIsDisplayed()
        compose.onNodeWithText("下一步（0 張）").assertIsDisplayed()
    }

    @Test
    fun 收斂中就先看得到格子() {
        show(
            Step2State(
                plan = FramePlan("v", 3, List(3) { it * 10.0 }, false),
                ready = setOf(0, 1, 2),
                converging = true,
                hintSeen = true,
            )
        )
        compose.onNodeWithText("正在過濾相似畫面…").assertIsDisplayed()
        compose.onNodeWithText("00:20").assertIsDisplayed()
    }

    @Test
    fun formatClock_把秒數變成分秒() {
        assertEquals("00:00", formatClock(0.0))
        assertEquals("00:09", formatClock(9.4))
        assertEquals("01:05", formatClock(65.0))
        assertEquals("61:01", formatClock(3661.0))
    }

    // ---- 手動補圖（階段 4c）----

    @Test
    fun 手動格帶截圖標記() {
        val base = Step2State(plan = source.plan, converging = false)
        show(
            base.copy(
                manual = listOf(ManualCell(base.plan.frameCount, 15.0, java.io.File("/tmp/a.webp"))),
                ready = base.ready + base.plan.frameCount,
                selected = base.selected + base.plan.frameCount,
                kept = emptyList(),
            ),
        )
        compose.onAllNodesWithContentDescription("截圖").assertCountEquals(1)
    }

    @Test
    fun storyboard格沒有截圖標記() {
        val base = Step2State(plan = source.plan, converging = false)
        show(base.copy(ready = (0 until base.plan.frameCount).toSet(), kept = (0 until base.plan.frameCount).toList()))
        compose.onAllNodesWithContentDescription("截圖").assertCountEquals(0)
    }

    @Test
    fun 手動格的圖也向bitmapFor要() {
        val asked = mutableListOf<Int>()
        val base = Step2State(plan = source.plan, converging = false)
        val cell = base.plan.frameCount
        show(
            base.copy(
                manual = listOf(ManualCell(cell, 5.0, java.io.File("/tmp/a.webp"))),
                ready = base.ready + cell,
                kept = emptyList(),
            ),
            bitmapFor = { asked += it; null },
        )
        compose.waitForIdle()
        assertTrue(asked.toString(), asked.contains(cell))
    }
}
