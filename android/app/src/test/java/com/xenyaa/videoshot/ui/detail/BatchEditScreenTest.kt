package com.xenyaa.videoshot.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.wizard.Step3Cell
import com.xenyaa.videoshot.wizard.Step3Store
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BatchEditScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun shot(id: Long, atSec: Double) = ShotRow(
        id = id, videoId = "v1", atSec = atSec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
        eventDate = "2026-03-01", place = null, description = null,
    )

    private fun readyState(): BatchEditState.Ready {
        val cells = listOf(Step3Cell(0, 10.0, manual = false))
        val store = Step3Store(cells, defaultEventDate = "2026-03-01")
        store.restore(details = mapOf(0 to ShotDetails(eventDate = "2026-03-01")), selected = setOf(0))
        return BatchEditState.Ready(store, listOf(shot(1, 10.0)), places = emptyList(), tags = emptyList())
    }

    private var closed = false

    private fun show(state: BatchEditState) {
        compose.setContent {
            VideoshotTheme {
                BatchEditScreen(
                    state = state,
                    bitmapFor = { null },
                    onToggle = {}, onSelectAll = {}, onSelectNone = {}, onInvert = {}, onSelectUnapplied = {},
                    onEditEventDate = {}, onEditPlace = {}, onEditDescription = {}, onEditTags = {},
                    onApply = {}, onFinish = {},
                    onClose = { closed = true },
                )
            }
        }
    }

    @Test
    fun 標題與關閉鈕都在() {
        show(readyState())
        compose.onNodeWithText("批次編輯圖資").assertIsDisplayed()
        compose.onNodeWithContentDescription("關閉").performClick()
        assert(closed)
    }

    @Test
    fun 讀取中顯示提示不是空白() {
        show(BatchEditState.Loading)
        compose.onNodeWithText("正在載入…").assertIsDisplayed()
    }

    @Test
    fun 錯誤狀態顯示提示() {
        show(BatchEditState.Error)
        compose.onNodeWithText("這支影片還沒有收藏，沒有東西可以編輯").assertIsDisplayed()
    }

    @Test
    fun 沒有動過欄位時主按鈕是完成() {
        show(readyState())
        compose.onNodeWithText("完成").assertIsDisplayed()
    }
}
