package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
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

    // 統計卡每一格是一個可點節點，TalkBack 名稱「{標籤} {數字}」（階段 15D：統計卡改成單一卡片、
    // 每項 clearAndSetSemantics，數字與標籤不再是獨立文字節點，所以改用名稱找）。
    @Test
    fun 三格統計顯示正確數字() {
        setContent(state)
        compose.onNodeWithContentDescription("收藏片段 42").assertIsDisplayed()
        compose.onNodeWithContentDescription("本月新增 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("來源影片 12").assertIsDisplayed()
    }

    @Test
    fun 三格統計各自可點並回報onOpenStat() {
        var count = 0
        setContent(state, onOpenStat = { count++ })
        compose.onNodeWithContentDescription("收藏片段 42").performClick()
        compose.onNodeWithContentDescription("本月新增 5").performClick()
        compose.onNodeWithContentDescription("來源影片 12").performClick()
        assert(count == 3) { "三格都應該回報 onOpenStat，實際 $count" }
    }

    @Test
    fun 選單六列與副標都在_沒有Gemini金鑰那一列() {
        setContent(state)
        listOf("備份", "縮圖", "取圖", "AI 分析", "地點管理", "標籤管理").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        // 階段 17：描述查詢下架，查詢 · Gemini 金鑰入口隱藏（畫面與 GeminiKeyScreen 仍保留）
        compose.onNodeWithText("查詢").assertDoesNotExist()
        compose.onNodeWithText("Gemini", substring = true).assertDoesNotExist()
        compose.onNodeWithText("已使用 11.8 MB").assertIsDisplayed()
        compose.onNodeWithText("過濾相似強度：中").assertIsDisplayed()
        compose.onNodeWithText("0 個地點").assertIsDisplayed()
    }

    @Test
    fun 沒連結時點hero的尚未設定備份會開備份() {
        var opened: AccountSection? = null
        setContent(state, onOpenSection = { opened = it })
        compose.onAllNodesWithText("尚未設定備份").onFirst().performClick()
        assert(opened == AccountSection.BACKUP)
    }

    @Test
    fun 已連結時點Email會開備份() {
        var opened: AccountSection? = null
        setContent(
            state.copy(linkedAccount = LinkedGoogleAccount("阿明", "ming@example.com")),
            onOpenSection = { opened = it },
        )
        compose.onNodeWithText("ming@example.com").performClick()
        assert(opened == AccountSection.BACKUP)
    }

    /** 原型帳號頁 hero 上方有頂欄（原型寫「設定」，正式詞彙是「帳號」，見設計文件偏離清單）。 */
    @Test
    fun 有帳號頂欄() {
        setContent(state)
        compose.onNodeWithText("帳號").assertIsDisplayed()
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

    /** 手冊 §八：已連結時圓形頭像顯示帳號名稱的第一個字（大寫），不是通用人形圖示。 */
    @Test
    fun 已連結時頭像顯示名稱首字大寫() {
        setContent(state.copy(linkedAccount = LinkedGoogleAccount("ming wang", "ming@example.com")))
        compose.onNodeWithText("M").assertIsDisplayed()
    }

    @Test
    fun 點統計卡會呼叫onOpenStat() {
        var called = false
        setContent(state, onOpenStat = { called = true })
        compose.onNodeWithContentDescription("收藏片段 42").performClick()
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
