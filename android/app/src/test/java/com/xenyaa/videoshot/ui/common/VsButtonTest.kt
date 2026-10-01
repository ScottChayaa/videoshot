package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
class VsButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 點擊會回呼() {
        var n = 0
        compose.setContent { VideoshotTheme { VsButton("下一步", { n++ }) } }
        compose.onNodeWithText("下一步").performClick()
        assertEquals(1, n)
    }

    @Test fun 停用時不能點() {
        compose.setContent { VideoshotTheme { VsButton("查詢", {}, enabled = false) } }
        compose.onNodeWithText("查詢").assertIsNotEnabled()
    }

    @Test fun 至少44高() {
        compose.setContent { VideoshotTheme { VsButton("好", {}, Modifier.testTag("b"), variant = ButtonVariant.Quiet) } }
        compose.onNodeWithTag("b").assertHeightIsAtLeast(44.dp)
    }
}
