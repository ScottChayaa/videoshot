package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
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

    private fun setContent(state: AccountState, onOpenEditor: (PlaceUsage) -> Unit = {}) {
        compose.setContent {
            VideoshotTheme {
                PlaceManagementScreen(
                    state = state,
                    onBack = {}, onOpenEditor = onOpenEditor, onDismissEditor = {},
                    onEditName = {}, onEditAliases = {},
                    onRequestSave = {}, onConfirmMerge = {}, onDismissMerge = {},
                    onAskDelete = {}, onDismissDelete = {}, onConfirmDelete = {},
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
}
