package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.xenyaa.videoshot.data.repo.model.MonthFacet
import com.xenyaa.videoshot.data.repo.model.Page
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
class HomeScreenTest {

    @get:Rule val compose = createComposeRule()

    /** 測試不解真的圖：一律回預留圖，畫面會畫出秒數，剛好也驗到「不是破圖」。 */
    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun row(id: Long, date: String, description: String? = null) = ShotRow(
        id = id, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = date, place = null, description = description,
    )

    private fun stateOf(vararg rows: ShotRow, facets: Map<String, List<MonthFacet>> = emptyMap()) =
        HomeStore.appendPage(HomeState(facets = facets), Page(rows.toList(), null), rows.size)

    private fun show(state: HomeState, onOpen: (Int) -> Unit = {}, onFacet: (String, MonthFacet) -> Unit = { _, _ -> }) {
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = state,
                    loader = loader,
                    listState = rememberLazyGridState(),
                    onOpen = onOpen,
                    onLoadMore = {},
                    onPickMonth = {},
                    onFacetClick = onFacet,
                )
            }
        }
    }

    @Test
    fun 依年月分組顯示標題() {
        show(stateOf(row(3, "2026-03-05"), row(2, "2026-03-01"), row(1, "2026-01-09")))
        compose.onNodeWithText("2026年3月").assertIsDisplayed()
        compose.onNodeWithText("2026年1月").assertIsDisplayed()
    }

    /** 手冊 §二：卡片上只有縮圖，沒有描述文字。 */
    @Test
    fun 卡片上不顯示描述() {
        show(stateOf(row(1, "2026-03-05", description = "在海邊烤肉")))
        compose.onNodeWithText("在海邊烤肉").assertDoesNotExist()
    }

    /** 描述改放在 contentDescription 裡 —— TalkBack 唸得出來（手冊 §零「輔助操作」）。 */
    @Test
    fun 縮圖有可以被輔助技術唸出來的名稱() {
        show(stateOf(row(1, "2026-03-05", description = "在海邊烤肉")))
        compose.onNodeWithContentDescription("在海邊烤肉").assertIsDisplayed()
    }

    @Test
    fun 沒有描述的縮圖用中性名稱加秒數() {
        show(stateOf(row(1, "2026-03-05")))
        compose.onNodeWithContentDescription("片段縮圖 01:05").assertIsDisplayed()
    }

    @Test
    fun 點縮圖回報它在清單裡的位置() {
        var opened = -1
        show(stateOf(row(3, "2026-03-05"), row(2, "2026-03-01"), row(1, "2026-01-09")), onOpen = { opened = it })
        compose.onAllNodesWithContentDescription("片段縮圖 01:05")[2].performClick()
        assertEquals(2, opened)
    }

    @Test
    fun 月份標籤列列出該月的地點與標籤() {
        show(
            stateOf(
                row(1, "2026-03-05"),
                facets = mapOf("2026-03" to listOf(MonthFacet("宜蘭", "place", 3), MonthFacet("露營", "tag", 2))),
            )
        )
        compose.onNodeWithText("宜蘭").assertIsDisplayed()
        compose.onNodeWithText("露營").assertIsDisplayed()
    }

    @Test
    fun 點月份標籤會回報月份與標籤() {
        var picked: Pair<String, MonthFacet>? = null
        show(
            stateOf(row(1, "2026-03-05"), facets = mapOf("2026-03" to listOf(MonthFacet("宜蘭", "place", 3)))),
            onFacet = { month, facet -> picked = month to facet },
        )
        compose.onNodeWithText("宜蘭").performClick()
        assertEquals("2026-03", picked?.first)
        assertEquals("宜蘭", picked?.second?.name)
    }

    @Test
    fun 完全沒有收藏時的空狀態() {
        show(HomeState(endReached = true))
        compose.onNodeWithText("還沒有收藏").assertIsDisplayed()
        compose.onNodeWithText("按下方的【取圖】，貼一支 YouTube 網址就可以開始").assertIsDisplayed()
    }

    @Test
    fun 三張圖就有三個縮圖格() {
        show(stateOf(row(3, "2026-03-05"), row(2, "2026-03-01"), row(1, "2026-01-09")))
        compose.onAllNodesWithContentDescription("片段縮圖 01:05").assertCountEquals(3)
    }
}
