package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
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

    @get:Rule val compose = createComposeRule()

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
}
