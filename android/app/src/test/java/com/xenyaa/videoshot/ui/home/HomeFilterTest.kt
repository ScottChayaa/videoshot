package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.data.repo.model.MonthCount
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
class HomeFilterTest {

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
        id = id, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = date, place = null, description = null,
    )

    private fun loaded(upToMonth: String? = null): HomeState {
        val base = HomeState(
            upToMonth = upToMonth,
            months = listOf(MonthCount("2026-03", 12), MonthCount("2026-01", 4)),
        )
        return HomeStore.appendPage(base, Page(listOf(row(1, "2026-03-05")), null), 1)
    }

    private var picked: Pair<Boolean, String?> = false to null

    private fun show(state: HomeState) {
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = state,
                    loader = loader,
                    listState = rememberLazyGridState(),
                    onOpen = {},
                    onLoadMore = {},
                    onPickMonth = { picked = true to it },
                    onFacetClick = { _, _ -> },
                )
            }
        }
    }

    /** 手冊 §二第三條：一進來就是完整時間軸，沒有狀態列。 */
    @Test
    fun 預設沒有篩選狀態列() {
        show(loaded())
        compose.onNodeWithText("只顯示 2026年3月 以前的收藏").assertDoesNotExist()
    }

    @Test
    fun 日曆鈕開出月份選擇器_選項帶張數() {
        show(loaded())
        compose.onNodeWithContentDescription("依時間篩選").performClick()
        compose.onNodeWithText("只顯示這個月以前").assertIsDisplayed()
        compose.onNodeWithText("2026年3月 · 12 張").assertIsDisplayed()
        compose.onNodeWithText("2026年1月 · 4 張").assertIsDisplayed()
    }

    @Test
    fun 選一個月份會回報那個月() {
        show(loaded())
        compose.onNodeWithContentDescription("依時間篩選").performClick()
        compose.onNodeWithText("2026年1月 · 4 張").performClick()
        assertEquals(true to "2026-01", picked)
    }

    @Test
    fun 有篩選時顯示可清除的狀態列() {
        show(loaded(upToMonth = "2026-03"))
        compose.onNodeWithText("只顯示 2026年3月 以前的收藏").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除時間篩選").assertIsDisplayed()
    }

    @Test
    fun 按叉會清除篩選() {
        show(loaded(upToMonth = "2026-03"))
        compose.onNodeWithContentDescription("清除時間篩選").performClick()
        assertEquals(true to null, picked)
    }

    @Test
    fun 篩選後沒有資料的空狀態帶清除鈕() {
        show(HomeState(upToMonth = "2025-01", endReached = true))
        compose.onNodeWithText("這個時間點以前沒有收藏").assertIsDisplayed()
        compose.onNodeWithText("清除時間篩選").performClick()
        assertEquals(true to null, picked)
    }

    @Test
    fun 選擇器裡也能直接清除() {
        show(loaded(upToMonth = "2026-03"))
        compose.onNodeWithContentDescription("依時間篩選").performClick()
        compose.onNodeWithText("清除").performClick()
        assertEquals(true to null, picked)
    }
}
