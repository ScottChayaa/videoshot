package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.similarity.FilterStrength
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
class CaptureSettingScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 目前強度打勾其他兩個沒有() {
        var selected: FilterStrength? = null
        compose.setContent {
            VideoshotTheme {
                CaptureSettingScreen(current = FilterStrength.MEDIUM, onBack = {}, onSelect = { selected = it })
            }
        }
        compose.onNodeWithText("中").assertIsDisplayed()
        compose.onNodeWithText("高").performClick()
        assert(selected == FilterStrength.HIGH)
    }
}
