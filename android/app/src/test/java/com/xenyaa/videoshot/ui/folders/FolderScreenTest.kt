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
            )
        )
        compose.onNodeWithText("宜蘭").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("片段縮圖 01:05").assertCountEquals(2)
    }

    @Test
    fun 點圖回報第幾張() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1), items = listOf(shot(10), shot(11))))
        compose.onAllNodesWithContentDescription("片段縮圖 01:05")[1].performClick()
        assertEquals(1, openedIndex)
    }

    /** 規格第六節：加入圖片只從 Lightbox 的【加入分類】，這一頁不提供挑圖介面。 */
    @Test
    fun 空的資料夾說得出圖要從哪裡加() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1)))
        compose.onNodeWithText("這個相簿還沒有圖片").assertIsDisplayed()
        compose.onNodeWithText("在圖片的全屏檢視裡用【加入相簿】把圖放進來").assertIsDisplayed()
    }

    /** 相簿裡不能再建子相簿：〔⋯〕只有重新命名與移除相簿。 */
    @Test
    fun 更多操作沒有新增子相簿() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1)))
        compose.onNodeWithContentDescription("這個相簿的更多操作").performClick()
        compose.onNodeWithText("重新命名").assertIsDisplayed()
        compose.onNodeWithText("移除相簿").assertIsDisplayed()
        compose.onNodeWithText("新增子資料夾").assertDoesNotExist()
        compose.onNodeWithText("新增子相簿").assertDoesNotExist()
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
        compose.onNodeWithText("重新命名").performClick()
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
        compose.onNodeWithText("移除相簿").performClick()
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
        compose.onNodeWithText("這個相簿還沒有圖片").assertDoesNotExist()

        // 點〔重試〕要能再叫一次 onLoadMore——不管掛載當下的續載偵測（空清單一律判定「近底」，
        // 跟 HomeScreen 同一個算法）已經先自動打過幾次，點下去之後那一次一定要多算進去。
        val before = loadMoreCalls
        compose.onNodeWithText("重試").performClick()
        assertEquals(before + 1, loadMoreCalls)
    }

    @Test
    fun 顯示麵包屑() {
        show(FolderState(node = FolderNode(2, 1, "加勒比海之旅", 2), breadcrumb = listOf("加勒比海之旅")))
        compose.onNodeWithText("相簿 / 加勒比海之旅").assertIsDisplayed()
    }

    @Test
    fun 多層麵包屑用斜線串起來() {
        show(FolderState(node = FolderNode(3, 2, "夜潛", 3), breadcrumb = listOf("旅行", "宜蘭", "夜潛")))
        compose.onNodeWithText("相簿 / 旅行 / 宜蘭 / 夜潛").assertIsDisplayed()
    }

    @Test
    fun 子資料夾是清單列且有更多操作() {
        show(
            FolderState(
                node = FolderNode(1, null, "旅行", 1),
                children = listOf(card(2, "夜潛", 7)),
            )
        )
        compose.onNodeWithText("夜潛").assertIsDisplayed()
        compose.onNodeWithText("7 張").assertIsDisplayed()
        compose.onNodeWithContentDescription("「夜潛」的更多操作").assertIsDisplayed()
    }

    @Test
    fun 點子資料夾整列會進入那個子資料夾() {
        var opened: FolderCard? = null
        compose.setContent {
            VideoshotTheme {
                FolderScreen(
                    state = FolderState(node = FolderNode(1, null, "旅行", 1), children = listOf(card(2, "夜潛", 7))),
                    loader = loader, onBack = {}, onOpenChild = { opened = it }, onOpenShot = {}, onLoadMore = {},
                    onStartRename = {}, onAskDeleteSelf = {}, onRenameChild = {},
                    onAskDeleteChild = {}, onEditorName = {}, onConfirmEditor = {}, onDismissEditor = {},
                    onConfirmDelete = {}, onDismissDelete = {},
                )
            }
        }
        compose.onNodeWithText("夜潛").performClick()
        assertEquals(2L, opened?.id)
    }

    @Test
    fun 有子資料夾沒有圖時仍顯示空狀態() {
        show(FolderState(node = FolderNode(1, null, "旅行", 1), children = listOf(card(2, "夜潛", 0))))
        compose.onNodeWithText("夜潛").assertIsDisplayed()
        compose.onNodeWithText("這個相簿還沒有圖片").assertIsDisplayed()
    }
}
