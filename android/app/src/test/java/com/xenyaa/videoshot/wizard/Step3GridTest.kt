package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.details.ShotDetails
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 跟 Step3DockTest 同一個理由：預設 320×470dp 視窗比任何支援的裝置都小，
// 會讓縮圖牆在這個不存在的小螢幕上被 dock 擠壓到量不出高度。
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Step3GridTest {

    @get:Rule val compose = createComposeRule()

    private fun stateOf(
        count: Int = 4,
        selected: Set<Int> = setOf(0, 1, 2, 3),
        applied: Set<Int> = emptySet(),
    ) = Step3State(
        cells = List(count) { Step3Cell(it, it * 70.0, manual = false) },
        details = List(count) { it }.associateWith {
            ShotDetails(eventDate = "2026-01-01", applied = it in applied)
        },
        selected = selected,
        cropping = false,
    )

    private fun show(
        state: Step3State,
        onToggle: (Int) -> Unit = {},
        onSelectAll: () -> Unit = {},
        onSelectNone: () -> Unit = {},
        onInvert: () -> Unit = {},
        onSelectUnapplied: () -> Unit = {},
    ) {
        compose.setContent {
            Step3DetailsScreen(
                state = state,
                bitmapFor = { null },
                onToggle = onToggle,
                onSelectAll = onSelectAll,
                onSelectNone = onSelectNone,
                onInvert = onInvert,
                onSelectUnapplied = onSelectUnapplied,
                onEditEventDate = {},
                onEditPlace = {},
                onEditDescription = {},
                onEditTags = {},
                onApply = {},
                onFinish = {},
            )
        }
    }

    @Test
    fun 標題數得出總張數與已完成() {
        show(stateOf(applied = setOf(0, 1)))
        compose.onNodeWithText("4 張 · 2 已完成").assertIsDisplayed()
    }

    @Test
    fun 每一格都畫出來了() {
        show(stateOf())
        compose.onAllNodesWithContentDescription("第三步的格子", substring = true).assertCountEquals(4)
    }

    @Test
    fun 套用過的格子有綠點() {
        show(stateOf(applied = setOf(0, 2)))
        compose.onAllNodesWithContentDescription("已套用", substring = true).assertCountEquals(2)
    }

    @Test
    fun 勾選中的格子講得出自己被勾選() {
        show(stateOf(selected = setOf(1)))
        compose.onAllNodesWithContentDescription("已勾選", substring = true).assertCountEquals(1)
    }

    @Test
    fun 點格子會回報切換() {
        var toggled: Int? = null
        show(stateOf(), onToggle = { toggled = it })
        compose.onNodeWithText("00:00").performClick()
        assertEquals(0, toggled)
    }

    @Test
    fun 快捷列四顆都在() {
        show(stateOf())
        compose.onNodeWithText("全選").assertIsDisplayed()
        compose.onNodeWithText("全不選").assertIsDisplayed()
        compose.onNodeWithText("反選").assertIsDisplayed()
        compose.onNodeWithText("未填的").assertIsDisplayed()
    }

    @Test
    fun 快捷列四顆各自回報() {
        var log = mutableListOf<String>()
        show(
            stateOf(),
            onSelectAll = { log += "all" },
            onSelectNone = { log += "none" },
            onInvert = { log += "invert" },
            onSelectUnapplied = { log += "unapplied" },
        )
        compose.onNodeWithText("全選").performClick()
        compose.onNodeWithText("全不選").performClick()
        compose.onNodeWithText("反選").performClick()
        compose.onNodeWithText("未填的").performClick()
        assertEquals(listOf("all", "none", "invert", "unapplied"), log)
    }

    @Test
    fun 裁圖中會顯示進度() {
        show(stateOf().copy(cropping = true, cropDone = 2, cropTotal = 4))
        compose.onNodeWithText("處理縮圖 2/4").assertIsDisplayed()
    }
}
