package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import com.xenyaa.videoshot.wizard.frames.FramePlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Step2InteractionTest {

    @get:Rule val compose = createComposeRule()

    private val source = FakeFrameSource.of(frameCount = 3, intervalSec = 10.0)
    private val haptics = FakeHaptics()

    private val toggled = mutableListOf<Int>()
    private val played = mutableListOf<Int>()
    private val takenTaps = mutableListOf<Int>()
    private var hintDismissed = 0

    private fun show(
        selected: Set<Int> = emptySet(),
        taken: Set<Int> = emptySet(),
        playingFrame: Int? = null,
        hintSeen: Boolean = true,
    ) {
        val state = Step2State(
            plan = FramePlan("v", 3, List(3) { it * 10.0 }, false),
            ready = setOf(0, 1, 2),
            converging = false,
            kept = listOf(0, 1, 2),
            selected = selected,
            taken = taken,
            playingFrame = playingFrame,
            hintSeen = hintSeen,
        )
        compose.setContent {
            Step2GridScreen(
                state = state,
                bitmapFor = { source.bitmapOf(it) },
                haptics = haptics,
                onToggle = { toggled += it },
                onPlayFrame = { played += it },
                onTakenTap = { takenTaps += it },
                onSelectAll = {},
                onShowAll = {},
                onOnlySelected = {},
                onDismissHint = { hintDismissed++ },
                onNext = {},
            )
        }
    }

    @Test
    fun 點一格會回報勾選() {
        show()
        compose.onNodeWithContentDescription("第 2 格 00:10").performClick()
        assertEquals(listOf(1), toggled)
    }

    @Test
    fun 長按一格會跳播而不是勾選() {
        show()
        compose.onNodeWithContentDescription("第 2 格 00:10").performTouchInput { longClick() }
        assertEquals(listOf(1), played)
        assertEquals("長按不該順便勾選", emptyList<Int>(), toggled)
    }

    @Test
    fun 長按有震動回饋() {
        show()
        compose.onNodeWithContentDescription("第 1 格 00:00").performTouchInput { longClick() }
        assertEquals(1, haptics.ticks)
    }

    @Test
    fun 點右上播放鈕等同長按() {
        show()
        compose.onAllNodesWithContentDescription("跳到這一段")[2].performClick()
        assertEquals(listOf(2), played)
        assertEquals("▶ 不該順便勾選", emptyList<Int>(), toggled)
    }

    @Test
    fun 播放鈕是無障礙到得了的入口() {
        show()
        // 每一格都要有，長按不是唯一入口（手冊 §四第二步）
        assertEquals(3, compose.onAllNodesWithContentDescription("跳到這一段").fetchSemanticsNodes().size)
    }

    @Test
    fun 點已收藏的格子會回報而不是靜靜沒反應() {
        show(taken = setOf(0))
        compose.onNodeWithContentDescription("第 1 格 00:00").performClick()
        assertEquals(listOf(0), takenTaps)
        assertEquals("已收藏的不進勾選", emptyList<Int>(), toggled)
    }

    @Test
    fun 已收藏的格子仍然長按得動() {
        show(taken = setOf(0))
        compose.onNodeWithContentDescription("第 1 格 00:00").performTouchInput { longClick() }
        assertEquals(listOf(0), played)
    }

    @Test
    fun 一次性提示沒看過時會出現() {
        show(hintSeen = false)
        compose.onNodeWithText("點一下收藏・長按看看那一段").assertIsDisplayed()
    }

    @Test
    fun 一次性提示看過就不出現() {
        show(hintSeen = true)
        compose.onNodeWithText("點一下收藏・長按看看那一段").assertDoesNotExist()
    }

    @Test
    fun 點任意處一次性提示會消失() {
        show(hintSeen = false)
        compose.onNodeWithContentDescription("第 1 格 00:00").performClick()
        assertEquals(1, hintDismissed)
    }

    @Test
    fun 假震動器數得出次數() {
        val h = FakeHaptics()
        h.tick()
        h.tick()
        assertEquals(2, h.ticks)
    }
}
