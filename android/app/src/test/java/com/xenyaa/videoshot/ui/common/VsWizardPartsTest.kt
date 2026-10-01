package com.xenyaa.videoshot.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun 底線分頁標出選中並回報() {
        var picked = -1
        compose.setContent { VideoshotTheme { VsUnderlineTabs(listOf("標籤與地點", "描述"), selected = 0, onSelect = { picked = it }) } }
        compose.onNodeWithText("標籤與地點").assertIsSelected()
        compose.onNodeWithText("描述").performClick()
        assertEquals(1, picked)
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
