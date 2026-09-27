package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.data.repo.model.TagUsage
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
class TagManagementScreenTest {

    @get:Rule val compose = createComposeRule()

    private val tags = listOf(
        TagUsage(1, "阿明", "person", emptyList(), 3),
        TagUsage(2, "露營", "topic", listOf("野營"), 5),
    )

    @Test
    fun 列出全部標籤與張數() {
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = AccountState(tags = tags),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditKind = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = {}, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
                )
            }
        }
        compose.onNodeWithText("阿明").assertIsDisplayed()
        compose.onNodeWithText("露營").assertIsDisplayed()
    }

    @Test
    fun 點一個標籤會呼叫onOpenEditor() {
        var opened: TagUsage? = null
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = AccountState(tags = tags),
                    onBack = {}, onOpenEditor = { opened = it }, onDismissEditor = {},
                    onEditName = {}, onEditKind = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = {}, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
                )
            }
        }
        compose.onNodeWithText("阿明").performClick()
        assert(opened?.id == 1L)
    }

    @Test
    fun 編輯抽屜開著時顯示名稱欄位與目前值() {
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = AccountState(tags = tags, editor = TagEditor(2, "露營", com.xenyaa.videoshot.core.tags.TagKind.TOPIC, "野營")),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditKind = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = {}, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
                )
            }
        }
        // 「露營」同時出現在背景列表列與抽屜的名稱欄位——用別名欄位的初始值「野營」
        // 來驗證抽屜是帶著正確那個標籤的資料打開的，這個值只會出現一次，不會有多節點歧義。
        compose.onNodeWithText("野營").assertIsDisplayed()
    }

    @Test
    fun 合併確認提示顯示目標名稱按確定會呼叫onConfirmMerge() {
        var confirmed = false
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = AccountState(
                        tags = tags,
                        editor = TagEditor(1, "露營", com.xenyaa.videoshot.core.tags.TagKind.PERSON, ""),
                        pendingMerge = "露營",
                    ),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditKind = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = { confirmed = true }, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
                )
            }
        }
        compose.onNodeWithText("合併到「露營」").assertIsDisplayed()
        compose.onNodeWithText("合併").performClick()
        assert(confirmed)
    }

    @Test
    fun 刪除確認顯示標籤名按刪除會呼叫onConfirmDelete() {
        var confirmed = false
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = AccountState(tags = tags, deleting = tags[0]),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditKind = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = {}, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = { confirmed = true },
                )
            }
        }
        compose.onNodeWithText("刪除「阿明」").assertIsDisplayed()
        compose.onNodeWithText("刪除").performClick()
        assert(confirmed)
    }

    /** 最終審查 Important 3：`AccountState.error` 原本設了卻沒有任何畫面讀它——
     * 這裡釘住錯誤列真的會畫出來，按〔重試〕會呼叫 onRetry（同 FoldersScreenTest 的寫法）。 */
    @Test
    fun 讀取失敗顯示錯誤列按重試會呼叫onRetry() {
        var retried = false
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = AccountState(tags = tags, error = "操作失敗，請再試一次"),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditKind = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = {}, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
                    onRetry = { retried = true },
                )
            }
        }
        compose.onNodeWithText("操作失敗，請再試一次").assertIsDisplayed()
        compose.onNodeWithText("重試").performClick()
        assert(retried)
    }
}
