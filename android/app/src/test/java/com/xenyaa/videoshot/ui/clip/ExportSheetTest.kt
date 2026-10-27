package com.xenyaa.videoshot.ui.clip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.timeline.ClipOrientation
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
class ExportSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        orientation: ClipOrientation = ClipOrientation.PORTRAIT,
        durationMs: Long = 48_000,
        defaultTitle: String = "videoshot 2026/10/18",
        onStart: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                ExportSheet(orientation, durationMs, defaultTitle, onStart = onStart, onDismiss = onDismiss)
            }
        }
    }

    @Test
    fun 抽屜顯示摘要標題與非公開提醒() {
        show()

        compose.onNodeWithText("導出").assertIsDisplayed()
        compose.onNodeWithText("0:48・直式・會成為 Shorts").assertIsDisplayed()
        compose.onNodeWithText("標題").assertIsDisplayed()
        compose.onNodeWithText("videoshot 2026/10/18").assertIsDisplayed()
        compose.onNodeWithText("以「非公開」上傳到你的 YouTube").assertIsDisplayed()
        compose.onNodeWithText("開始").assertIsEnabled()
    }

    @Test
    fun 橫式摘要是一般影片() {
        show(orientation = ClipOrientation.LANDSCAPE, durationMs = 80_000)

        compose.onNodeWithText("1:20・橫式・一般影片").assertIsDisplayed()
    }

    @Test
    fun 超過上限紅字且開始停用() {
        var started = false
        show(durationMs = 228_000, onStart = { started = true })

        compose.onNodeWithText("3:48・直式・超過上限 48 秒，請先刪掉一些段落").assertIsDisplayed()
        compose.onNodeWithText("開始").assertIsNotEnabled()
        compose.onNodeWithText("開始").performClick()
        assertEquals(false, started)
    }

    @Test
    fun 標題清空時開始停用() {
        show()

        compose.onNodeWithContentDescription("標題").performTextClearance()
        compose.onNodeWithText("開始").assertIsNotEnabled()

        // 只有空白也算空
        compose.onNodeWithContentDescription("標題").performTextInput("   ")
        compose.onNodeWithText("開始").assertIsNotEnabled()
    }

    @Test
    fun 開始回傳修剪過空白的標題() {
        val started = mutableListOf<String>()
        show(onStart = { started += it })

        compose.onNodeWithContentDescription("標題").performTextReplacement("  我的片  ")
        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("我的片"), started)
    }

    @Test
    fun 沒改標題直接開始回傳預設標題() {
        val started = mutableListOf<String>()
        show(onStart = { started += it })

        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("videoshot 2026/10/18"), started)
    }
}
