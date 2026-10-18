package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
// 跟 Step3FieldBarTest 同一個理由：預設 320×470dp 視窗比任何支援的裝置都小，
// 會讓縮圖牆在這個不存在的小螢幕上被底部列擠壓到量不出高度。
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Step3GridTest {

    @get:Rule val compose = createComposeRule()

    private fun stateOf(
        count: Int = 4,
        selected: Set<Int> = setOf(0, 1, 2, 3),
    ) = Step3State(
        cells = List(count) { Step3Cell(it, it * 70.0, manual = false) },
        details = List(count) { it }.associateWith { ShotDetails(eventDate = "2026-01-01") },
        selected = selected,
        cropping = false,
    )

    private fun show(
        state: Step3State,
        onToggle: (Int) -> Unit = {},
    ) {
        compose.setContent {
            Step3DetailsScreen(
                state = state,
                bitmapFor = { null },
                onToggle = onToggle,
                onSelectAll = {},
                onApply = {},
                onFinish = {},
            )
        }
    }

    @Test
    fun 每一格都畫出來了() {
        show(stateOf())
        compose.onAllNodesWithContentDescription("第三步的格子", substring = true).assertCountEquals(4)
    }

    @Test
    fun 勾選中的格子講得出自己被勾選() {
        show(stateOf(selected = setOf(1)))
        compose.onAllNodesWithContentDescription("已勾選", substring = true).assertCountEquals(1)
    }

    /** 跟第二步一樣：正方形、每列 4 張、貼齊螢幕。 */
    @Test
    fun 格子是正方形_每列四張貼齊螢幕() {
        show(stateOf())
        val first = compose.onAllNodesWithContentDescription("第三步的格子", substring = true)[0].fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, first.height, 1f)
        assertEquals(0f, first.left, 1f)
        val fourth = compose.onAllNodesWithContentDescription("第三步的格子", substring = true)[3].fetchSemanticsNode().boundsInRoot
        assertEquals(root.right, fourth.right, 1f)
        assertEquals(first.top, fourth.top, 1f)
    }

    @Test
    fun 格子沒有時間字樣也沒有已套用記號() {
        show(stateOf())
        compose.onAllNodesWithText("00:00").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("已套用", substring = true).assertCountEquals(0)
    }

    @Test
    fun 格子唸得出哪些欄位已設定哪些沒有() {
        show(Step3State(
            cells = listOf(Step3Cell(0, 70.0, false)),
            details = mapOf(0 to ShotDetails("2026-01-01", place = "冬山河")),
            selected = emptySet(),
            cropping = false,
        ))
        compose.onNodeWithContentDescription("第三步的格子 01:10・已設定：時間、地點・未設定：標籤、描述").assertExists()
    }

    @Test
    fun 點格子會回報切換() {
        var toggled: Int? = null
        show(stateOf(), onToggle = { toggled = it })
        compose.onNodeWithContentDescription("第三步的格子 00:00", substring = true).performClick()
        assertEquals(0, toggled)
    }

    @Test
    fun 長按格子浮出這張的圖資_不改勾選() {
        var toggled = 0
        show(
            Step3State(
                cells = listOf(Step3Cell(0, 70.0, false)),
                details = mapOf(0 to ShotDetails("2025-07-12", place = "冬山河", tags = listOf("玩水", "阿明"))),
                selected = emptySet(),
                cropping = false,
            ),
            onToggle = { toggled++ },
        )
        compose.onNodeWithContentDescription("第三步的格子 01:10", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("2025年7月12日").assertIsDisplayed()
        compose.onNodeWithText("冬山河").assertIsDisplayed()
        compose.onNodeWithText("玩水、阿明").assertIsDisplayed()
        compose.onNodeWithText("未設定").assertIsDisplayed()
        compose.onNodeWithText("影片 01:10").assertIsDisplayed()
        assertEquals(0, toggled)
    }

    @Test
    fun 點浮窗本身也會關掉() {
        show(stateOf())
        compose.onNodeWithContentDescription("第三步的格子 00:00", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("影片 00:00").performClick()
        compose.onAllNodesWithText("影片 00:00").assertCountEquals(0)
    }

    @Test
    fun 裁圖中會顯示進度() {
        show(stateOf().copy(cropping = true, cropDone = 2, cropTotal = 4))
        compose.onNodeWithText("處理縮圖 2/4").assertIsDisplayed()
    }
}
