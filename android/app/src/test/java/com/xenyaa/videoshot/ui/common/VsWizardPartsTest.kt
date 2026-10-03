package com.xenyaa.videoshot.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class VsWizardPartsTest {
    @get:Rule val compose = createComposeRule()
    private val steps = listOf("貼網址", "挑畫面", "填資料")

    /** 手冊 §四：進度指示有文字「1. 貼網址 › 2. 挑畫面 › 3. 填資料」。 */
    @Test fun 步驟條有編號文字且標出目前步驟() {
        compose.setContent { VideoshotTheme { VsStepIndicator(steps, current = 1) } }
        compose.onNodeWithText("2. 挑畫面", substring = true).assertIsSelected()
        compose.onNodeWithText("3. 填資料", substring = true).assertIsNotSelected()
    }

    @Test fun 已完成的步驟可以點回去() {
        var went = -1
        compose.setContent { VideoshotTheme { VsStepIndicator(steps, current = 2, onStepClick = { went = it }) } }
        compose.onNodeWithText("1. 貼網址", substring = true).performClick()
        assertEquals(0, went)
    }

    @Test fun 可點的步驟觸控區至少44() {
        compose.setContent { VideoshotTheme { VsStepIndicator(steps, current = 2, onStepClick = {}) } }
        compose.onNodeWithText("1. 貼網址", substring = true).assertHeightIsAtLeast(44.dp)
    }

    /** 沿用既有精靈行為：已到達過的後面步驟也能點（WizardViewModel.jumpTo + furthest）。 */
    @Test fun 已到達的後面步驟可以點() {
        var went = -1
        compose.setContent { VideoshotTheme { VsStepIndicator(steps, current = 0, furthest = 2, onStepClick = { went = it }) } }
        compose.onNodeWithText("3. 填資料", substring = true).performClick()
        assertEquals(2, went)
    }

    @Test fun 沒到達的步驟不能點() {
        var went = -1
        compose.setContent { VideoshotTheme { VsStepIndicator(steps, current = 0, furthest = 1, onStepClick = { went = it }) } }
        compose.onNodeWithText("3. 填資料", substring = true).assertHasNoClickAction()
    }

    /** 15A 最終審查：「‹」不該被 TalkBack 唸出來。 */
    @Test fun 返回箭頭不進語意() {
        compose.setContent { VideoshotTheme { VsStepIndicator(steps, current = 2, onStepClick = {}) } }
        compose.onNodeWithContentDescription("回到 1. 貼網址").assertExists()
        compose.onAllNodesWithText("‹", substring = true).assertCountEquals(0)
    }

    /** 往後跳的步驟 TalkBack 唸「前往 …」。 */
    @Test fun 往後跳的步驟唸前往() {
        compose.setContent { VideoshotTheme { VsStepIndicator(steps, current = 0, furthest = 2, onStepClick = {}) } }
        compose.onNodeWithContentDescription("前往 3. 填資料").assertExists()
    }

    /** 15A 最終審查：切換型小按鈕的開關狀態要進語意。 */
    @Test fun 工具列小按鈕的選取狀態進語意() {
        compose.setContent { VideoshotTheme { VsToolbarPill("只看已選", {}, selected = true, isToggle = true) } }
        compose.onNodeWithText("只看已選").assertIsSelected()
    }

    /** 一般動作鈕不帶 Selected，免得被唸成「未選取」。 */
    @Test fun 一般動作鈕不帶選取語意() {
        compose.setContent { VideoshotTheme { VsToolbarPill("全選", {}) } }
        compose.onNodeWithText("全選").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test fun 底線分頁標出選中並回報() {
        var picked = -1
        compose.setContent { VideoshotTheme { VsUnderlineTabs(listOf("標籤與地點", "描述"), selected = 0, onSelect = { picked = it }) } }
        compose.onNodeWithText("標籤與地點").assertIsSelected()
        compose.onNodeWithText("描述").performClick()
        assertEquals(1, picked)
    }

    /** 計畫 15D Task 1：兩個字的分頁（約 34dp 寬）觸控區也要有 44dp。 */
    @Test fun 兩個字的分頁觸控區至少44寬() {
        compose.setContent { VideoshotTheme { VsUnderlineTabs(listOf("標籤與地點", "描述"), 0, {}) } }
        compose.onNodeWithText("描述").assertWidthIsAtLeast(44.dp)
    }

    @Test fun 工具列小按鈕可點且觸控區至少44() {
        var n = 0
        compose.setContent {
            VideoshotTheme { VsToolbarPill("全選", { n++ }, Modifier.testTag("p"), style = PillStyle.Quiet) }
        }
        compose.onNodeWithTag("p").assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("全選").performClick()
        assertEquals(1, n)
    }

    @Test fun 提示卡可以關閉() {
        var closed = false
        compose.setContent { VideoshotTheme { VsHintCard("點一下就收藏", onDismiss = { closed = true }) } }
        compose.onNodeWithContentDescription("關閉提示").performClick()
        assertTrue(closed)
    }

    @Test fun 動作列顯示狀態文字與按鈕() {
        compose.setContent {
            VideoshotTheme {
                VsActionDock(status = { Text("118 張候選 · 已選 4") }) { VsButton("下一步（4 張）", {}) }
            }
        }
        compose.onNodeWithText("118 張候選 · 已選 4").assertIsDisplayed()
        compose.onNodeWithText("下一步（4 張）").assertIsDisplayed()
    }
}
