package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ComingSoonScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 說得出這一區還沒做以及什麼時候做() {
        compose.setContent {
            VideoshotTheme { ComingSoonScreen(title = "分類", note = "資料夾會在階段 8 做好") }
        }
        compose.onNodeWithText("分類").assertIsDisplayed()
        compose.onNodeWithText("資料夾會在階段 8 做好").assertIsDisplayed()
    }
}
