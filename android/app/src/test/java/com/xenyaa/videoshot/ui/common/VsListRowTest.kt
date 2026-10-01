package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
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
class VsListRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 顯示主標與副標且可點() {
        var opened = false
        compose.setContent {
            VideoshotTheme { VsListRow("取圖", subtitle = "過濾相似強度：中", icon = VsIcons.Filter, onClick = { opened = true }) }
        }
        compose.onNodeWithText("過濾相似強度：中", substring = true).assertIsDisplayed()
        compose.onNodeWithText("取圖", substring = true).performClick()
        assertTrue(opened)
    }

    @Test fun 不可點的列沒有點擊動作() {
        compose.setContent { VideoshotTheme { VsListRow("版本", Modifier.testTag("r"), subtitle = "1.0") } }
        compose.onNodeWithTag("r").assertHasNoClickAction()
    }
}
