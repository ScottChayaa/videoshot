package com.xenyaa.videoshot.ui.shell

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.home.HomeScreen
import com.xenyaa.videoshot.ui.home.HomeState
import com.xenyaa.videoshot.ui.home.HomeStore
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
class WizardFinishTest {

    @get:Rule val compose = createComposeRule()

    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = File("/unused")
            override fun exists(key: ThumbKey) = false
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun row(id: Long, date: String) = ShotRow(
        id = id, videoId = "v1", atSec = 1.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = date, place = null, description = null,
    )

    /** 40 張分散在三個月，確保目標月份不在第一畫面。 */
    private fun bigState(): HomeState = HomeStore.appendPage(
        HomeState(),
        Page(
            (1..20).map { row(it.toLong(), "2026-03-%02d".format((it % 28) + 1)) } +
                (21..30).map { row(it.toLong(), "2026-02-%02d".format((it % 27) + 1)) } +
                (31..40).map { row(it.toLong(), "2026-01-%02d".format((it % 27) + 1)) },
            null,
        ),
        total = 40,
    )

    @Test
    fun 捲到指定的月份() {
        var scrolled = 0
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = bigState(),
                    loader = loader,
                    listState = rememberLazyGridState(),
                    onOpen = {}, onLoadMore = {}, onPickMonth = {}, onFacetClick = { _, _ -> },
                    scrollToMonth = "2026-01",
                    onScrolledToMonth = { scrolled++ },
                )
            }
        }
        compose.onNodeWithText("2026年1月").assertIsDisplayed()
        assertEquals("捲完要回報一次，否則每次重組都會再捲一遍", 1, scrolled)
    }

    @Test
    fun 目標月份不在已載入的範圍內時不當機() {
        var scrolled = 0
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = bigState(),
                    loader = loader,
                    listState = rememberLazyGridState(),
                    onOpen = {}, onLoadMore = {}, onPickMonth = {}, onFacetClick = { _, _ -> },
                    scrollToMonth = "2019-07",
                    onScrolledToMonth = { scrolled++ },
                )
            }
        }
        compose.onNodeWithText("2026年3月").assertIsDisplayed()
        assertEquals(1, scrolled)
    }
}
