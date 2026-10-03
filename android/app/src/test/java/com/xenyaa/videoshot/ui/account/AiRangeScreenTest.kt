package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class AiRangeScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 按加號會回呼變大的值() {
        var last: Pair<Int, Int>? = null
        compose.setContent {
            VideoshotTheme {
                AiRangeScreen(beforeSec = 10, afterSec = 20, onBack = {}, onChange = { b, a -> last = b to a })
            }
        }
        // 步進器的數值節點改由共用 VsStepper 畫(語意唸成「往前秒數 10」,不再是獨立的文字節點),
        // 所以改用 contentDescription 找數值;按鈕名稱仍沿用舊的「往前秒數增加」。
        compose.onNodeWithContentDescription("往前秒數 10").assertIsDisplayed()
        compose.onNodeWithContentDescription("往前秒數增加").performClick()
        assert(last == 11 to 20)
    }

    @Test
    fun 按減號不會低於零() {
        var last: Pair<Int, Int>? = null
        compose.setContent {
            VideoshotTheme {
                AiRangeScreen(beforeSec = 0, afterSec = 20, onBack = {}, onChange = { b, a -> last = b to a })
            }
        }
        // 到 0 時減號改為停用(共用步進器的行為),點下去不會回呼。
        compose.onNodeWithContentDescription("往前秒數減少").assertIsNotEnabled().performClick()
        assert(last == null)
        compose.onNodeWithContentDescription("往前秒數增加").assertIsEnabled()
    }

    @Test
    fun 往後秒數的加減各自帶著另一個值回報() {
        var last: Pair<Int, Int>? = null
        compose.setContent {
            VideoshotTheme {
                AiRangeScreen(beforeSec = 10, afterSec = 20, onBack = {}, onChange = { b, a -> last = b to a })
            }
        }
        compose.onNodeWithContentDescription("往後秒數 20").assertIsDisplayed()
        compose.onNodeWithContentDescription("往後秒數增加").performClick()
        assert(last == 10 to 21)
        compose.onNodeWithContentDescription("往後秒數減少").performClick()
        assert(last == 10 to 19)
    }

    @Test
    fun 顯示群組標題_兩列說明與尚未上線的提示() {
        compose.setContent {
            VideoshotTheme {
                AiRangeScreen(beforeSec = 10, afterSec = 20, onBack = {}, onChange = { _, _ -> })
            }
        }
        compose.onNodeWithText("分析區間").assertIsDisplayed()
        compose.onNodeWithText("送 AI 分析時，從時間點往前涵蓋的秒數").assertIsDisplayed()
        compose.onNodeWithText("往後涵蓋的秒數").assertIsDisplayed()
        compose.onNodeWithText("AI 補充功能還沒上線，這裡的設定會先存著，上線後才會送出分析。").assertIsDisplayed()
    }
}
