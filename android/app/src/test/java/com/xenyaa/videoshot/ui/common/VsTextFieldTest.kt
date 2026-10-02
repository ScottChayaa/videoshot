package com.xenyaa.videoshot.ui.common

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
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
class VsTextFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 標籤在上方且可輸入() {
        var text by mutableStateOf("")
        compose.setContent { VideoshotTheme { VsTextField(text, { text = it }, label = "地點") } }
        compose.onNodeWithText("地點").assertIsDisplayed()
        compose.onNodeWithContentDescription("地點").performTextInput("加勒比海")
        assertEquals("加勒比海", text)
    }

    @Test fun 佔位字樣與說明都看得到() {
        compose.setContent {
            VideoshotTheme { VsTextField("", {}, label = "描述", placeholder = "〈多個值〉", mixedPlaceholder = true, supporting = "留空，之後 AI 補") }
        }
        compose.onNodeWithText("〈多個值〉").assertIsDisplayed()
        compose.onNodeWithText("留空，之後 AI 補").assertIsDisplayed()
    }

    @Test fun 按完成鍵會觸發動作() {
        var done = false
        compose.setContent {
            VideoshotTheme {
                VsTextField("夜潛", {}, label = "新增標籤",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done = true }))
            }
        }
        compose.onNodeWithContentDescription("新增標籤").performImeAction()
        assertTrue(done)
    }

    /** 輔助技術要分得出哪些欄位會被寫入（不只靠顏色）：圓點亮起才帶「將寫入」。 */
    @Test fun 亮起的標籤圓點帶將寫入語意() {
        compose.setContent {
            VideoshotTheme {
                VsTextField("", {}, label = "地點", labelAccent = true)
            }
        }
        compose.onNodeWithContentDescription("將寫入").assertExists()
    }

    @Test fun 沒亮起時沒有將寫入語意() {
        compose.setContent { VideoshotTheme { VsTextField("", {}, label = "地點") } }
        compose.onNodeWithContentDescription("將寫入").assertDoesNotExist()
    }
}
