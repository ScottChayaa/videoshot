package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class GeminiKeyScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 沒有金鑰時顯示尚未設定沒有清除按鈕() {
        compose.setContent {
            VideoshotTheme { GeminiKeyScreen(keySet = false, onBack = {}, onSave = {}, onClear = {}) }
        }
        compose.onNodeWithText("尚未設定").assertIsDisplayed()
    }

    @Test
    fun 已設定時顯示清除按鈕點下去會呼叫onClear() {
        var cleared = false
        compose.setContent {
            VideoshotTheme { GeminiKeyScreen(keySet = true, onBack = {}, onSave = {}, onClear = { cleared = true }) }
        }
        compose.onNodeWithText("已設定").assertIsDisplayed()
        compose.onNodeWithText("清除").performClick()
        assert(cleared)
    }

    @Test
    fun 輸入金鑰按儲存會帶著輸入值呼叫onSave() {
        var saved: String? = null
        compose.setContent {
            VideoshotTheme { GeminiKeyScreen(keySet = false, onBack = {}, onSave = { saved = it }, onClear = {}) }
        }
        compose.onNodeWithText("Gemini 金鑰").performTextInput("AIzaSy-fake")
        compose.onNodeWithText("儲存").performClick()
        assert(saved == "AIzaSy-fake")
    }
}
