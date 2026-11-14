package com.xenyaa.videoshot.wizard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
        RecentVideo("v1", "宜蘭兩天一夜", 1_757_000_000L, 12, channelTitle = "旅行頻道"),
        RecentVideo("v2", "埃及市場", 1_756_000_000L, 5),
    )

    private var opened: String? = null
    private var channelOf: String? = null

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
                onOpenChannel = { channelOf = it },
            )
        }
    }

    @Test
    fun 最近取過的影片是可點的清單列() {
        show()
        compose.onNodeWithText("歷史紀錄").performClick()
        compose.onNodeWithText("宜蘭兩天一夜").assertIsDisplayed()
        compose.onNodeWithText("12 張", substring = true).assertIsDisplayed()
        compose.onNodeWithText("宜蘭兩天一夜").performClick()
        // 點一列直接進第二步，不是把網址填回輸入框（手冊 §四第一步）
        assertEquals("v1", opened)
    }

    @Test
    fun 點頻道開頻道而不是進第二步() {
        show()
        compose.onNodeWithText("歷史紀錄").performClick()
        compose.onNodeWithText("旅行頻道").performClick()
        assertEquals("v1", channelOf)
        assertEquals(null, opened)
    }

    @Test
    fun 歷史紀錄預設收著_點標題展開再點收起() {
        show()
        compose.onNodeWithText("宜蘭兩天一夜").assertDoesNotExist()
        compose.onNodeWithText("歷史紀錄").performClick()
        compose.onNodeWithText("宜蘭兩天一夜").assertIsDisplayed()
        compose.onNodeWithText("歷史紀錄").performClick()
        compose.onNodeWithText("宜蘭兩天一夜").assertDoesNotExist()
    }

    @Test
    fun 點輸入框時剪貼簿有YouTube網址就能一鍵帶入() {
        clipboard().setPrimaryClip(ClipData.newPlainText(null, " https://youtu.be/abcdefghijk "))
        show()
        compose.onNodeWithText("貼上剛複製的網址").assertDoesNotExist()
        compose.onNodeWithContentDescription("貼上 YouTube 網址").performClick()
        compose.onNodeWithText("貼上剛複製的網址").performClick()
        compose.onNodeWithText("下一步").performClick()
        assertEquals("https://youtu.be/abcdefghijk", opened)
        // 帶入後跟輸入框一樣了，不再顯示
        compose.onNodeWithText("貼上剛複製的網址").assertDoesNotExist()
    }

    @Test
    fun 剪貼簿不是YouTube網址就不顯示貼上列() {
        clipboard().setPrimaryClip(ClipData.newPlainText(null, "晚餐吃什麼"))
        show()
        compose.onNodeWithContentDescription("貼上 YouTube 網址").performClick()
        compose.onNodeWithText("貼上剛複製的網址").assertDoesNotExist()
    }

    private fun clipboard() = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(ClipboardManager::class.java)

    @Test
    fun 沒有歷史時不顯示清單標題() {
        show(recent = emptyList())
        compose.onNodeWithText("歷史紀錄").assertDoesNotExist()
    }

    @Test
    fun 送出會把輸入原樣交給呼叫端() {
        show()
        // 欄位標籤改成「…網址或 videoId」顯示在欄位上方；可編輯節點以 semanticLabel「貼上 YouTube 網址」定位
        compose.onNodeWithContentDescription("貼上 YouTube 網址").performTextInput("https://youtu.be/abcdefghijk")
        compose.onNodeWithText("下一步").performClick()
        assertEquals("https://youtu.be/abcdefghijk", opened)
    }

    @Test
    fun 有字時右邊的圓圈叉叉可以清空() {
        show()
        compose.onNodeWithContentDescription("清除網址").assertDoesNotExist()
        compose.onNodeWithContentDescription("貼上 YouTube 網址").performTextInput("https://youtu.be/abcdefghijk")
        compose.onNodeWithContentDescription("清除網址").performClick()
        compose.onNodeWithContentDescription("清除網址").assertDoesNotExist()
        compose.onNodeWithText("下一步").performClick()
        assertEquals("", opened)
    }

    @Test
    fun 錯誤訊息就地顯示() {
        show(status = Step1Status.Error("找不到影片，請確認貼的是 YouTube 影片網址"))
        compose.onNodeWithText("找不到影片", substring = true).assertIsDisplayed()
    }

    @Test
    fun 讀取中時下一步不能按() {
        show(status = Step1Status.Loading)
        compose.onNodeWithText("下一步").assertIsNotEnabled()
    }

    /** 原型 .wz-hist：副標是「日期 · 取圖 N 張」。 */
    @Test
    fun 取圖紀錄副標寫取了幾張() {
        show(recent = listOf(RecentVideo("v3", "京都", 1_790_000_000L, 3)))
        compose.onNodeWithText("歷史紀錄").performClick()
        compose.onNodeWithText("取圖 3 張", substring = true).assertIsDisplayed()
    }

    /** 鍵盤的「前往」等同按【下一步】。 */
    @Test
    fun 鍵盤前往會送出() {
        show()
        compose.onNodeWithContentDescription("貼上 YouTube 網址").performTextInput("https://youtu.be/aqz-KE-bpKQ")
        compose.onNodeWithContentDescription("貼上 YouTube 網址").performImeAction()
        assertEquals("https://youtu.be/aqz-KE-bpKQ", opened)
    }
}
