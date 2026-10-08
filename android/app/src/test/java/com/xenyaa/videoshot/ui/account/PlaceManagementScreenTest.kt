package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.data.repo.model.PlaceUsage
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PlaceManagementScreenTest {

    @get:Rule val compose = createComposeRule()

    private val places = listOf(
        PlaceUsage(1, "宜蘭礁溪", listOf("礁溪"), 2),
        PlaceUsage(2, "沒用到", emptyList(), 0),
    )

    private fun setContent(
        state: AccountState,
        onOpenEditor: (PlaceUsage) -> Unit = {},
        onStartMerge: () -> Unit = {},
        onConfirmMergeTarget: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                PlaceManagementScreen(
                    state = state,
                    onBack = {}, onOpenEditor = onOpenEditor, onDismissEditor = {},
                    onEditName = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = {}, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
                    onStartMerge = onStartMerge, onConfirmMergeTarget = onConfirmMergeTarget,
                )
            }
        }
    }

    @Test
    fun 列出全部地點與張數() {
        setContent(AccountState(places = places))
        compose.onNodeWithText("全部地點（2）").assertIsDisplayed()
        compose.onNodeWithText("宜蘭礁溪").assertIsDisplayed()
        compose.onNodeWithText("2 張").assertIsDisplayed()
        compose.onNodeWithText("沒用到").assertIsDisplayed()
        compose.onNodeWithText("0 張").assertIsDisplayed()
    }

    @Test
    fun 點一列會呼叫onOpenEditor() {
        var opened: PlaceUsage? = null
        setContent(AccountState(places = places), onOpenEditor = { opened = it })
        compose.onNodeWithText("宜蘭礁溪").performClick()
        assert(opened?.id == 1L)
    }

    @Test
    fun 刪除確認框說明圖會變成沒有地點() {
        setContent(AccountState(places = places, placeDeleting = places[0]))
        compose.onNodeWithText("刪除「宜蘭礁溪」").assertIsDisplayed()
        compose.onNodeWithText("這 2 張圖會變成沒有地點", substring = true).assertIsDisplayed()
        compose.onNodeWithText("改用【合併到…】", substring = true).assertIsDisplayed()
        compose.onNodeWithText("刪除").assertIsDisplayed()
    }

    @Test
    fun 編輯抽屜的合併到按鈕會呼叫onStartMerge() {
        var started = false
        setContent(AccountState(places = places, placeEditor = PlaceEditor(1, "宜蘭礁溪", "礁溪")), onStartMerge = { started = true })
        compose.onNodeWithText("合併到…").performClick()
        assert(started)
    }

    @Test
    fun 合併確認框說明圖與別名並可確認() {
        var confirmed = false
        val request = MergeRequest(MergeKind.PLACE, 3, "礁溪", 1)
        setContent(
            AccountState(places = places, mergeConfirm = MergeConfirm(request, 1, "宜蘭礁溪")),
            onConfirmMergeTarget = { confirmed = true },
        )
        compose.onNodeWithText("「礁溪」的 1 張圖會改成「宜蘭礁溪」，「礁溪」會變成它的別名，之後查「礁溪」一樣找得到。").assertIsDisplayed()
        compose.onNodeWithText("合併").performClick()
        assert(confirmed)
    }

    @Test
    fun 合併中顯示對話框且沒有按鈕() {
        setContent(AccountState(places = places, merging = true))
        compose.onNodeWithText("合併中…").assertIsDisplayed()
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.onNodeWithText("合併").assertDoesNotExist()
    }

    @Test
    fun 挑目標抽屜列出候選不含來源() {
        val request = MergeRequest(MergeKind.PLACE, 2, "沒用到", 0)
        setContent(AccountState(places = places, mergePicking = request))
        compose.onNodeWithText("把「沒用到」合併到…").assertIsDisplayed()
        // 候選（宜蘭礁溪）與底下清單各一列；來源「沒用到」同理清單一列、候選不含
        compose.onAllNodesWithText("宜蘭礁溪").assertCountEquals(2)
        compose.onAllNodesWithText("沒用到").assertCountEquals(1)
    }
}
