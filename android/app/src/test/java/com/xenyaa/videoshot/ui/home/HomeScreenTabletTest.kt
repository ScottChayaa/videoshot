package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import com.xenyaa.videoshot.core.home.homeColumnsFor
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 600dp 以上要加欄（手冊 §零）。[HomeScreenTest] 等其他測試都固定在手機寬度
 * （`w411dp-h891dp`），只驗得到 `homeColumnsFor` 這個純函式本身；這裡另外開一個
 * 平板寬度的 Robolectric 設定，驗證縮圖牆**實際排版**真的照這個欄數走
 * （見階段 7 全盤覆查第 8 點最後一項）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp")
class HomeScreenTabletTest {

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

    private fun row(id: Long) = ShotRow(
        id = id, videoId = "v1", atSec = 1.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = "2026-03-05", place = null, description = null,
    )

    @Test
    fun `寬螢幕的縮圖牆真的排出更多欄`() {
        var widthDp = 0
        lateinit var listState: LazyGridState
        compose.setContent {
            VideoshotTheme {
                widthDp = LocalConfiguration.current.screenWidthDp
                listState = rememberLazyGridState()
                val state = HomeStore.appendPage(
                    HomeState(),
                    Page((0 until 12).map { row(it.toLong()) }, null),
                    total = 12,
                )
                HomeScreen(
                    state = state,
                    loader = loader,
                    listState = listState,
                    onOpen = {},
                    onLoadMore = {},
                    onPickMonth = {},
                    onFacetClick = { _, _ -> },
                )
            }
        }
        compose.waitForIdle()

        val expectedColumns = homeColumnsFor(widthDp)
        // 手機三欄的固定寬度在這個 qualifier 下該被超過，不然這個測試沒驗到「加欄」
        assertTrue("寬度 ${widthDp}dp 該落在加欄的門檻之上", expectedColumns > 3)

        val tilesPerRow = listState.layoutInfo.visibleItemsInfo
            .filter { it.key.toString().startsWith("t-") }
            .groupBy { it.row }
            .values
            .maxOf { it.size }

        assertEquals(expectedColumns, tilesPerRow)
    }
}
