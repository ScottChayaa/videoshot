package com.xenyaa.videoshot.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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

    /** 兩顆按鈕都滿版（寬度相同、接近整個畫面寬），說明文字也在。 */
    @Test
    fun 兩顆按鈕滿版且說明文字顯示() {
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = {}) } }
        compose.onNodeWithText("如果你之前備份過圖庫，可以直接還原；也可以先略過，之後隨時能在帳號頁連結。").assertIsDisplayed()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val restore = compose.onNodeWithText("從 Google Drive 還原").fetchSemanticsNode().boundsInRoot
        val fresh = compose.onNodeWithText("全新開始").fetchSemanticsNode().boundsInRoot
        assert(restore.width >= root.width * 0.8f && fresh.width >= root.width * 0.8f) { "兩顆按鈕應滿版" }
        assert(kotlin.math.abs(restore.width - fresh.width) < 1f) { "兩顆按鈕寬度應相同" }
        assert(restore.top < fresh.top) { "【從 Google Drive 還原】在上、【全新開始】在下" }
    }

    @Test
    fun 平常兩顆按鈕都可按_連結中兩顆都停用() {
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = {}) } }
        compose.onNodeWithText("從 Google Drive 還原").assertIsEnabled()
        compose.onNodeWithText("全新開始").assertIsEnabled()
    }

    @Test
    fun 連結中時全新開始也停用() {
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = {}, linking = true) } }
        compose.onNodeWithText("連結中…").assertIsNotEnabled()
        compose.onNodeWithText("全新開始").assertIsNotEnabled()
    }
}
