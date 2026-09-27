package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.backup.LinkedGoogleAccount
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.ui.shell.AccountSection
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
class AccountScreenTest {

    @get:Rule val compose = createComposeRule()

    private val state = AccountState(
        loading = false,
        stats = AccountStats(totalShots = 42, thisMonthShots = 5, distinctVideos = 12),
        storageUsageBytes = 12_345_678L,
    )

    private fun setContent(state: AccountState, onOpenSection: (AccountSection) -> Unit = {}, onOpenStat: () -> Unit = {}) {
        compose.setContent {
            VideoshotTheme { AccountScreen(state = state, onOpenSection = onOpenSection, onOpenStat = onOpenStat) }
        }
    }

    @Test
    fun 三格統計顯示正確數字() {
        setContent(state)
        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun 沒連結時hero顯示尚未設定備份() {
        setContent(state)
        // 「尚未設定備份」同時是 hero 的標題、也是「備份」選單列的說明文字（兩者故意同字——
        // 都是在描述同一件「還沒連結」的事實），這裡只需要確認至少顯示一次；
        // hero 排在 LazyColumn 最前面，語意樹的第一個命中一定是它。
        compose.onAllNodesWithText("尚未設定備份").onFirst().assertIsDisplayed()
    }

    @Test
    fun 已連結時hero顯示帳號名稱() {
        setContent(state.copy(linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com")))
        compose.onNodeWithText("阿明").assertIsDisplayed()
    }

    @Test
    fun 點統計卡會呼叫onOpenStat() {
        var called = false
        setContent(state, onOpenStat = { called = true })
        compose.onNodeWithText("42").performClick()
        assert(called)
    }

    @Test
    fun 點標籤管理列會呼叫onOpenSection帶TAGS() {
        var opened: AccountSection? = null
        setContent(state, onOpenSection = { opened = it })
        compose.onNodeWithText("標籤管理").performClick()
        assert(opened == AccountSection.TAGS)
    }
}
