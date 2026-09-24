package com.xenyaa.videoshot.ui.folders

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
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
class FolderScreenTest {

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

    /** atSec 65 秒 → 縮圖唸出來是「片段縮圖 01:05」。 */
    private fun shot(id: Long) = ShotRow(
        id = id, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = "2026-03-01", place = null, description = null,
    )

    private fun card(id: Long, name: String, count: Int = 0) =
        FolderCard(id, name, count, lastActivityAt = 0, preview = emptyList())

    private var openedIndex = -1
    private var backs = 0
    private var renamedChild: FolderCard? = null
    private var deleteAskedChild: FolderCard? = null
    private var loadMoreCalls = 0

    private fun show(state: FolderState) {
        compose.setContent {
            VideoshotTheme {
                FolderScreen(
                    state = state,
                    loader = loader,
                    onBack = { backs++ },
                    onOpenChild = {},
                    onOpenShot = { openedIndex = it },
                    onLoadMore = { loadMoreCalls++ },
                    onStartCreateChild = {},
                    onStartRename = {},
                    onAskDeleteSelf = {},
                    onRenameChild = { renamedChild = it },
                    onAskDeleteChild = { deleteAskedChild = it },
                    onEditorName = {},
                    onConfirmEditor = {},
                    onDismissEditor = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                )
            }
        }
    }

    @Test
    fun 標題是資料夾名稱() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1)))
        compose.onNodeWithText("旅行").assertIsDisplayed()
    }

    @Test
    fun 上半是子資料夾下半是本層的圖() {
        show(
            FolderState(
                node = FolderNode(1, null, "旅行", 1),
                children = listOf(card(2, "宜蘭", 3)),
                items = listOf(shot(10), shot(11)),
                total = 2,
            )
        )
        compose.onNodeWithText("宜蘭").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("片段縮圖 01:05").assertCountEquals(2)
    }

    @Test
    fun 點圖回報第幾張() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1), items = listOf(shot(10), shot(11)), total = 2))
        compose.onAllNodesWithContentDescription("片段縮圖 01:05")[1].performClick()
        assertEquals(1, openedIndex)
    }

    /** 規格第六節：加入圖片只從 Lightbox 的【加入分類】，這一頁不提供挑圖介面。 */
    @Test
    fun 空的資料夾說得出圖要從哪裡加() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1)))
        compose.onNodeWithText("這個資料夾還沒有圖片").assertIsDisplayed()
        compose.onNodeWithText("在圖片的全屏檢視裡用【加入分類】把圖放進來").assertIsDisplayed()
    }

    @Test
    fun 第五層不給再建子資料夾並說明原因() {
        show(FolderState(node = FolderNode(9, 8, "第 5 層", 5)))
        compose.onNodeWithContentDescription("這個資料夾的更多操作").performClick()
        compose.onNodeWithText("新增子資料夾").assertIsNotEnabled()
        compose.onNodeWithText("已經是第 5 層，不能再往下分").assertIsDisplayed()
    }

    @Test
    fun 第四層還可以建子資料夾() {
        show(FolderState(node = FolderNode(9, 8, "第 4 層", 4)))
        compose.onNodeWithContentDescription("這個資料夾的更多操作").performClick()
        compose.onNodeWithText("新增子資料夾").assertIsEnabled()
    }

    @Test
    fun 返回鍵回得去清單頁() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1)))
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }

    /**
     * 裁決 2 的回歸測試：子資料夾卡片的【改名】要帶入子資料夾自己的名字與 id，
     * 不能誤觸到這一頁自己（父資料夾）的改名。
     */
    @Test
    fun 子資料夾卡片改名帶入子資料夾自己的名字() {
        show(
            FolderState(
                node = FolderNode(1, null, "旅行", 1),
                children = listOf(card(2, "宜蘭", 3)),
            )
        )
        compose.onNodeWithContentDescription("「宜蘭」的更多操作").performClick()
        compose.onNodeWithText("改名").performClick()
        assertEquals(2L, renamedChild?.id)
        assertEquals("宜蘭", renamedChild?.name)
    }

    /** 裁決 2 的回歸測試：子資料夾卡片的【刪除】要問的是那張子卡片，不是這一頁自己。 */
    @Test
    fun 子資料夾卡片刪除問的是子資料夾自己() {
        show(
            FolderState(
                node = FolderNode(1, null, "旅行", 1),
                children = listOf(card(2, "宜蘭", 3)),
            )
        )
        compose.onNodeWithContentDescription("「宜蘭」的更多操作").performClick()
        compose.onNodeWithText("刪除資料夾").performClick()
        assertEquals(2L, deleteAskedChild?.id)
        assertEquals("宜蘭", deleteAskedChild?.name)
    }

    /**
     * 審查 Important 3 的回歸測試：讀取失敗時要顯示錯誤列＋重試，不能被空狀態判斷式
     * 誤判成「這個資料夾是空的」——那是對使用者主動說錯話（原本的沉默 bug 修好後新引入的問題）。
     */
    @Test
    fun 讀取失敗顯示錯誤列不顯示資料夾是空的() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1), error = "載入失敗，請再試一次"))
        compose.onNodeWithText("載入失敗，請再試一次").assertIsDisplayed()
        compose.onNodeWithText("這個資料夾還沒有圖片").assertDoesNotExist()

        // 點〔重試〕要能再叫一次 onLoadMore——不管掛載當下的續載偵測（空清單一律判定「近底」，
        // 跟 HomeScreen 同一個算法）已經先自動打過幾次，點下去之後那一次一定要多算進去。
        val before = loadMoreCalls
        compose.onNodeWithText("重試").performClick()
        assertEquals(before + 1, loadMoreCalls)
    }
}
