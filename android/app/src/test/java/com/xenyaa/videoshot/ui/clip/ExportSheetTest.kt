package com.xenyaa.videoshot.ui.clip

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
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
        onStart: (String, UploadPrivacy, String?) -> Unit = { _, _, _ -> },
        onDismiss: () -> Unit = {},
        defaultRecordingDate: String? = "2026-10-18",
    ) {
        compose.setContent {
            VideoshotTheme {
                ExportSheet(
                    orientation, durationMs, defaultTitle, onStart = onStart, onDismiss = onDismiss,
                    defaultRecordingDate = defaultRecordingDate,
                )
            }
        }
    }

    /** 打開公開狀態的下拉選單，選 [label]。 */
    private fun pickPrivacy(label: String) {
        compose.onNodeWithContentDescription("公開狀態：", substring = true).performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test
    fun 抽屜顯示摘要標題與公開狀態預設非公開() {
        show()

        compose.onNodeWithText("導出").assertIsDisplayed()
        compose.onNodeWithText("0:48・直式・會成為 Shorts").assertIsDisplayed()
        compose.onNodeWithText("標題").assertIsDisplayed()
        compose.onNodeWithText("videoshot 2026/10/18").assertIsDisplayed()
        compose.onNodeWithContentDescription("錄影日期：2026年10月18日").assertIsDisplayed()
        compose.onNodeWithContentDescription("公開狀態：非公開").assertIsDisplayed()
        // 下拉選單還沒打開：其他選項不在畫面上
        compose.onNodeWithText("私人").assertDoesNotExist()
        compose.onNodeWithText("私人影片無法在 app 裡取圖").assertDoesNotExist()
        compose.onNodeWithText("以「非公開」上傳到你的 YouTube").assertDoesNotExist()
        compose.onNodeWithText("開始").assertIsEnabled()
    }

    @Test
    fun 選私人出現提醒並回傳私人() {
        val started = mutableListOf<Pair<String, UploadPrivacy>>()
        show(onStart = { t, p, _ -> started += t to p })

        pickPrivacy("私人")
        compose.onNodeWithContentDescription("公開狀態：私人").assertIsDisplayed()
        compose.onNodeWithText("私人影片無法在 app 裡取圖").assertIsDisplayed()
        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("videoshot 2026/10/18" to UploadPrivacy.PRIVATE), started)
    }

    @Test
    fun 選公開回傳公開() {
        val started = mutableListOf<UploadPrivacy>()
        show(onStart = { _, p, _ -> started += p })

        pickPrivacy("公開")
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
        show(durationMs = 228_000, onStart = { _, _, _ -> started = true })

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
        show(onStart = { t, _, _ -> started += t })

        compose.onNodeWithContentDescription("標題").performTextReplacement("  我的片  ")
        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("我的片"), started)
    }

    @Test
    fun 沒改標題直接開始回傳預設標題() {
        val started = mutableListOf<String>()
        show(onStart = { t, _, _ -> started += t })

        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf("videoshot 2026/10/18"), started)
    }

    @Test
    fun 下拉清單標出目前選的那項() {
        show()

        compose.onNodeWithContentDescription("公開狀態：非公開").performClick()
        compose.onNode(hasText("非公開") and isSelected()).assertIsDisplayed()
        compose.onNode(hasText("公開") and isSelected().not()).assertIsDisplayed()
        compose.onNode(hasText("私人") and isSelected().not()).assertIsDisplayed()
    }

    @Test
    fun 開始回傳錄影日期() {
        val started = mutableListOf<String?>()
        show(onStart = { _, _, d -> started += d })

        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf<String?>("2026-10-18"), started)
    }

    @Test
    fun 沒有預設錄影日期就不顯示也不回傳() {
        val started = mutableListOf<String?>()
        show(defaultRecordingDate = null, onStart = { _, _, d -> started += d })

        compose.onNodeWithContentDescription("錄影日期：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("開始").performClick()

        assertEquals(listOf<String?>(null), started)
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
