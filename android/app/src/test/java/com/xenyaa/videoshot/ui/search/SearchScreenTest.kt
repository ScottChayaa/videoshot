package com.xenyaa.videoshot.ui.search

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun show(state: SearchState, onToggleFacet: (MonthFacet) -> Unit = {}, onRunSearch: () -> Unit = {}) {
        compose.setContent {
            VideoshotTheme {
                SearchScreen(
                    state = state, loader = loader, listState = rememberLazyGridState(),
                    onSetMode = {}, onSetTextQuery = {}, onToggleFacet = onToggleFacet,
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
