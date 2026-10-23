package com.xenyaa.videoshot.ui.account

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xenyaa.videoshot.data.repo.model.TagUsage
import com.xenyaa.videoshot.ui.testing.near
import com.xenyaa.videoshot.ui.testing.pixelsAround
import com.xenyaa.videoshot.ui.theme.KindColors
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

    // 圖示著色測試要量像素（pixelsAround），需要 Activity 版的 rule；其餘用法與 createComposeRule 相同
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tags = listOf(
        TagUsage(1, "阿明", emptyList(), 3),
        TagUsage(2, "露營", listOf("野營"), 5),
    )

    @Test
    fun 列出全部標籤與張數() {
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = AccountState(tags = tags),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditAliases = {},
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
                    onEditName = {}, onEditAliases = {},
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
                    state = AccountState(tags = tags, editor = TagEditor(2, "露營", "野營")),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditAliases = {},
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
                        editor = TagEditor(1, "露營", ""),
                        pendingMerge = "露營",
                    ),
                    onBack = {}, onOpenEditor = {}, onDismissEditor = {},
                    onEditName = {}, onEditAliases = {},
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
                    onEditName = {}, onEditAliases = {},
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
                    onEditName = {}, onEditAliases = {},
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

    private fun showScreen(
        state: AccountState,
        onOpenEditor: (TagUsage) -> Unit = {},
        onEditName: (String) -> Unit = {},
        onRequestSave: () -> Unit = {},
        onDismissMerge: () -> Unit = {},
        onAskDelete: (TagUsage) -> Unit = {},
        onDismissDelete: () -> Unit = {},
        onStartMerge: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = state,
                    onBack = {}, onOpenEditor = onOpenEditor, onDismissEditor = {},
                    onEditName = onEditName, onEditAliases = {},
                    onRequestSave = onRequestSave, onConfirmMerge = {}, onDismissMerge = onDismissMerge,
                    onAskDelete = onAskDelete, onDismissDelete = onDismissDelete, onConfirmDelete = {},
                    onStartMerge = onStartMerge,
                )
            }
        }
    }

    /** 設定頁版面：清單在群組裡（群組標題帶標籤總數）、下方說明文字原樣保留。 */
    @Test
    fun 清單放在群組裡且說明文字保留() {
        showScreen(AccountState(tags = tags))
        compose.onNodeWithText("全部標籤（2）").assertIsDisplayed()
        compose.onNodeWithText("點一個標籤可以改名、編輯別名或刪除；刪除只解除關聯，圖不會被刪。").assertIsDisplayed()
        // 張數在同一行右側（標籤不分種類）
        compose.onNodeWithText("3 張").assertIsDisplayed()
    }

    /** 清單列圖示一律是標籤色的 `#`，不是 textDim。 */
    @Test
    fun 標籤列圖示套標籤色() {
        showScreen(AccountState(tags = tags))
        val row = compose.pixelsAround(compose.onNodeWithText("阿明"), margin = 0)
        var n = 0
        for (y in 0 until row.height) for (x in 0 until row.width) if (near(row[x, y], KindColors.tag, 0.05f)) n++
        assert(n > 0) { "標籤列應畫出標籤色的圖示" }
    }

    /** 標籤不分種類：編輯抽屜沒有種類選擇。 */
    @Test
    fun 編輯抽屜沒有種類選擇() {
        showScreen(AccountState(tags = tags, editor = TagEditor(2, "露營", "野營")))
        compose.onNodeWithText("顯示圖示").assertDoesNotExist()
        compose.onNodeWithText("人物").assertDoesNotExist()
    }

    @Test
    fun 編輯抽屜儲存與刪除會回報_名稱空白時儲存停用() {
        var saved = false
        var asked: TagUsage? = null
        showScreen(
            AccountState(tags = tags, editor = TagEditor(2, "露營", "")),
            onRequestSave = { saved = true },
            onAskDelete = { asked = it },
        )
        compose.onNodeWithText("儲存").assertIsEnabled().performClick()
        assert(saved)
        compose.onNodeWithText("刪除").performClick()
        assert(asked?.id == 2L)
    }

    @Test
    fun 編輯抽屜名稱空白時儲存停用() {
        showScreen(AccountState(tags = tags, editor = TagEditor(2, "", "")))
        compose.onNodeWithText("儲存").assertIsNotEnabled()
    }

    @Test
    fun 編輯抽屜名稱欄輸入會回報() {
        var typed = ""
        showScreen(
            AccountState(tags = tags, editor = TagEditor(2, "", "")),
            onEditName = { typed = it },
        )
        compose.onNodeWithContentDescription("名稱").performTextInput("新名")
        assert(typed == "新名")
    }

    @Test
    fun 合併確認按取消會呼叫onDismissMerge() {
        var dismissed = false
        showScreen(
            AccountState(tags = tags, editor = TagEditor(1, "露營", ""), pendingMerge = "露營"),
            onDismissMerge = { dismissed = true },
        )
        compose.onNodeWithText("取消").performClick()
        assert(dismissed)
    }

    @Test
    fun 刪除確認按取消會呼叫onDismissDelete() {
        var dismissed = false
        showScreen(AccountState(tags = tags, deleting = tags[0]), onDismissDelete = { dismissed = true })
        compose.onNodeWithText("取消").performClick()
        assert(dismissed)
    }

    @Test
    fun 編輯抽屜的合併到按鈕會呼叫onStartMerge() {
        var started = false
        showScreen(AccountState(tags = tags, editor = TagEditor(1, "阿明", "")), onStartMerge = { started = true })
        compose.onNodeWithText("合併到…").performClick()
        assert(started)
    }

    @Test
    fun 合併確認框文案() {
        val request = MergeRequest(MergeKind.TAG, 3, "明哥", 1)
        showScreen(AccountState(tags = tags, mergeConfirm = MergeConfirm(request, 1, "阿明")))
        compose.onNodeWithText("「明哥」的 1 張圖會改成「阿明」，「明哥」會變成它的別名。").assertIsDisplayed()
        compose.onNodeWithText("合併").assertIsDisplayed()
    }

    @Test
    fun 合併中顯示對話框且沒有按鈕() {
        showScreen(AccountState(tags = tags, merging = true))
        compose.onNodeWithText("合併中…").assertIsDisplayed()
        compose.onNodeWithText("取消").assertDoesNotExist()
    }

    /** 16C 設計決議 5：刪除確認框提醒改用合併。 */
    @Test
    fun 刪除確認框提醒改用合併() {
        showScreen(AccountState(tags = tags, deleting = tags[0]))
        compose.onNodeWithText("改用【合併到…】", substring = true).assertIsDisplayed()
    }
}
