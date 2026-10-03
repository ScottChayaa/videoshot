package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

    @Test
    fun 單選列標出目前強度_點低會回報() {
        var selected: FilterStrength? = null
        compose.setContent {
            VideoshotTheme {
                CaptureSettingScreen(current = FilterStrength.MEDIUM, onBack = {}, onSelect = { selected = it })
            }
        }
        compose.onNodeWithText("中").assertIsSelected()
        compose.onNodeWithText("高").assertIsNotSelected()
        compose.onNodeWithText("低").assertIsNotSelected()
        compose.onNodeWithText("低").performClick()
        assert(selected == FilterStrength.LOW)
    }

    @Test
    fun 顯示群組標題_三列副標與下方說明() {
        compose.setContent {
            VideoshotTheme {
                CaptureSettingScreen(current = FilterStrength.HIGH, onBack = {}, onSelect = {})
            }
        }
        compose.onNodeWithText("過濾相似強度").assertIsDisplayed()
        compose.onNodeWithText("砍得最多；連續的相似畫面幾乎只留一張").assertIsDisplayed()
        compose.onNodeWithText("預設值；多數影片的平衡點").assertIsDisplayed()
        compose.onNodeWithText("只砍幾乎一模一樣的，寧可留多").assertIsDisplayed()
        compose.onNodeWithText("進入「挑畫面」時會先用這個強度收斂候選畫面，隨時可以在該頁按【顯示全部】看完整的候選。").assertIsDisplayed()
        compose.onNodeWithText("高").assertIsSelected()
    }
}
