package com.xenyaa.videoshot.ui.home

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

/** 照片頁多選：長按進入、點一下切換、頂欄「已選 N 張」、底部【刪除】【加入相簿】。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeSelectionTest {

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
        id = id, videoId = "v1", atSec = 60.0 + id, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
        eventDate = "2026-03-05", place = null, description = null,
    )

    private var selection by mutableStateOf(emptySet<Long>())
    private var opened = -1
    private var deleted: Set<Long>? = null
    private var addCalls = 0

    private fun show() {
        val state = HomeStore.appendPage(HomeState(), Page(listOf(row(1), row(2), row(3)), null))
        compose.setContent {
            VideoshotTheme {
                HomeScreen(
                    state = state,
                    loader = loader,
                    listState = rememberLazyGridState(),
                    onOpen = { opened = it },
                    onLoadMore = {},
                    onPickMonth = {},
                    onFacetClick = {},
                    selection = selection,
                    onToggleSelect = { id -> selection = if (id in selection) selection - id else selection + id },
                    onClearSelection = { selection = emptySet() },
                    onDeleteSelected = { deleted = selection; selection = emptySet() },
                    onAddSelectedToAlbum = { addCalls++ },
                )
            }
        }
    }

    private fun tile(sec: Int) = compose.onNodeWithContentDescription("片段縮圖 01:%02d".format(sec))

    @Test
    fun 長按進入多選_頂欄顯示已選張數_底部出現刪除與加入相簿() {
        show()
        compose.onNodeWithText("刪除").assertDoesNotExist()
        tile(1).performTouchInput { longClick() }
        assertEquals(setOf(1L), selection)
        compose.onNodeWithText("已選 1 張").assertIsDisplayed()
        compose.onNodeWithText("刪除").assertIsDisplayed()
        compose.onNodeWithText("加入相簿").assertIsDisplayed()
        // 篩選與日曆鈕收起來
        compose.onNodeWithContentDescription("依時間篩選").assertDoesNotExist()
    }

    @Test
    fun 多選中點一下是切換選取不是開啟_取消最後一張就離開多選() {
        show()
        tile(1).performTouchInput { longClick() }
        tile(2).performClick()
        assertEquals(setOf(1L, 2L), selection)
        assertEquals("多選中點縮圖不該開 Lightbox", -1, opened)
        compose.onNodeWithText("已選 2 張").assertIsDisplayed()

        tile(1).performClick()
        tile(2).performClick()
        assertEquals(emptySet<Long>(), selection)
        compose.onNodeWithText("刪除").assertDoesNotExist()
        compose.onNodeWithContentDescription("依時間篩選").assertIsDisplayed()
    }

    @Test
    fun 已選的格子帶勾選狀態() {
        show()
        tile(2).performTouchInput { longClick() }
        compose.onAllNodesWithContentDescription("片段縮圖 01:02")
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On),
        ).assertIsDisplayed()
    }

    @Test
    fun 頂欄的取消選取離開多選() {
        show()
        tile(1).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("取消選取").performClick()
        assertEquals(emptySet<Long>(), selection)
    }

    @Test
    fun 刪除要先確認_確認後才回報() {
        show()
        tile(1).performTouchInput { longClick() }
        tile(3).performClick()
        compose.onNodeWithText("刪除").performClick()
        compose.onNodeWithText("刪除 2 張照片？").assertIsDisplayed()
        assertEquals(null, deleted)
        // 確認框的【刪除】（動作列也有一個「刪除」，確認框在最後一個視窗）
        compose.onAllNodesWithText("刪除").onLast().performClick()
        assertEquals(setOf(1L, 3L), deleted)
    }

    /** 動作列每一顆都是圖示＋文字，整顆是一個按鈕（TalkBack 唸文字）。 */
    @Test
    fun 動作列的按鈕帶文字() {
        show()
        tile(1).performTouchInput { longClick() }
        compose.onNodeWithText("刪除").assertHasClickAction()
        compose.onNodeWithText("加入相簿").assertHasClickAction()
    }

    @Test
    fun 加入相簿回報一次() {
        show()
        tile(1).performTouchInput { longClick() }
        compose.onNodeWithText("加入相簿").performClick()
        assertEquals(1, addCalls)
    }
}
