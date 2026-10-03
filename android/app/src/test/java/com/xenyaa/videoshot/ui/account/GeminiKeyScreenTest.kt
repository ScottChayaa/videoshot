package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
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
        // 共用輸入欄的標籤是欄位上方的獨立文字,輸入動作要打在欄位本身(以 contentDescription 定位)。
        compose.onNodeWithContentDescription("Gemini 金鑰").performTextInput("AIzaSy-fake")
        compose.onNodeWithText("儲存").performClick()
        assert(saved == "AIzaSy-fake")
    }

    @Test
    fun 輸入框是密碼欄_空白時儲存停用_有字才啟用() {
        compose.setContent {
            VideoshotTheme { GeminiKeyScreen(keySet = false, onBack = {}, onSave = {}, onClear = {}) }
        }
        compose.onNodeWithContentDescription("Gemini 金鑰")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("儲存").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Gemini 金鑰").performTextInput("abc")
        compose.onNodeWithText("儲存").assertIsEnabled()
    }

    @Test
    fun 顯示說明文字() {
        compose.setContent {
            VideoshotTheme { GeminiKeyScreen(keySet = false, onBack = {}, onSave = {}, onClear = {}) }
        }
        compose.onNodeWithText(
            "只用於查詢解析、只存在這台手機；換手機要重新輸入。建議在 Google Cloud 把這把金鑰限縮為只能呼叫 Generative Language API。",
        ).assertIsDisplayed()
    }
}
