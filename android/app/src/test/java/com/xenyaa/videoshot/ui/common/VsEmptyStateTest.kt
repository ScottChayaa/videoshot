package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class VsEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    /** 手冊 §六：沒有資料夾時空狀態帶一顆【新增資料夾】，不是只有一句灰字。 */
    @Test fun 有動作時顯示按鈕() {
        var clicked = false
        compose.setContent {
            VideoshotTheme { VsEmptyState("還沒有任何分類", icon = VsIcons.Folder, actionText = "新增資料夾", onAction = { clicked = true }) }
        }
        compose.onNodeWithText("還沒有任何分類").assertIsDisplayed()
        compose.onNodeWithText("新增資料夾").performClick()
        assertTrue(clicked)
    }

    @Test fun 沒有動作就只有訊息() {
        compose.setContent { VideoshotTheme { VsEmptyState("這個時間以前沒有標籤") } }
        compose.onNodeWithText("這個時間以前沒有標籤").assertIsDisplayed()
    }
}
