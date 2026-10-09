package com.xenyaa.videoshot.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
    fun 標題_清除_套用_兩個搜尋框都在() {
        show()
        compose.onNodeWithText("篩選").assertIsDisplayed()
        compose.onNodeWithText("清除").assertIsDisplayed().assertHasClickAction()
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
        compose.onNodeWithContentDescription("搜尋地點").performClick()
        compose.onNodeWithContentDescription("搜尋地點").performTextInput("礁")
        assertEquals(listOf(true to "礁"), queries)
        queries.clear()
        compose.onNodeWithContentDescription("搜尋標籤").performClick()
        compose.onNodeWithContentDescription("搜尋標籤").performTextInput("溫")
        // draft 在測試裡是固定的（沒有 ViewModel 把字寫回去），地點框會被重設成空字串而多回報一次；只看標籤區那一筆
        assertEquals(listOf(false to "溫"), queries.filter { !it.first })
    }

    @Test
    fun 搜尋平常是圖示鈕_點了才展開成輸入框並聚焦() {
        show()
        // 收合時是按鈕、不是輸入框
        compose.onNodeWithContentDescription("搜尋地點")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertCountEquals(0)

        compose.onNodeWithContentDescription("搜尋地點").performClick()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertCountEquals(1)
        compose.onNodeWithContentDescription("搜尋地點").assertIsFocused()
        // 標籤區還是圖示鈕
        compose.onNodeWithContentDescription("搜尋標籤")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun 搜尋框空著時沒有清除鈕() {
        show()
        compose.onNodeWithContentDescription("搜尋地點").performClick()
        compose.onNodeWithContentDescription("清除搜尋文字").assertDoesNotExist()
    }

    @Test
    fun 有搜尋字時一直展開_清除鈕一按清空() {
        show(draft = FilterDraft(emptySet(), emptySet(), query = mapOf(true to "礁", false to "")))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertCountEquals(1)
        compose.onNodeWithContentDescription("清除搜尋文字").assertHasClickAction().performClick()
        assertEquals(listOf(true to ""), queries)
    }

    @Test
    fun 少量小膠囊只有一頁_沒有點點() {
        show()
        compose.onNodeWithContentDescription("頁，共", substring = true).assertDoesNotExist()
    }

    @Test
    fun 超過四列分頁_點點表示第幾頁_往左滑換到下一頁() {
        show(options = (1..60).map { place("地點$it") } + listOf(tag("溫泉")))
        compose.onNodeWithText("地點1", substring = false).assertIsDisplayed()
        // 第一頁放不下 60 個，後面的地點不在畫面上（分頁器只組看得到的那頁）
        compose.onNodeWithText("地點60").assertDoesNotExist()
        val dots = compose.onNodeWithContentDescription("第 1 頁，共", substring = true)
        dots.assertExists()
        // 地點區的分頁器（可水平捲動的節點）往左滑一頁
        // 地點區在上面，第一個就是它（標籤區只有一頁，也是分頁器）
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            .onFirst()
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("第 2 頁，共", substring = true).assertExists()
        // 標籤區只有一個，不分頁：只有地點區有點點
        compose.onAllNodesWithContentDescription("頁，共", substring = true).assertCountEquals(1)
    }

    @Test
    fun 超過100個時提示還有幾個沒列出() {
        show(options = (1..130).map { place("地點$it") })
        compose.onNodeWithText("還有 30 個沒列出，請用搜尋找").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun 不到100個沒有沒列出的提示() {
        show()
        compose.onNodeWithText("沒列出", substring = true).assertDoesNotExist()
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
    fun 有勾選時清除可以按_與套用各自回報() {
        show(draft = FilterDraft(setOf("礁溪"), emptySet()))
        compose.onNodeWithText("清除").assertIsEnabled().performClick()
        assertEquals(1, cleared)
        assertEquals(0, applied)
        compose.onNodeWithText("套用").performClick()
        assertEquals(1, applied)
    }

    @Test
    fun 沒有勾選時清除不能按() {
        show()
        compose.onNodeWithText("清除").assertIsNotEnabled().performClick()
        assertEquals(0, cleared)
    }

    // ---- 最終審查 Minor 1：候選還在讀、讀取失敗 ----

    @Test
    fun 候選讀取中兩區顯示骨架_不說這段時間沒有() {
        show(options = emptyList(), status = FilterOptionsStatus.LOADING)
        // 骨架整塊是一個節點，TalkBack 唸「載入中…」
        compose.onAllNodesWithContentDescription("載入中…").assertCountEquals(2)
        compose.onNodeWithText("這段時間沒有地點").assertDoesNotExist()
        compose.onNodeWithText("這段時間沒有標籤").assertDoesNotExist()
    }

    @Test
    fun 候選讀到後骨架消失() {
        show()
        compose.onAllNodesWithContentDescription("載入中…").assertCountEquals(0)
    }

    @Test
    fun 第一幀只有骨架_接著一列一列畫上小膠囊_不等抽屜停穩() {
        compose.mainClock.autoAdvance = false
        show()
        // 外框與搜尋框第一個畫面就在
        compose.onNodeWithText("篩選").assertExists()
        compose.onNodeWithContentDescription("搜尋地點").assertExists()
        compose.onAllNodesWithContentDescription("載入中…").assertCountEquals(2)
        compose.onNodeWithText("礁溪").assertDoesNotExist()

        // 下一幀地點區第一列就出現
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText("礁溪").assertExists()
        // 地點區一列一列畫完才輪到標籤區；十來幀（約 0.2 秒，抽屜展開動畫還沒跑完）內兩區都畫完
        repeat(12) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText("溫泉").assertExists()
        compose.onAllNodesWithContentDescription("載入中…").assertCountEquals(0)
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
