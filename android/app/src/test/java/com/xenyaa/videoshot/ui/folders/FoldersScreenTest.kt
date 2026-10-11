package com.xenyaa.videoshot.ui.folders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
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
import androidx.compose.ui.unit.dp
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

    private fun card(id: Long, name: String, count: Int = 0, preview: List<ShotRow> = emptyList(), archivedAt: Long? = null) =
        FolderCard(id, name, count, lastActivityAt = 0, preview = preview, archivedAt = archivedAt)

    private var opened: FolderCard? = null
    private var created: String? = null
    private var renamed: Pair<Long, String>? = null
    private var deleted: FolderCard? = null
    private var sorted: FolderSort? = null
    private var retryCalls = 0
    private var lastQuery: String? = null
    private var toggledArchive: FolderCard? = null
    private var openArchiveCalls = 0

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
                    onQuery = { lastQuery = it; state = state.copy(query = it) },
                    onEditorName = { state = FoldersStore.editName(state, it) },
                    onSearching = { state = FoldersStore.setSearching(state, it) },
                    onSort = { sorted = it },
                    onStartCreate = { state = FoldersStore.startCreate(state) },
                    onStartRename = { state = FoldersStore.startRename(state, it) },
                    onAskDelete = { state = FoldersStore.askDelete(state, it) },
                    onDismissEditor = { state = FoldersStore.closeEditor(state) },
                    onDismissDelete = { state = FoldersStore.closeDelete(state) },
                    onRetry = { retryCalls++ },
                    onToggleArchive = { toggledArchive = it },
                    onOpenArchive = { openArchiveCalls++ },
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
        compose.onNodeWithText("重新命名").performClick()
        // performTextReplacement 不靠游標位置，比 performTextInput 穩（控制端裁決第 2 點）
        compose.onNodeWithContentDescription("相簿名稱").performTextReplacement("旅行 2")
        compose.onNodeWithText("儲存").performClick()
        assertEquals(1L to "旅行 2", renamed)
    }

    @Test
    fun 移除確認說得出會連子相簿一起移除且圖不動() {
        show(FoldersState(cards = listOf(card(1, "旅行")), deleting = card(1, "旅行")))
        compose.onNodeWithText("刪除相簿「旅行」").assertIsDisplayed()
        compose.onNodeWithText("裡面的子相簿會一起移除，圖片不會被刪除。").assertIsDisplayed()
    }

    /** 手冊 §六第三條：沒有任何資料夾時，空狀態帶一顆【新增資料夾】。 */
    @Test
    fun 完全沒有資料夾時空狀態帶新增按鈕() {
        show(FoldersState(cards = emptyList()))
        compose.onNodeWithText("還沒有任何相簿").assertIsDisplayed()
        compose.onNodeWithText("新增相簿").performClick()
        compose.onNodeWithContentDescription("相簿名稱").performTextInput("旅行")
        compose.onNodeWithText("建立").performClick()
        assertEquals("旅行", created)
    }

    @Test
    fun 篩掉光了給的是清除篩選() {
        show(FoldersState(cards = listOf(card(1, "旅行")), searching = true, query = "找不到的"))
        compose.onNodeWithText("沒有符合的相簿").assertIsDisplayed()
        compose.onNodeWithText("清除篩選").assertIsDisplayed()
    }

    @Test
    fun 狀態列說得出數量與目前排序() {
        show(FoldersState(cards = listOf(card(1, "旅行"), card(2, "貓")), sort = FolderSort.RECENT))
        compose.onNodeWithText("2 個相簿").assertIsDisplayed()
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

    /**
     * N2 的回歸測試：`folderCards(null)` 讀取失敗時要顯示錯誤列＋重試，不能被空狀態判斷式
     * 誤判成「還沒有任何分類」——那是對使用者主動說錯話。跟資料夾頁的同一條回歸測試對稱
     * （`FolderScreenTest.讀取失敗顯示錯誤列不顯示資料夾是空的`）。
     */
    @Test
    fun 讀取失敗顯示錯誤列不顯示還沒有任何分類() {
        show(FoldersState(cards = emptyList(), error = "載入失敗，請再試一次"))
        compose.onNodeWithText("載入失敗，請再試一次").assertIsDisplayed()
        compose.onNodeWithText("還沒有任何相簿").assertDoesNotExist()

        compose.onNodeWithText("重試").performClick()
        assertEquals(1, retryCalls)
    }

    /** 15D：張數改到圖片區右下角的膠囊，名稱列不再顯示張數——同一張卡片「4 張」只會出現一次。 */
    @Test
    fun 張數在圖片區的膠囊上且只出現一次() {
        show(FoldersState(cards = listOf(card(1, "旅行", count = 4, preview = listOf(shot(10), shot(11), shot(12), shot(13))))))
        compose.onNodeWithText("4 張").assertIsDisplayed()
        compose.onAllNodesWithText("4 張").assertCountEquals(1)
        compose.onNodeWithText("旅行").assertIsDisplayed()
        // 膠囊在圖片區（名稱列上方），名稱列在膠囊下面
        // 卡片本身是 clickable（合併語意），要量各自位置得看未合併的樹
        val pill = compose.onNodeWithText("4 張", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val name = compose.onNodeWithText("旅行", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("張數膠囊應在名稱列上方：膠囊 bottom=${pill.bottom}、名稱 top=${name.top}", pill.bottom <= name.top)
    }

    @Test
    fun 排序抽屜的選中項回報為選取() {
        show(FoldersState(cards = listOf(card(1, "旅行")), sort = FolderSort.COUNT_DESC))
        compose.onNodeWithText("張數多 → 少").performClick() // 狀態列的排序鍵
        compose.onNode(hasText("張數多 → 少") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("名稱 A → Z") and isSelectable()).assertIsNotSelected()
        compose.onNode(hasText("最近加入") and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun 狀態列排序鍵的觸控區至少44dp() {
        show(FoldersState(cards = listOf(card(1, "旅行")), sort = FolderSort.RECENT))
        compose.onNode(hasText("最近加入") and hasClickAction()).assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun 沒有符合時清除篩選會回報清空查詢() {
        show(FoldersState(cards = listOf(card(1, "旅行")), searching = true, query = "找不到的"))
        compose.onNodeWithText("清除篩選").performClick()
        assertEquals("", lastQuery)
    }

    @Test
    fun 搜尋列的取消會收起() {
        show(FoldersState(cards = listOf(card(1, "旅行")), searching = true))
        compose.onNodeWithContentDescription("搜尋相簿名稱").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("搜尋相簿名稱").assertDoesNotExist()
    }

    /** 點〔搜尋〕整條頂欄換成搜尋框（標題與新增鈕收起來）；【取消】清掉搜尋字並回到原本的頂欄。 */
    @Test
    fun 點搜尋頂欄換成搜尋框_取消後復原() {
        show(FoldersState(cards = listOf(card(1, "旅行"), card(2, "宜蘭"))))
        compose.onNodeWithContentDescription("搜尋相簿").performClick()
        compose.onNodeWithContentDescription("搜尋相簿名稱").assertIsDisplayed()
        compose.onNodeWithContentDescription("新增相簿").assertDoesNotExist()

        compose.onNodeWithContentDescription("搜尋相簿名稱").performTextInput("宜")
        compose.onNodeWithText("符合 1 個").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除搜尋文字").assertIsDisplayed()

        compose.onNodeWithText("取消").performClick()
        assertEquals("", state.query)
        compose.onNodeWithContentDescription("搜尋相簿名稱").assertDoesNotExist()
        compose.onNodeWithContentDescription("新增相簿").assertIsDisplayed()
        compose.onNodeWithText("2 個相簿").assertIsDisplayed()
    }

    @Test
    fun 相簿畫面不列封存的相簿_數量也不算() {
        show(FoldersState(cards = listOf(card(1, "旅行"), card(2, "舊的", archivedAt = 100))))
        compose.onNodeWithText("旅行").assertIsDisplayed()
        compose.onNodeWithText("舊的").assertDoesNotExist()
        compose.onNodeWithText("1 個相簿").assertIsDisplayed()
    }

    @Test
    fun 卡片選單有封存相簿() {
        show(FoldersState(cards = listOf(card(1, "旅行"))))
        compose.onNodeWithContentDescription("「旅行」的更多操作").performClick()
        compose.onNodeWithText("封存相簿").performClick()
        assertEquals(1L, toggledArchive?.id)
    }

    /** 拉過門檻（80dp）再放手才進入。Robolectric 預設 mdpi，1px＝1dp；下拉阻尼約一半，拉 600px 夠過門檻。 */
    @Test
    fun 下拉過門檻放手進入封存相簿() {
        show(FoldersState(cards = listOf(card(1, "旅行"), card(3, "貓"))))
        compose.onNodeWithTag("folderGrid").performTouchInput {
            down(Offset(centerX, top + 20f))
            repeat(20) { moveBy(Offset(0f, 30f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(1, openArchiveCalls)
    }

    @Test
    fun 拉過門檻又滑回去放手不進入() {
        show(FoldersState(cards = listOf(card(1, "旅行"), card(3, "貓"))))
        compose.onNodeWithTag("folderGrid").performTouchInput {
            down(Offset(centerX, top + 20f))
            repeat(20) { moveBy(Offset(0f, 30f)) }
            repeat(20) { moveBy(Offset(0f, -30f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(0, openArchiveCalls)
    }

    /** 全部相簿都封存時相簿畫面是空狀態，還是要拉得進封存相簿（不然就找不回來了）。 */
    @Test
    fun 空狀態也能下拉進入封存相簿() {
        show(FoldersState(cards = listOf(card(2, "舊的", archivedAt = 100))))
        compose.onNodeWithText("還沒有任何相簿").performTouchInput {
            down(Offset(centerX, top))
            repeat(20) { moveBy(Offset(0f, 30f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(1, openArchiveCalls)
    }

    @Test
    fun 搜尋中下拉不會進入() {
        show(FoldersState(cards = listOf(card(1, "旅行"), card(3, "貓")), searching = true))
        compose.onNodeWithTag("folderGrid").performTouchInput {
            down(Offset(centerX, top + 20f))
            repeat(20) { moveBy(Offset(0f, 30f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(0, openArchiveCalls)
    }

    /** 手勢之外的入口：TalkBack 的自訂動作（官方範例也要求提供可及的替代入口）。 */
    @Test
    fun 有進入封存相簿的無障礙自訂動作() {
        show(FoldersState(cards = listOf(card(1, "旅行"))))
        // 掛在可聚焦的狀態列文字上（容器節點 TalkBack 聚焦不到）
        compose.onNodeWithText("1 個相簿").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == "進入封存相簿" }.action()
        assertEquals(1, openArchiveCalls)
    }

    /** 搜尋時跟下拉手勢一樣關掉：沒有任何節點帶這個自訂動作。 */
    @Test
    fun 搜尋中沒有進入封存相簿的無障礙自訂動作() {
        show(FoldersState(cards = listOf(card(1, "旅行")), searching = true))
        compose.onAllNodes(SemanticsMatcher("有自訂動作「進入封存相簿」") { n ->
            n.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "進入封存相簿" }
        }, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun 下拉提示文字跟著門檻換() {
        assertEquals("下拉進入封存相簿", archivePullLabel(ready = false))
        assertEquals("放開進入封存相簿", archivePullLabel(ready = true))
    }
}
