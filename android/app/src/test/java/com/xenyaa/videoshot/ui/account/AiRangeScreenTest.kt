package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class AiRangeScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 按加號會回呼變大的值() {
        var last: Pair<Int, Int>? = null
        compose.setContent {
            VideoshotTheme {
                AiRangeScreen(beforeSec = 10, afterSec = 20, onBack = {}, onChange = { b, a -> last = b to a })
            }
        }
        compose.onNodeWithText("10").assertExists()
        compose.onNodeWithContentDescription("往前秒數增加").performClick()
        assert(last == 11 to 20)
    }

    @Test
    fun 按減號不會低於零() {
        var last: Pair<Int, Int>? = null
        compose.setContent {
            VideoshotTheme {
                AiRangeScreen(beforeSec = 0, afterSec = 20, onBack = {}, onChange = { b, a -> last = b to a })
            }
        }
        compose.onNodeWithContentDescription("往前秒數減少").performClick()
        assert(last == null)
    }
}
