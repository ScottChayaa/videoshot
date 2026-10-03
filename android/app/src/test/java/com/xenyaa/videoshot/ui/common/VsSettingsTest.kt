package com.xenyaa.videoshot.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
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

/** 計畫 15D Task 1：設定頁共用元件（原型 `.set-group`／`.set-row.pick`／`.stepper`／`.set-note`／查詢頁時間欄位）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class VsSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 設定群組顯示標題與子列() {
        compose.setContent {
            VideoshotTheme {
                VsSettingGroup(title = "過濾相似強度") {
                    VsListRow("低")
                    VsSettingDivider()
                    VsListRow("高")
                }
            }
        }
        compose.onNodeWithText("過濾相似強度").assertIsDisplayed()
        compose.onNodeWithText("低").assertIsDisplayed()
        compose.onNodeWithText("高").assertIsDisplayed()
    }

    @Test fun 設定說明文字顯示() {
        compose.setContent { VideoshotTheme { VsSettingNote("數字越大，留下的相似畫面越少。") } }
        compose.onNodeWithText("數字越大，留下的相似畫面越少。").assertIsDisplayed()
    }

    @Test fun 單選列標出選中且回報() {
        var picked = false
        compose.setContent {
            VideoshotTheme { VsRadioRow("中", selected = true, onSelect = { picked = true }, subtitle = "每 3 張留 1") }
        }
        compose.onNodeWithText("中", substring = true).assertIsSelected().performClick()
        assertTrue(picked)
    }

    @Test fun 單選列未選中時不是選取狀態且觸控區夠大() {
        compose.setContent { VideoshotTheme { VsRadioRow("低", selected = false, onSelect = {}, modifier = Modifier.testTag("r")) } }
        compose.onNodeWithTag("r").assertIsNotSelected().assertHeightIsAtLeast(44.dp).assertHasClickAction()
    }

    @Test fun 步進器到上限時加號停用() {
        compose.setContent { VideoshotTheme { VsStepper(30, {}, 0..30, label = "往後秒數") } }
        compose.onNodeWithContentDescription("增加 往後秒數").assertIsNotEnabled()
        compose.onNodeWithContentDescription("減少 往後秒數").assertIsEnabled()
    }

    @Test fun 步進器到下限時減號停用() {
        compose.setContent { VideoshotTheme { VsStepper(0, {}, 0..30, label = "往前秒數") } }
        compose.onNodeWithContentDescription("減少 往前秒數").assertIsNotEnabled()
        compose.onNodeWithContentDescription("增加 往前秒數").assertIsEnabled()
    }

    @Test fun 步進器按加減回報新值() {
        var v = 10
        compose.setContent { VideoshotTheme { VsStepper(v, { v = it }, 0..30, label = "往後秒數") } }
        compose.onNodeWithContentDescription("增加 往後秒數").performClick()
        assertEquals(11, v)
        compose.onNodeWithContentDescription("減少 往後秒數").performClick()
        assertEquals(9, v)
    }

    /** 數值節點唸「{label} {value}」，不要單獨唸一個數字。 */
    @Test fun 步進器數值唸出標籤() {
        compose.setContent { VideoshotTheme { VsStepper(12, {}, 0..30, Modifier.testTag("s"), label = "往後秒數") } }
        compose.onNodeWithContentDescription("往後秒數 12").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
    }

    /** 按＋／−後焦點仍在按鈕上，數值節點要是 liveRegion，TalkBack 才會唸出新值（最終審查 M2）。 */
    @Test fun 步進器數值是liveRegion() {
        compose.setContent { VideoshotTheme { VsStepper(12, {}, 0..30, label = "往後秒數") } }
        compose.onNodeWithContentDescription("往後秒數 12")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun 下拉欄位唸得出標籤與值() {
        compose.setContent { VideoshotTheme { VsSelectField("時間", "全部日期", {}) } }
        compose.onNodeWithContentDescription("時間：全部日期").assertHasClickAction()
    }

    @Test fun 下拉欄位點了會回報且是按鈕角色() {
        var clicked = false
        compose.setContent { VideoshotTheme { VsSelectField("時間", "全部日期", { clicked = true }) } }
        compose.onNodeWithContentDescription("時間：全部日期")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
            .performClick()
        assertTrue(clicked)
    }
}
