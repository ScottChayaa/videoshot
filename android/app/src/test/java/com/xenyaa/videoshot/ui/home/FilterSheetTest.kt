package com.xenyaa.videoshot.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FilterSheetTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun place(name: String) = FilterOption(name, true, "other", emptyList())
    private fun tag(name: String) = FilterOption(name, false, "topic", emptyList())

    private val toggled = mutableListOf<FilterOption>()
    private val queries = mutableListOf<Pair<Boolean, String>>()
    private val expanded = mutableListOf<Boolean>()
    private var cleared = 0
    private var applied = 0
    private var dismissed = 0
    private var retried = 0

    private fun show(
        options: List<FilterOption> = listOf(place("礁溪"), place("墾丁"), tag("溫泉"), tag("露營")),
        draft: FilterDraft = FilterDraft(emptySet(), emptySet()),
        status: FilterOptionsStatus = FilterOptionsStatus.READY,
    ) {
        compose.setContent {
            VideoshotTheme {
                FilterSheet(
                    options = options,
                    draft = draft,
                    onToggle = { toggled += it },
                    onQuery = { isPlace, text -> queries += isPlace to text },
                    onExpand = { expanded += it },
                    onClear = { cleared++ },
                    onApply = { applied++ },
                    onDismiss = { dismissed++ },
                    status = status,
                    onRetry = { retried++ },
                )
            }
        }
    }

    @Test
    fun 標題_清除篩選_套用_兩個搜尋框都在() {
        show()
        compose.onNodeWithText("篩選").assertIsDisplayed()
        compose.onNodeWithText("清除篩選").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("套用").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("地點").assertIsDisplayed()
        compose.onNodeWithText("標籤").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜尋地點").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜尋標籤").assertIsDisplayed()
    }

    @Test
    fun 小膠囊是切換型_已勾選的帶選取狀態() {
        show(draft = FilterDraft(setOf("礁溪"), emptySet()))
        compose.onNodeWithText("礁溪")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        compose.onNodeWithText("墾丁").assertIsNotSelected()
        compose.onNodeWithText("溫泉").assertIsNotSelected()
    }

    @Test
    fun 點小膠囊呼叫onToggle並帶那個選項() {
        show()
        compose.onNodeWithText("墾丁").performClick()
        compose.onNodeWithText("露營").performClick()
        assertEquals(listOf(place("墾丁"), tag("露營")), toggled)
    }

    @Test
    fun 小膠囊不顯示張數() {
        show()
        // 文字要剛好等於名稱（substring = false）；後面多接一個張數就會對不到
        for (name in listOf("礁溪", "墾丁", "溫泉", "露營")) {
            compose.onNodeWithText(name, substring = false).assertIsDisplayed()
        }
        // 而且整個抽屜裡沒有任何文字帶數字
        compose.onAllNodes(
            SemanticsMatcher("文字含數字") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.any(Char::isDigit) } == true
            },
        ).assertCountEquals(0)
    }

    @Test
    fun 搜尋框打字回報是哪一區() {
        show()
        compose.onNodeWithContentDescription("搜尋地點").performTextInput("礁")
        assertEquals(listOf(true to "礁"), queries)
        queries.clear()
        compose.onNodeWithContentDescription("搜尋標籤").performTextInput("溫")
        // draft 在測試裡是固定的（沒有 ViewModel 把字寫回去），地點框會被重設成空字串而多回報一次；只看標籤區那一筆
        assertEquals(listOf(false to "溫"), queries.filter { !it.first })
    }

    @Test
    fun 超過50個出現顯示全部_點了呼叫onExpand() {
        show(options = (1..60).map { place("地點$it") } + listOf(tag("溫泉")))
        compose.onNodeWithText("顯示全部（60）").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(true), expanded)
        // 標籤區只有一個，不需要展開
        compose.onAllNodesWithText("顯示全部", substring = true).assertCountEquals(1)
    }

    @Test
    fun 已展開後沒有顯示全部() {
        show(
            options = (1..60).map { place("地點$it") },
            draft = FilterDraft(emptySet(), emptySet(), expanded = setOf(true)),
        )
        compose.onAllNodesWithText("顯示全部", substring = true).assertCountEquals(0)
    }

    @Test
    fun 區裡沒有任何候選時顯示空白說明() {
        show(options = emptyList())
        compose.onNodeWithText("這段時間沒有地點").assertIsDisplayed()
        compose.onNodeWithText("這段時間沒有標籤").assertIsDisplayed()
    }

    @Test
    fun 搜尋沒有符合時說找不到() {
        show(
            options = listOf(place("礁溪"), tag("溫泉")),
            draft = FilterDraft(emptySet(), emptySet(), query = mapOf(true to "xyz", false to "")),
        )
        compose.onNodeWithText("找不到符合的地點").assertIsDisplayed()
        compose.onNodeWithText("找不到符合的標籤").assertDoesNotExist()
        compose.onNodeWithText("溫泉").assertIsDisplayed()
    }

    @Test
    fun 清除篩選與套用各自回報() {
        show()
        compose.onNodeWithText("清除篩選").performClick()
        assertEquals(1, cleared)
        assertEquals(0, applied)
        compose.onNodeWithText("套用").performClick()
        assertEquals(1, applied)
    }

    // ---- 最終審查 Minor 1：候選還在讀、讀取失敗 ----

    @Test
    fun 候選讀取中兩區顯示載入中_不說這段時間沒有() {
        show(options = emptyList(), status = FilterOptionsStatus.LOADING)
        compose.onAllNodesWithText("載入中…").assertCountEquals(2)
        compose.onNodeWithText("這段時間沒有地點").assertDoesNotExist()
        compose.onNodeWithText("這段時間沒有標籤").assertDoesNotExist()
    }

    @Test
    fun 候選讀取中已勾選的項目照樣列出() {
        show(options = emptyList(), draft = FilterDraft(setOf("礁溪"), setOf("溫泉")), status = FilterOptionsStatus.LOADING)
        compose.onNodeWithText("礁溪").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("溫泉").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun 候選讀取失敗顯示讀取失敗與重試_點了呼叫onRetry() {
        show(options = emptyList(), status = FilterOptionsStatus.FAILED)
        compose.onNodeWithText("讀取失敗").assertIsDisplayed()
        compose.onNodeWithText("這段時間沒有地點").assertDoesNotExist()
        compose.onNodeWithText("這段時間沒有標籤").assertDoesNotExist()
        compose.onNodeWithText("重試").assertHasClickAction().performClick()
        assertEquals(1, retried)
        assertEquals(0, cleared)
    }
}
