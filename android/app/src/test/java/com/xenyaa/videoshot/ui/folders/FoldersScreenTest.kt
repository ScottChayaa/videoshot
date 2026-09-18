package com.xenyaa.videoshot.ui.folders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.folders.FolderSort
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
class FoldersScreenTest {

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

    private fun shot(id: Long) = ShotRow(
        id = id, videoId = "v1", atSec = 65.0, source = "storyboard", frameIndex = 1, sbLevel = 3,
        eventDate = "2026-03-01", place = null, description = null,
    )

    private fun card(id: Long, name: String, count: Int = 0, preview: List<ShotRow> = emptyList()) =
        FolderCard(id, name, count, lastActivityAt = 0, preview = preview)

    private var opened: FolderCard? = null
    private var created: String? = null
    private var renamed: Pair<Long, String>? = null
    private var deleted: FolderCard? = null
    private var sorted: FolderSort? = null

    /**
     * 用 `mutableStateOf`（不是純 `var`）——回呼在 composable 外的一般 lambda 裡跑，
     * 改純 `var` 不會讓 Compose 知道要重組，畫面會停在舊的一輪，對話框開了也看不到。
     */
    private var state by mutableStateOf(FoldersState())

    private fun show(initial: FoldersState) {
        state = initial
        compose.setContent {
            VideoshotTheme {
                FoldersScreen(
                    state = state,
                    loader = loader,
                    onOpen = { opened = it },
                    onCreate = { created = it },
                    onRename = { id, name -> renamed = id to name },
                    onDelete = { deleted = it },
                    onQuery = { state = state.copy(query = it) },
                    onEditorName = { state = FoldersStore.editName(state, it) },
                    onSearching = { state = FoldersStore.setSearching(state, it) },
                    onSort = { sorted = it },
                    onStartCreate = { state = FoldersStore.startCreate(state) },
                    onStartRename = { state = FoldersStore.startRename(state, it) },
                    onAskDelete = { state = FoldersStore.askDelete(state, it) },
                    onDismissEditor = { state = FoldersStore.closeEditor(state) },
                    onDismissDelete = { state = FoldersStore.closeDelete(state) },
                )
            }
        }
    }

    @Test
    fun 卡片有名稱與含子孫張數() {
        show(FoldersState(cards = listOf(card(1, "旅行", count = 12))))
        compose.onNodeWithText("旅行").assertIsDisplayed()
        compose.onNodeWithText("12 張").assertIsDisplayed()
    }

    /** 手冊 §六第一條：空的資料夾要說「還沒有圖片」，不是留一塊空白。 */
    @Test
    fun 空資料夾的卡片說還沒有圖片() {
        show(FoldersState(cards = listOf(card(1, "新的"))))
        compose.onNodeWithText("還沒有圖片").assertIsDisplayed()
    }

    @Test
    fun 有圖的卡片畫出預覽而不是那句話() {
        show(FoldersState(cards = listOf(card(1, "旅行", 2, listOf(shot(10), shot(11))))))
        compose.onNodeWithText("還沒有圖片").assertDoesNotExist()
    }

    @Test
    fun 點卡片會開那個資料夾() {
        val c = card(1, "旅行")
        show(FoldersState(cards = listOf(c)))
        compose.onNodeWithText("旅行").performClick()
        assertEquals(c, opened)
    }

    /** 手冊 §六第二條：右下有看得見的 `⋯`，不是只有長按。 */
    @Test
    fun 卡片上的更多可以改名() {
        show(FoldersState(cards = listOf(card(1, "旅行"))))
        compose.onNodeWithContentDescription("「旅行」的更多操作").performClick()
        compose.onNodeWithText("改名").performClick()
        // performTextReplacement 不靠游標位置，比 performTextInput 穩（控制端裁決第 2 點）
        compose.onNodeWithContentDescription("資料夾名稱").performTextReplacement("旅行 2")
        compose.onNodeWithText("儲存").performClick()
        assertEquals(1L to "旅行 2", renamed)
    }

    @Test
    fun 刪除確認說得出會連子資料夾一起刪且圖不動() {
        show(FoldersState(cards = listOf(card(1, "旅行")), deleting = card(1, "旅行")))
        compose.onNodeWithText("刪除「旅行」").assertIsDisplayed()
        compose.onNodeWithText("子資料夾會一起刪除，圖片不會被刪除。").assertIsDisplayed()
    }

    /** 手冊 §六第三條：沒有任何資料夾時，空狀態帶一顆【新增資料夾】。 */
    @Test
    fun 完全沒有資料夾時空狀態帶新增按鈕() {
        show(FoldersState(cards = emptyList()))
        compose.onNodeWithText("還沒有任何分類").assertIsDisplayed()
        compose.onNodeWithText("新增資料夾").performClick()
        compose.onNodeWithContentDescription("資料夾名稱").performTextInput("旅行")
        compose.onNodeWithText("建立").performClick()
        assertEquals("旅行", created)
    }

    @Test
    fun 篩掉光了給的是清除篩選() {
        show(FoldersState(cards = listOf(card(1, "旅行")), searching = true, query = "找不到的"))
        compose.onNodeWithText("沒有符合的分類").assertIsDisplayed()
        compose.onNodeWithText("清除篩選").assertIsDisplayed()
    }

    @Test
    fun 狀態列說得出數量與目前排序() {
        show(FoldersState(cards = listOf(card(1, "旅行"), card(2, "貓")), sort = FolderSort.RECENT))
        compose.onNodeWithText("2 個分類").assertIsDisplayed()
        compose.onNodeWithText("最近加入").assertIsDisplayed()
    }

    @Test
    fun 排序抽屜選得到另一種排序() {
        show(FoldersState(cards = listOf(card(1, "旅行"))))
        compose.onNodeWithText("名稱 A → Z").performClick()
        compose.onNodeWithText("張數多 → 少").performClick()
        assertEquals(FolderSort.COUNT_DESC, sorted)
    }

    @Test
    fun 對話框的錯誤訊息留在欄位旁邊() {
        show(FoldersState(editor = FolderEditor(null, "旅行", "同一層已經有「旅行」了")))
        compose.onNodeWithText("同一層已經有「旅行」了").assertIsDisplayed()
    }
}
