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
    private var unarchived: FolderCard? = null
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
                    onUnarchive = { unarchived = it },
                    onRename = { _, _ -> },
                    onDelete = {},
                    onEditorName = { state = FoldersStore.editName(state, it) },
                    onStartRename = { state = FoldersStore.startRename(state, it) },
                    onAskDelete = { state = FoldersStore.askDelete(state, it) },
                    onDismissEditor = { state = FoldersStore.closeEditor(state) },
                    onDismissDelete = { state = FoldersStore.closeDelete(state) },
                    onRetry = {},
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

    @Test
    fun 選單是取消封存() {
        show(FoldersState(cards = listOf(card(2, "舊的", 100))))
        compose.onNodeWithContentDescription("「舊的」的更多操作").performClick()
        // 頂欄標題本身就是「封存相簿」，所以只剩 1 個＝選單裡沒有【封存相簿】
        compose.onAllNodesWithText("封存相簿").assertCountEquals(1)
        compose.onNodeWithText("取消封存").performClick()
        assertEquals(2L, unarchived?.id)
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
        compose.onNodeWithText("在相簿的 ⋯ 選單選【封存相簿】").assertIsDisplayed()
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
