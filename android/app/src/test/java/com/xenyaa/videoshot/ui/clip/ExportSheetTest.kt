package com.xenyaa.videoshot.ui.clip

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.data.repo.model.UploadPrivacy
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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
        onStart: (String, UploadPrivacy) -> Unit = { _, _ -> },
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                ExportSheet(orientation, durationMs, defaultTitle, onStart = onStart, onDismiss = onDismiss)
            }
        }
    }

    @Test
    fun 抽屜顯示摘要標題與公開狀態預設非公開() {
        show()

        compose.onNodeWithText("導出").assertIsDisplayed()
        compose.onNodeWithText("0:48・直式・會成為 Shorts").assertIsDisplayed()
        compose.onNodeWithText("標題").assertIsDisplayed()
        compose.onNodeWithText("videoshot 2026/10/18").assertIsDisplayed()
        compose.onNodeWithText("公開狀態").assertIsDisplayed()
        compose.onNodeWithText("非公開").assertIsSelected()
        compose.onNodeWithText("公開").assertIsNotSelected()
        compose.onNodeWithText("私人").assertIsNotSelected()
        compose.onNodeWithText("私人影片無法在 app 裡取圖").assertDoesNotExist()
        compose.onNodeWithText("以「非公開」上傳到你的 YouTube").assertDoesNotExist()
        compose.onNodeWithText("開始").assertIsEnabled()
    }

    @Test
    fun 選私人出現提醒並回傳私人() {
        val started = mutableListOf<Pair<String, UploadPrivacy>>()
        show(onStart = { t, p -> started += t to p })

        compose.onNodeWithText("私人").performClick()
        compose.onNodeWithText("私人").assertIsSelected()
        compose.onNodeWithText("非公開").assertIsNotSelected()
        compose.onNodeWithText("私人影片無法在 app 裡取圖").assertIsDisplayed()
        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("videoshot 2026/10/18" to UploadPrivacy.PRIVATE), started)
    }

    @Test
    fun 選公開回傳公開() {
        val started = mutableListOf<UploadPrivacy>()
        show(onStart = { _, p -> started += p })

        compose.onNodeWithText("公開").performClick()
        compose.onNodeWithText("私人影片無法在 app 裡取圖").assertDoesNotExist()
        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf(UploadPrivacy.PUBLIC), started)
    }

    @Test
    fun 橫式摘要只寫橫式影片() {
        show(orientation = ClipOrientation.LANDSCAPE, durationMs = 80_000)

        compose.onNodeWithText("1:20・橫式影片").assertIsDisplayed()
    }

    @Test
    fun 超過上限紅字且開始停用() {
        var started = false
        show(durationMs = 228_000, onStart = { _, _ -> started = true })

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
        show(onStart = { t, _ -> started += t })

        compose.onNodeWithContentDescription("標題").performTextReplacement("  我的片  ")
        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("我的片"), started)
    }

    @Test
    fun 沒改標題直接開始回傳預設標題() {
        val started = mutableListOf<String>()
        show(onStart = { t, _ -> started += t })

        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("videoshot 2026/10/18"), started)
    }

    @Test
    fun 點服務條款開YouTube服務條款() {
        show()

        compose.onNodeWithText("上傳即表示同意 YouTube 服務條款").performClick()

        // 抽屜是另一個視窗，自己帶連結處理器（換不掉）：改看實際送出去的 Intent
        val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://www.youtube.com/t/terms", intent.dataString)
    }
}
