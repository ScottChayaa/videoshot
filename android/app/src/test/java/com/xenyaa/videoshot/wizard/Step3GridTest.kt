package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.xenyaa.videoshot.core.details.ShotDetails
import org.junit.Assert.assertEquals
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertTrue
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
        bitmapFor: suspend (Int) -> androidx.compose.ui.graphics.ImageBitmap? = { null },
    ) {
        compose.setContent {
            Step3DetailsScreen(
                state = state,
                bitmapFor = bitmapFor,
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

    /** 縮圖左下四個欄位圖示那一排不能比格子寬（16dp 圖示＋間距）。w360dp 的情況見 [Step3GridNarrowTest]。 */
    @Test
    fun 欄位圖示那排不比格子寬() {
        show(stateOf())
        assertFieldIconRowsFit(compose)
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
    /** 跟第二步、照片頁一致：載入中畫骨架塊（拿不到圖只留底色，見下一則）。 */
    @Test
    fun 載入中的格子畫骨架() {
        show(stateOf(), bitmapFor = { awaitCancellation() })
        compose.onAllNodesWithTag(FRAME_SKELETON_TAG, useUnmergedTree = true).assertCountEquals(4)
    }

    @Test
    fun 拿不到圖的格子不畫骨架() {
        show(stateOf())
        compose.onAllNodesWithTag(FRAME_SKELETON_TAG, useUnmergedTree = true).assertCountEquals(0)
    }
}

/**
 * 每格約 98dp 寬（393dp 手機），圖示列 4×16＋3×3＋8＝81dp 放得下。
 * 圖示列加了 testTag `step3-field-icons`；格子本身是可點的合併節點，所以要看未合併的樹才找得到。
 */
internal fun assertFieldIconRowsFit(compose: ComposeContentTestRule) {
    val rows = compose.onAllNodesWithTag("step3-field-icons", useUnmergedTree = true)
    rows.assertCountEquals(4)
    val cells = compose.onAllNodesWithContentDescription("第三步的格子", substring = true)
    for (i in 0 until 4) {
        val row = rows[i].fetchSemanticsNode().boundsInRoot
        val cell = cells[i].fetchSemanticsNode().boundsInRoot
        assertTrue("第 $i 格圖示列寬 ${row.width} 超過格寬 ${cell.width}", row.width <= cell.width)
    }
}

/**
 * 同一項檢查換成 w360dp（常見的最窄手機）。拆成另一個類別是因為 Robolectric 的 qualifiers 以類別為單位設定，
 * 這樣不必在測試中途動態改視窗。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class Step3GridNarrowTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 窄螢幕下欄位圖示那排也不比格子寬() {
        compose.setContent {
            Step3DetailsScreen(
                state = Step3State(
                    cells = List(4) { Step3Cell(it, it * 70.0, manual = false) },
                    details = List(4) { it }.associateWith { ShotDetails(eventDate = "2026-01-01") },
                    selected = setOf(0, 1, 2, 3),
                    cropping = false,
                ),
                bitmapFor = { null },
                onToggle = {},
                onSelectAll = {},
                onApply = {},
                onFinish = {},
            )
        }
        assertFieldIconRowsFit(compose)
    }
}
