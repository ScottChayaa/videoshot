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
