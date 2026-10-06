package com.xenyaa.videoshot.ui.account

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.ui.testing.near
import com.xenyaa.videoshot.ui.testing.pixelsAround
import com.xenyaa.videoshot.ui.theme.Palettes
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
class BackupScreenTest {

    // 錯誤提示框的顏色測試要量像素（pixelsAround），需要 Activity 版的 rule；其餘用法與 createComposeRule 相同
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val linked = LinkedGoogleAccount("阿明", "ming@example.com")

    private fun show(
        account: LinkedGoogleAccount? = linked,
        backingUp: Boolean = false,
        error: String? = null,
        onLink: () -> Unit = {},
        onUnlink: () -> Unit = {},
        onBackupNow: () -> Unit = {},
        onRestore: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = account, lastBackupAtEpochSec = 0, backingUp = backingUp, backupError = error,
                    onBack = {}, onLinkClick = onLink, onUnlinkClick = onUnlink,
                    onBackupNowClick = onBackupNow, onRestoreClick = onRestore,
                )
            }
        }
    }

    private fun count(node: androidx.compose.ui.test.SemanticsNodeInteraction, c: Color): Int {
        val grid = compose.pixelsAround(node, margin = 0)
        var n = 0
        for (y in 0 until grid.height) for (x in 0 until grid.width) if (near(grid[x, y], c, 0.2f)) n++
        return n
    }

    @Test
    fun 未連結時顯示連結按鈕_沒有備份按鈕() {
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = null, lastBackupAtEpochSec = 0, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = {}, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("連結 Google 帳號以啟用備份").assertIsDisplayed()
    }

    @Test
    fun 已連結時顯示帳號資訊與上次備份時間() {
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com"),
                    lastBackupAtEpochSec = 1000, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = {}, onRestoreClick = {},
                    nowEpochSec = 1030,
                )
            }
        }
        compose.onNodeWithText("已連結：阿明（ming@example.com）").assertIsDisplayed()
        compose.onNodeWithText("上次備份：剛剛").assertIsDisplayed()
    }

    /**
     * 最終審查 Important 4：連結失敗時 `linkedAccount` 還是 null——錯誤訊息如果畫在
     * 「已連結」那一支分支裡（原本的寫法），使用者最需要看到它的那條路徑永遠看不到。
     */
    @Test
    fun 未連結時也要顯示錯誤訊息() {
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = null, lastBackupAtEpochSec = 0, backingUp = false,
                    backupError = "連結 Google 帳號失敗，請確認網路後再試一次",
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = {}, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("連結 Google 帳號失敗，請確認網路後再試一次").assertIsDisplayed()
    }

    /** 已連結那一支（中斷連結／立即備份失敗）也還要顯示得出來——改位置不能把原本的行為弄丟。 */
    @Test
    fun 已連結時同樣顯示錯誤訊息() {
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com"),
                    lastBackupAtEpochSec = 0, backingUp = false, backupError = "中斷連結失敗，請確認網路後再試一次",
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = {}, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("中斷連結失敗，請確認網路後再試一次").assertIsDisplayed()
    }

    @Test
    fun 點立即備份會呼叫onBackupNowClick() {
        var called = false
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com"),
                    lastBackupAtEpochSec = 0, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = {}, onBackupNowClick = { called = true }, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("立即備份").performClick()
        assert(called)
    }

    @Test
    fun 點中斷連結先跳確認框_按確認才真的呼叫onUnlinkClick() {
        var called = false
        compose.setContent {
            VideoshotTheme {
                BackupScreen(
                    linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com"),
                    lastBackupAtEpochSec = 0, backingUp = false, backupError = null,
                    onBack = {}, onLinkClick = {}, onUnlinkClick = { called = true }, onBackupNowClick = {}, onRestoreClick = {},
                )
            }
        }
        compose.onNodeWithText("中斷連結").performClick()
        assert(!called) // 只是跳出確認框，還沒真的呼叫
        compose.onAllNodesWithText("中斷連結").onLast().performClick() // 確認框裡的那顆
        assert(called)
    }

    /** 【中斷連結】在最底：位置在【立即備份】【從 Drive 還原】下方、貼近畫面底緣，而且滿版（手冊 §一、§八）。 */
    @Test
    fun 中斷連結在最底且滿版() {
        show()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val unlink = compose.onNodeWithText("中斷連結").fetchSemanticsNode().boundsInRoot
        val backup = compose.onNodeWithText("立即備份").fetchSemanticsNode().boundsInRoot
        val restore = compose.onNodeWithText("從 Drive 還原").fetchSemanticsNode().boundsInRoot
        assert(unlink.top >= backup.bottom && unlink.top >= restore.bottom) { "中斷連結應在其他按鈕下方" }
        assert(root.bottom - unlink.bottom < root.height * 0.1f) { "中斷連結應貼近畫面底緣" }
        assert(unlink.width >= root.width * 0.85f) { "中斷連結應滿版（寬 ${unlink.width} / ${root.width}）" }
    }

    @Test
    fun 未連結時沒有中斷連結_也沒有備份與還原按鈕() {
        show(account = null)
        compose.onNodeWithText("中斷連結").assertDoesNotExist()
        compose.onNodeWithText("立即備份").assertDoesNotExist()
        compose.onNodeWithText("從 Drive 還原").assertDoesNotExist()
    }

    @Test
    fun 點連結按鈕會呼叫onLinkClick() {
        var called = false
        show(account = null, onLink = { called = true })
        compose.onNodeWithText("連結 Google 帳號以啟用備份").performClick()
        assert(called)
    }

    @Test
    fun 點從Drive還原會呼叫onRestoreClick() {
        var called = false
        show(onRestore = { called = true })
        compose.onNodeWithText("從 Drive 還原").performClick()
        assert(called)
    }

    @Test
    fun 備份中時按鈕改字並停用() {
        show(backingUp = true)
        compose.onNodeWithText("備份中…").assertIsNotEnabled()
        compose.onNodeWithText("立即備份").assertDoesNotExist()
    }

    @Test
    fun 沒在備份時立即備份可按() {
        show()
        compose.onNodeWithText("立即備份").assertIsEnabled()
    }

    @Test
    fun 確認框按取消不會呼叫onUnlinkClick() {
        var called = false
        show(onUnlink = { called = true })
        compose.onNodeWithText("中斷連結").performClick()
        compose.onNodeWithText("取消").performClick()
        assert(!called)
        compose.onNodeWithText("中斷連結？").assertDoesNotExist()
    }

    /** 備份錯誤不是破壞性動作：用 warn 色的提示框，不是紅色（紅色只留給中斷連結等破壞性按鈕）。 */
    @Test
    fun 備份錯誤用warn色而不是紅色() {
        show(error = "備份失敗，請稍後再試")
        val node = compose.onNodeWithText("備份失敗，請稍後再試")
        val colors = Palettes.DEFAULT.palette
        assert(count(node, colors.warn) > 0) { "錯誤文字應是 warn 色" }
        assert(count(node, colors.danger) == 0) { "錯誤文字不該出現紅色" }
    }
}
