package com.xenyaa.videoshot.ui.search

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SearchScreenTest {

    @get:Rule val compose = createComposeRule()

    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun show(
        state: SearchState,
        onToggleFacet: (MonthFacet) -> Unit = {},
        onRunSearch: () -> Unit = {},
        onSetMode: (SearchMode) -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                SearchScreen(
                    state = state, loader = loader, listState = rememberLazyGridState(),
                    onSetMode = onSetMode, onSetTextQuery = {}, onToggleFacet = onToggleFacet,
                    onShowMoreFacets = {}, onPickMonth = {}, onRunSearch = onRunSearch,
                    onLoadMore = {}, onShowConditions = {}, onOpen = {},
                )
            }
        }
    }

    @Test
    fun 沒有選條件時查詢按鈕停用且顯示原因() {
        // 條件頁的頂欄標題跟停用按鈕都顯示「查詢」二字（見 SearchTopBar／queryButtonLabel），
        // 用 hasClickAction() 縮定到按鈕那一個節點，不然 onNodeWithText 會因為找到 2 個節點而丟例外。
        show(SearchState())
        compose.onNode(hasText("查詢") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("先選一個以上的標籤或地點").assertIsDisplayed()
    }

    @Test
    fun 選了條件後按鈕文字帶數量且不再停用() {
        val state = SearchState(facets = listOf(MonthFacet("宜蘭", "place", 3)), selected = setOf("place:宜蘭"))
        show(state)
        compose.onNodeWithText("查詢 1 個條件").assertIsDisplayed()
    }

    @Test
    fun 點chip會回呼toggle() {
        var toggled: MonthFacet? = null
        show(SearchState(facets = listOf(MonthFacet("宜蘭", "place", 3))), onToggleFacet = { toggled = it })
        compose.onNodeWithText("宜蘭 3").performClick()
        assert(toggled?.name == "宜蘭")
    }

    /**
     * 打勾圖示現在是 `VsTagChip` 自己畫的（`contentDescription = "已選"`），不再是查詢頁手畫、
     * 掛 `testTag("chipCheck")` 的 Icon。小膠囊是可點的合併語意節點，圖示的描述要用未合併樹才找得到。
     */
    @Test
    fun 選取的chip顯示打勾圖示未選取的不顯示() {
        val state = SearchState(
            facets = listOf(MonthFacet("宜蘭", "place", 3), MonthFacet("台北", "place", 2)),
            selected = setOf("place:宜蘭"),
        )
        show(state)
        compose.onAllNodesWithContentDescription("已選", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun 標籤雲的小膠囊是切換型選中時帶選取狀態() {
        val state = SearchState(
            facets = listOf(MonthFacet("宜蘭", "place", 3), MonthFacet("台北", "place", 2)),
            selected = setOf("place:宜蘭"),
        )
        show(state)
        compose.onNodeWithText("宜蘭 3").assertIsSelected()
        compose.onNodeWithText("台北 2").assertIsNotSelected()
    }

    @Test
    fun 標籤雲的標籤帶張數() {
        show(SearchState(facets = listOf(MonthFacet("夜潛", "tag", 3, "topic"))))
        compose.onNodeWithText("夜潛 3").assertIsDisplayed()
    }

    @Test
    fun 模式分頁是底線分頁點描述回報TEXT() {
        var mode: SearchMode? = null
        show(SearchState(), onSetMode = { mode = it })
        compose.onNodeWithText("標籤與地點").assertIsSelected()
        compose.onNodeWithText("描述").assertIsNotSelected()
        compose.onNodeWithText("描述").performClick()
        assertEquals(SearchMode.TEXT, mode)
    }

    @Test
    fun 時間欄位顯示目前範圍且點了會開月份選擇() {
        show(SearchState())
        compose.onNodeWithContentDescription("時間：全部日期").assertIsDisplayed().performClick()
        compose.onNodeWithText("只顯示這個月以前").assertIsDisplayed()
    }

    @Test
    fun 描述模式按鍵盤搜尋鍵在能查時觸發查詢() {
        var runs = 0
        show(SearchState(mode = SearchMode.TEXT, textQuery = "大蝦"), onRunSearch = { runs++ })
        compose.onNodeWithContentDescription("描述關鍵字").performImeAction()
        assertEquals(1, runs)
    }

    @Test
    fun 描述模式沒有字時按鍵盤搜尋鍵不觸發查詢() {
        var runs = 0
        show(SearchState(mode = SearchMode.TEXT, textQuery = ""), onRunSearch = { runs++ })
        compose.onNodeWithContentDescription("描述關鍵字").performImeAction()
        assertEquals(0, runs)
    }

    @Test
    fun 結果階段顯示結果列與聽懂了摘要() {
        val row = ShotRow(1, "v1", 10.0, "storyboard", 1, 3, "2026-03-01", null, null)
        val state = SearchState(
            phase = SearchPhase.RESULTS, results = listOf(row), total = 1,
            heard = ResolvedSummary("加勒比海・夜潛・大蝦", local = true),
        )
        show(state)
        compose.onNodeWithText("1 張").assertIsDisplayed()
        compose.onNodeWithText("聽懂了：加勒比海・夜潛・大蝦（本機解析）").assertIsDisplayed()
    }

    @Test
    fun 結果階段標籤模式結果列顯示選取的條件chip() {
        val state = SearchState(
            phase = SearchPhase.RESULTS,
            mode = SearchMode.TAG,
            facets = listOf(MonthFacet("宜蘭", "place", 3)),
            selected = setOf("place:宜蘭"),
        )
        show(state)
        compose.onNodeWithText("宜蘭").assertIsDisplayed()
    }

    /**
     * `seedFromHome` 帶進來的 key 是從單一月份的 facet 池子挑出來的，`loadFacets()` 重查
     * 整個時間範圍之後很可能把它修剪掉（`SearchStore.loadedFacets` 的 pruning）——結果列的
     * 條件 chip 不能因此消失，因為它現在直接從 `selected` 的 key 解析名稱，不反查 `facets`
     * （最終審查 Important 3）。這裡刻意讓 `facets` 完全不含 `selected` 裡的那個 key，
     * 模擬修剪後的狀態。
     */
    @Test
    fun 結果階段標籤模式即使facets被修剪掉條件chip仍然從selected顯示() {
        val state = SearchState(
            phase = SearchPhase.RESULTS,
            mode = SearchMode.TAG,
            facets = listOf(MonthFacet("台北", "place", 9)), // 池子裡沒有「宜蘭」
            selected = setOf("place:宜蘭"),
        )
        show(state)
        compose.onNode(hasText("宜蘭") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun 結果階段文字模式結果列顯示查詢字串chip() {
        val state = SearchState(
            phase = SearchPhase.RESULTS,
            mode = SearchMode.TEXT,
            textQuery = "大蝦",
            heard = ResolvedSummary("加勒比海・夜潛・大蝦", local = true),
        )
        show(state)
        // 「大蝦」單獨成一個節點的只有結果列的條件 chip——「聽懂了：…大蝦（本機解析）」
        // 是同一個 Text 裡的完整句子，onNodeWithText 預設精確比對不會跟它撞在一起，
        // 但還是用 hasClickAction() 明確鎖定 chip，跟前面「查詢」二字的處理手法一致。
        compose.onNode(hasText("大蝦") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun 結果階段沒有結果時顯示空狀態() {
        show(SearchState(phase = SearchPhase.RESULTS, results = emptyList(), total = 0))
        compose.onNodeWithText("沒有符合的收藏").assertIsDisplayed()
    }

    @Test
    fun 結果階段有返回鍵改條件() {
        var backClicked = false
        compose.setContent {
            VideoshotTheme {
                SearchScreen(
                    state = SearchState(phase = SearchPhase.RESULTS),
                    loader = loader, listState = rememberLazyGridState(),
                    onSetMode = {}, onSetTextQuery = {}, onToggleFacet = {}, onShowMoreFacets = {},
                    onPickMonth = {}, onRunSearch = {}, onLoadMore = {},
                    onShowConditions = { backClicked = true }, onOpen = {},
                )
            }
        }
        compose.onNodeWithContentDescription("改條件").performClick()
        assert(backClicked)
    }
}
