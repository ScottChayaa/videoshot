package com.xenyaa.videoshot.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class FirstRunChooserScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 兩個按鈕都顯示() {
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = {}) } }
        compose.onNodeWithText("從 Google Drive 還原").assertIsDisplayed()
        compose.onNodeWithText("全新開始").assertIsDisplayed()
    }

    @Test
    fun 點還原呼叫onRestoreClick() {
        var called = false
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = { called = true }, onStartFreshClick = {}) } }
        compose.onNodeWithText("從 Google Drive 還原").performClick()
        assert(called)
    }

    @Test
    fun 點全新開始呼叫onStartFreshClick() {
        var called = false
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = { called = true }) } }
        compose.onNodeWithText("全新開始").performClick()
        assert(called)
    }

    /**
     * 最終審查 Critical 1：全新安裝要先連結 Google 帳號才讀得到 Drive 上的備份，那段等待
     * （可能要跳系統畫面）不能讓畫面看起來像按了沒反應——按鈕要改字並停用。
     */
    @Test
    fun 連結中時按鈕改字並停用() {
        var called = false
        compose.setContent {
            VideoshotTheme {
                FirstRunChooserScreen(onRestoreClick = { called = true }, onStartFreshClick = {}, linking = true)
            }
        }
        compose.onNodeWithText("連結中…").assertIsDisplayed()
        compose.onNodeWithText("從 Google Drive 還原").assertDoesNotExist()
        compose.onNodeWithText("連結中…").performClick()
        assert(!called) { "連結中不該還能再按一次" }
    }

    /** 連結失敗要看得到原因，而且【從 Google Drive 還原】還能再按一次重試。 */
    @Test
    fun 連結失敗顯示錯誤訊息且還能再按一次() {
        var called = false
        compose.setContent {
            VideoshotTheme {
                FirstRunChooserScreen(
                    onRestoreClick = { called = true },
                    onStartFreshClick = {},
                    error = "連結 Google 帳號失敗，請再試一次",
                )
            }
        }
        compose.onNodeWithText("連結 Google 帳號失敗，請再試一次").assertIsDisplayed()
        compose.onNodeWithText("從 Google Drive 還原").performClick()
        assert(called)
    }
}
