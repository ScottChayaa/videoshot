package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
}
