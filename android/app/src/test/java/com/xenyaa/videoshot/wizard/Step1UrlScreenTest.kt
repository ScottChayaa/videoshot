package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Step1UrlScreenTest {

    @get:Rule val compose = createComposeRule()

    private val recents = listOf(
        RecentVideo("v1", "宜蘭兩天一夜", 1_757_000_000L, 12),
        RecentVideo("v2", "埃及市場", 1_756_000_000L, 5),
    )

    private var opened: String? = null

    private fun show(
        status: Step1Status = Step1Status.Idle,
        recent: List<RecentVideo> = recents,
    ) {
        compose.setContent {
            Step1UrlScreen(
                status = status,
                recent = recent,
                onSubmit = { opened = it },
                onOpenRecent = { opened = it },
            )
        }
    }

    @Test
    fun 最近取過的影片是可點的清單列() {
        show()
        compose.onNodeWithText("宜蘭兩天一夜").assertIsDisplayed()
        compose.onNodeWithText("12 張", substring = true).assertIsDisplayed()
        compose.onNodeWithText("宜蘭兩天一夜").performClick()
        // 點一列直接進第二步，不是把網址填回輸入框（手冊 §四第一步）
        assertEquals("v1", opened)
    }

    @Test
    fun 沒有歷史時不顯示清單標題() {
        show(recent = emptyList())
        compose.onNodeWithText("最近取過的影片").assertDoesNotExist()
    }

    @Test
    fun 送出會把輸入原樣交給呼叫端() {
        show()
        compose.onNodeWithText("貼上 YouTube 網址").performTextInput("https://youtu.be/abcdefghijk")
        compose.onNodeWithText("下一步").performClick()
        assertEquals("https://youtu.be/abcdefghijk", opened)
    }

    @Test
    fun 錯誤訊息就地顯示並說明可接受的格式() {
        show(status = Step1Status.Error("看不懂這個網址。可以貼 youtu.be/…、watch?v=…、shorts/… 或直接貼 11 碼影片 ID。"))
        compose.onNodeWithText("看不懂這個網址", substring = true).assertIsDisplayed()
        compose.onNodeWithText("shorts", substring = true).assertIsDisplayed()
    }

    @Test
    fun 讀取中時下一步不能按() {
        show(status = Step1Status.Loading)
        compose.onNodeWithText("下一步").assertIsNotEnabled()
    }
}
