package com.xenyaa.videoshot.ui.shell

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableStateOf
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
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
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

    /**
     * reload() 之後的真實順序是「先清空、稍後才補回來」，不是像 [bigState] 那樣一步到位。
     * 這段空檔要是被誤判成「這個月不存在」而提早回報，資料補齊後 scrollToMonth 已經被清成 null，
     * 效果不會再跑第二次，月份就永遠捲不到（AppRoot 呼叫 homeVm.reload() 就是這個順序）。
     */
    @Test
    fun 資料還在補齊時暫不放棄目標月份() {
        var scrolled = 0
        val state = mutableStateOf(HomeState(loading = true))
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = state.value,
                    loader = loader,
                    listState = rememberLazyGridState(),
                    onOpen = {}, onLoadMore = {}, onPickMonth = {}, onFacetClick = { _, _ -> },
                    scrollToMonth = "2026-01",
                    onScrolledToMonth = { scrolled++ },
                )
            }
        }
        // 清單還是空的、還在補資料：不能提早放棄，不然資料到位後不會再捲一次
        assertEquals("補資料的空檔不能被誤判成『這個月不存在』", 0, scrolled)

        state.value = bigState()
        compose.waitForIdle()

        compose.onNodeWithText("2026年1月").assertIsDisplayed()
        assertEquals("資料到位後才捲、才回報，整個過程只回報一次", 1, scrolled)
    }
}
