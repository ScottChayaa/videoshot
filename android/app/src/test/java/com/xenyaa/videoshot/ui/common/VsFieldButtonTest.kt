package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class VsFieldButtonTest {
    @get:Rule val compose = createComposeRule()

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test fun 整顆是一個按鈕_名稱是小字() {
        var clicks = 0
        compose.setContent { VideoshotTheme { VsFieldButton(VsIcons.MapPin, "地點", onClick = { clicks++ }) } }
        compose.onNodeWithContentDescription("地點").assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test fun 完成時唸出已完成() {
        compose.setContent { VideoshotTheme { VsFieldButton(VsIcons.Calendar, "時間", onClick = {}, done = true) } }
        compose.onNodeWithContentDescription("時間").assert(state("已完成"))
    }

    @Test fun 未完成時唸出未完成() {
        compose.setContent { VideoshotTheme { VsFieldButton(VsIcons.MapPin, "地點", onClick = {}, done = false) } }
        compose.onNodeWithContentDescription("地點").assert(state("未完成"))
    }

    @Test fun 沒有完成狀態的按鈕不唸狀態() {
        compose.setContent { VideoshotTheme { VsFieldButton(VsIcons.ImagesCheck, "全選", onClick = {}) } }
        compose.onNodeWithContentDescription("全選").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test fun 停用時按不下去() {
        compose.setContent { VideoshotTheme { VsFieldButton(VsIcons.Tag, "標籤", onClick = {}, enabled = false) } }
        compose.onNodeWithContentDescription("標籤").assertIsNotEnabled()
    }

    @Test fun 綠勾完整落在按鈕範圍內_不被裁切() {
        compose.setContent { VideoshotTheme { VsFieldButton(VsIcons.Calendar, "時間", onClick = {}, done = true) } }
        val button = compose.onNodeWithContentDescription("時間").getUnclippedBoundsInRoot()
        // 綠勾在 clearAndSetSemantics 底下，只有未合併的語意樹找得到
        val badge = compose.onNodeWithTag(FIELD_BUTTON_DONE_BADGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("綠勾上緣 ${badge.top} 不可高過按鈕上緣 ${button.top}", badge.top >= button.top)
        assertTrue("綠勾右緣 ${badge.right} 不可超過按鈕右緣 ${button.right}", badge.right <= button.right)
        assertTrue("綠勾下緣 ${badge.bottom} 不可超過按鈕下緣 ${button.bottom}", badge.bottom <= button.bottom)
    }

    @Test fun 綠勾加圖示加小字整組在按鈕內垂直置中() {
        compose.setContent { VideoshotTheme { VsFieldButton(VsIcons.Calendar, "時間", onClick = {}, done = true) } }
        val button = compose.onNodeWithContentDescription("時間").getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag(FIELD_BUTTON_DONE_BADGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = compose.onNodeWithTag(FIELD_BUTTON_LABEL_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        // 綠勾上緣是整組內容的視覺頂端；它到按鈕上緣的距離要等於小字到按鈕下緣的距離
        val above = badge.top - button.top
        val below = button.bottom - label.bottom
        assertTrue("上方留白 $above 與下方留白 $below 應相差不到 1dp", kotlin.math.abs((above - below).value) < 1f)
    }
}
