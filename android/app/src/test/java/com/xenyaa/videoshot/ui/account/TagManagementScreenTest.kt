package com.xenyaa.videoshot.ui.account

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xenyaa.videoshot.core.tags.TagKind
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

    private fun showScreen(
        state: AccountState,
        onOpenEditor: (TagUsage) -> Unit = {},
        onEditName: (String) -> Unit = {},
        onEditKind: (TagKind) -> Unit = {},
        onRequestSave: () -> Unit = {},
        onDismissMerge: () -> Unit = {},
        onAskDelete: (TagUsage) -> Unit = {},
        onDismissDelete: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                TagManagementScreen(
                    state = state,
                    onBack = {}, onOpenEditor = onOpenEditor, onDismissEditor = {},
                    onEditName = onEditName, onEditKind = onEditKind, onEditAliases = {},
                    onRequestSave = onRequestSave, onConfirmMerge = {}, onDismissMerge = onDismissMerge,
                    onAskDelete = onAskDelete, onDismissDelete = onDismissDelete, onConfirmDelete = {},
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
        // 副標原有內容：種類 ・ 張數
        compose.onNodeWithText("人物 ・ 3 張").assertIsDisplayed()
    }

    /** 清單列圖示用「種類色」著色（人物＝person 色），不是一律的 textDim。 */
    @Test
    fun 標籤列圖示套種類色() {
        showScreen(AccountState(tags = tags))
        val personRow = compose.pixelsAround(compose.onNodeWithText("阿明"), margin = 0)
        val topicRow = compose.pixelsAround(compose.onNodeWithText("露營"), margin = 0)
        // person（靛）與 topic（紫）色差只有約 0.16，容差要夠緊才分得開
        fun count(grid: com.xenyaa.videoshot.ui.testing.Grid, c: Color): Int {
            var n = 0
            for (y in 0 until grid.height) for (x in 0 until grid.width) if (near(grid[x, y], c, 0.05f)) n++
            return n
        }
        assert(count(personRow, KindColors.person) > 0) { "人物列應畫出 person 色的圖示" }
        assert(count(personRow, KindColors.topic) == 0) { "人物列不該出現 topic 色" }
        assert(count(topicRow, KindColors.topic) > 0) { "主題列應畫出 topic 色的圖示" }
        assert(count(topicRow, KindColors.person) == 0) { "主題列不該出現 person 色" }
    }

    /** kind 選擇是切換型：四顆、目前的 kind 選中，其餘不選；點另一顆回報該 kind。 */
    @Test
    fun 編輯抽屜kind是切換型且選中目前的種類() {
        var picked: TagKind? = null
        showScreen(
            AccountState(tags = tags, editor = TagEditor(2, "露營", TagKind.TOPIC, "野營")),
            onEditKind = { picked = it },
        )
        compose.onNodeWithText("主題").assertIsSelected()
        compose.onNodeWithText("人物").assertIsNotSelected()
        compose.onNodeWithText("動物").assertIsNotSelected()
        compose.onNodeWithText("其他").assertIsNotSelected()
        compose.onNodeWithText("動物").performClick()
        assert(picked == TagKind.PET)
    }

    @Test
    fun 編輯抽屜儲存與刪除會回報_名稱空白時儲存停用() {
        var saved = false
        var asked: TagUsage? = null
        showScreen(
            AccountState(tags = tags, editor = TagEditor(2, "露營", TagKind.TOPIC, "")),
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
        showScreen(AccountState(tags = tags, editor = TagEditor(2, "", TagKind.TOPIC, "")))
        compose.onNodeWithText("儲存").assertIsNotEnabled()
    }

    @Test
    fun 編輯抽屜名稱欄輸入會回報() {
        var typed = ""
        showScreen(
            AccountState(tags = tags, editor = TagEditor(2, "", TagKind.TOPIC, "")),
            onEditName = { typed = it },
        )
        compose.onNodeWithContentDescription("名稱").performTextInput("新名")
        assert(typed == "新名")
    }

    @Test
    fun 合併確認按取消會呼叫onDismissMerge() {
        var dismissed = false
        showScreen(
            AccountState(tags = tags, editor = TagEditor(1, "露營", TagKind.PERSON, ""), pendingMerge = "露營"),
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
}
