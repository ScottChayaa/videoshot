package com.xenyaa.videoshot.ui.folders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsNotEnabled
import com.xenyaa.videoshot.data.repo.model.FolderCard
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
class ArchivedFoldersScreenTest {

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

    private fun card(id: Long, name: String, archivedAt: Long?) =
        FolderCard(id, name, 0, lastActivityAt = 0, preview = emptyList(), archivedAt = archivedAt)

    private var backCalls = 0
    private var unarchiveSelectedCalls = 0
    private var askDeleteSelectedCalls = 0
    private var opened: FolderCard? = null
    private var state by mutableStateOf(FoldersState())

    private fun show(initial: FoldersState) {
        state = initial
        compose.setContent {
            VideoshotTheme {
                ArchivedFoldersScreen(
                    state = state,
                    loader = loader,
                    onBack = { backCalls++ },
                    onOpen = { opened = it },
                    onRename = { _, _ -> },
                    onDeleteSelected = {},
                    onEditorName = { state = FoldersStore.editName(state, it) },
                    onDismissEditor = { state = FoldersStore.closeEditor(state) },
                    onDismissDelete = { state = FoldersStore.closeDelete(state) },
                    onRetry = {},
                    onToggleSelect = { state = FoldersStore.toggleSelect(state, it) },
                    onClearSelection = { state = FoldersStore.clearSelection(state) },
                    onRenameSelected = {},
                    onUnarchiveSelected = { unarchiveSelectedCalls++ },
                    onAskDeleteSelected = { askDeleteSelectedCalls++ },
                )
            }
        }
    }

    @Test
    fun 只列封存的相簿() {
        show(FoldersState(cards = listOf(card(1, "旅行", null), card(2, "舊的", 100))))
        compose.onNodeWithText("封存相簿").assertIsDisplayed()
        compose.onNodeWithText("舊的").assertIsDisplayed()
        compose.onNodeWithText("旅行").assertDoesNotExist()
    }

    /** 長按進多選：頂欄「已選 N 本」＋【✕】，底部【重新命名】【取消封存】【刪除】，沒有【封存】。 */
    @Test
    fun 長按進多選_底部是取消封存() {
        show(FoldersState(cards = listOf(card(2, "舊的", 100), card(3, "更舊", 50))))
        compose.onNodeWithText("舊的").performTouchInput { longClick() }

        compose.onNodeWithText("已選 1 本").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消選取").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.onNodeWithText("重新命名").assertIsDisplayed()
        compose.onNodeWithText("刪除").assertIsDisplayed()
        compose.onNodeWithText("封存").assertDoesNotExist()

        compose.onNodeWithText("取消封存").performClick()
        assertEquals(1, unarchiveSelectedCalls)
    }

    @Test
    fun 多選中點卡片切換_取消選取離開多選() {
        show(FoldersState(cards = listOf(card(2, "舊的", 100), card(3, "更舊", 50))))
        compose.onNodeWithText("舊的").performTouchInput { longClick() }
        compose.onNodeWithText("更舊").performClick()
        assertEquals(setOf(2L, 3L), state.selection)
        assertEquals("多選中點卡片不該開相簿", null, opened)
        compose.onNodeWithText("重新命名").assertIsNotEnabled()

        compose.onNodeWithContentDescription("取消選取").performClick()
        assertEquals(emptySet<Long>(), state.selection)
        compose.onNodeWithText("封存相簿").assertIsDisplayed()
    }

    @Test
    fun 卡片上沒有更多操作按鈕() {
        show(FoldersState(cards = listOf(card(2, "舊的", 100))))
        compose.onNodeWithContentDescription("「舊的」的更多操作").assertDoesNotExist()
    }

    @Test
    fun 點卡片開相簿() {
        show(FoldersState(cards = listOf(card(2, "舊的", 100))))
        compose.onNodeWithText("舊的").performClick()
        assertEquals(2L, opened?.id)
    }

    @Test
    fun 沒有封存的相簿時顯示說明() {
        show(FoldersState(cards = listOf(card(1, "旅行", null))))
        compose.onNodeWithText("還沒有封存的相簿").assertIsDisplayed()
        compose.onNodeWithText("長按相簿，再按底部的【封存】").assertIsDisplayed()
    }

    @Test
    fun 讀取失敗顯示錯誤列不是空狀態() {
        show(FoldersState(error = "載入失敗，請再試一次"))
        compose.onNodeWithText("還沒有封存的相簿").assertDoesNotExist()
        compose.onNodeWithText("載入失敗，請再試一次").assertIsDisplayed()
    }

    @Test
    fun 頂欄返回鍵() {
        show(FoldersState())
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backCalls)
    }
}
