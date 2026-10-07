package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertHasClickAction
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
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun row(id: Long, date: String, description: String? = null) = ShotRow(
        id = id, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = date, place = null, description = description,
    )

    private fun stateOf(vararg rows: ShotRow, facets: Map<String, List<MonthFacet>> = emptyMap()) =
        HomeStore.appendPage(HomeState(facets = facets), Page(rows.toList(), null))

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

    /** 設計文件決定 5：首頁縮圖是正方形。 */
    @Test
    fun 首頁縮圖是正方形() {
        show(stateOf(row(3, "2026-03-05"), row(2, "2026-03-01"), row(1, "2026-01-09")))
        val b = compose.onAllNodesWithContentDescription("片段縮圖 01:05").onFirst().getUnclippedBoundsInRoot()
        assertEquals((b.right - b.left).value, (b.bottom - b.top).value, 0.5f)
    }

    /** 原型 .month-tags：地點與一般標籤的小膠囊都可以點，並回報完整的 MonthFacet（含 kind）。 */
    @Test
    fun 月份地點與人物標籤都能點且回報種類() {
        val picked = mutableListOf<MonthFacet>()
        show(
            stateOf(
                row(1, "2026-03-05"),
                facets = mapOf(
                    "2026-03" to listOf(MonthFacet("加勒比海", "place", 3), MonthFacet("小明", "tag", 2, "person")),
                ),
            ),
            onFacet = { _, f -> picked += f },
        )
        compose.onNodeWithText("加勒比海").assertHasClickAction().performClick()
        compose.onNodeWithText("小明").assertHasClickAction().performClick()
        assertEquals(listOf("place", "tag"), picked.map { it.kind })
        assertEquals("person", picked[1].tagKind)
    }

    /** 月份標籤是導覽動作、不是切換：不帶選取語意（TalkBack 不唸「未勾選，核取方塊」）。 */
    @Test
    fun 月份標籤是按鈕而不帶選取語意() {
        show(stateOf(row(1, "2026-03-05"), facets = mapOf("2026-03" to listOf(MonthFacet("宜蘭", "place", 3)))))
        compose.onNodeWithText("宜蘭")
            .assertHasClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun 篩選後沒有收藏時空狀態帶清除按鈕() {
        var cleared = false
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = HomeState(upToMonth = "2020-01", endReached = true),
                    loader = loader, listState = rememberLazyGridState(), onOpen = {}, onLoadMore = {},
                    onPickMonth = { if (it == null) cleared = true }, onFacetClick = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("這個時間點以前沒有收藏").assertIsDisplayed()
        // 狀態列與空狀態各有一個「清除時間篩選」：點文字那顆（空狀態的按鈕）
        compose.onNodeWithText("清除時間篩選").performClick()
        assertEquals(true, cleared)
    }

    @Test
    fun 讀取失敗列的重試可以點() {
        var retried = false
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = HomeState(error = "讀取失敗", endReached = true),
                    loader = loader, listState = rememberLazyGridState(), onOpen = {}, onLoadMore = { retried = true },
                    onPickMonth = {}, onFacetClick = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("重試").performClick()
        assertEquals(true, retried)
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

    /** 40 張分散在三個月，確保捲到的月份不在第一畫面（同 WizardFinishTest 的作法）。 */
    private fun bigState(upToMonth: String? = null): HomeState = HomeStore.appendPage(
        HomeState(upToMonth = upToMonth),
        Page(
            (1..20).map { row(it.toLong(), "2026-03-%02d".format((it % 28) + 1)) } +
                (21..30).map { row(it.toLong(), "2026-02-%02d".format((it % 27) + 1)) } +
                (31..40).map { row(it.toLong(), "2026-01-%02d".format((it % 27) + 1)) },
            null,
        ),
    )

    /**
     * 見階段 7 全盤覆查第 4 點：這個 composable 每次「重新掛載」（切分頁、開關 Lightbox）
     * 都是全新的一次 composition，LaunchedEffect(state.upToMonth) 不管 key 值有沒有變
     * 都會重跑一次。只看 key 相不相等的寫法在這裡會誤判成「篩選變了」，把捲動位置沖回頂端。
     *
     * listState 刻意宣告在 HomeScreen **外面**、掛載狀態切換時也不重建 —— 這跟正式環境
     * 把 `homeListState` 提到 AppRoot 是同一個道理，用來確認「捲動位置本身沒被沖掉」，
     * 只有 HomeScreen 那次重新掛載觸發的 LaunchedEffect 有沒有誤捲。
     */
    @Test
    fun 篩選沒變時重新掛載不會捲回頂端() {
        var mounted by mutableStateOf(true)
        var scrollTarget by mutableStateOf<String?>("2026-01")
        lateinit var externalListState: LazyGridState

        compose.setContent {
            VideoshotTheme {
                externalListState = rememberLazyGridState()
                if (mounted) {
                    HomeScreen(
                        state = bigState(upToMonth = "2026-03"),
                        loader = loader,
                        listState = externalListState,
                        onOpen = {},
                        onLoadMore = {},
                        onPickMonth = {},
                        onFacetClick = { _, _ -> },
                        scrollToMonth = scrollTarget,
                        onScrolledToMonth = { scrollTarget = null },
                    )
                }
            }
        }
        // 先捲到最舊那個月，離開頂端
        compose.onNodeWithText("2026年1月").assertIsDisplayed()

        // 模擬切分頁或開關 Lightbox：HomeScreen 整個離開、再回到 composition
        mounted = false
        compose.waitForIdle()
        mounted = true
        compose.waitForIdle()

        // 篩選（upToMonth）沒變：捲動位置該維持在剛才那裡，不能被沖回「2026年3月」
        compose.onNodeWithText("2026年1月").assertIsDisplayed()
    }
}
