package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Robolectric 預設視窗是 320×470dp——比這個 app 支援的任何裝置都小（minSdk 26，
// 驗收手冊只把 ≥600dp 另外歸類成「平板」，470dp 高的手機不在支援範圍內）。不指定
// qualifiers 的話，dock 與縮圖牆會在這個不存在的小螢幕上搶高度，擠壓出真實手機不會
// 重現的排版問題。改用一支普通現代手機的尺寸（w411dp-h891dp）。
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Step3DockTest {

    @get:Rule val compose = createComposeRule()

    private fun stateOf(
        details: Map<Int, ShotDetails> = (0..1).associateWith { ShotDetails("2026-01-01") },
        selected: Set<Int> = setOf(0, 1),
        patch: DetailsPatch = DetailsPatch(),
    ) = Step3State(
        cells = details.keys.sorted().map { Step3Cell(it, it * 70.0, manual = false) },
        details = details,
        selected = selected,
        patch = patch,
        cropping = false,
    )

    private fun show(
        state: Step3State,
        onEditPlace: (String) -> Unit = {},
        onEditTags: (List<String>) -> Unit = {},
        onApply: () -> Unit = {},
        onFinish: () -> Unit = {},
        placeSuggestions: List<String> = emptyList(),
        tagSuggestions: List<String> = emptyList(),
    ) {
        compose.setContent {
            Step3DetailsScreen(
                state = state,
                bitmapFor = { null },
                onToggle = {},
                onSelectAll = {},
                onSelectNone = {},
                onInvert = {},
                onSelectUnapplied = {},
                onEditEventDate = {},
                onEditPlace = onEditPlace,
                onEditDescription = {},
                onEditTags = onEditTags,
                onApply = onApply,
                onFinish = onFinish,
                placeSuggestions = placeSuggestions,
                tagSuggestions = tagSuggestions,
            )
        }
    }

    @Test
    fun 抽屜標題說得出在編輯幾張() {
        show(stateOf())
        compose.onNodeWithText("套用到已選的 2 張").assertIsDisplayed()
    }

    @Test
    fun 時間欄位下方是中性說明不是警告() {
        show(stateOf())
        compose.onNodeWithText("預設帶入 YouTube 的上傳日期，可以改成實際拍攝日").assertIsDisplayed()
    }

    @Test
    fun 描述的佔位字樣說得出可以留空() {
        show(stateOf())
        compose.onNodeWithText("留空，之後 AI 補").assertIsDisplayed()
    }

    @Test
    fun 值不一致時顯示多個值的佔位字樣() {
        show(
            stateOf(
                details = mapOf(
                    0 to ShotDetails("2026-01-01", place = "冬山河"),
                    1 to ShotDetails("2026-01-01", place = "羅東"),
                )
            )
        )
        compose.onNodeWithText("〈多個值〉").assertIsDisplayed()
    }

    @Test
    fun 沒動過欄位時主按鈕是完成而且沒有提示行() {
        var finished = false
        show(stateOf(), onFinish = { finished = true })
        compose.onNodeWithText("完成").performClick()
        assertTrue(finished)
        // 節點根本不存在時要用 assertDoesNotExist —— assertIsNotDisplayed 會因為找不到節點而丟例外
        compose.onNodeWithContentDescription("將更新的欄位").assertDoesNotExist()
    }

    @Test
    fun 動了欄位主按鈕變成套用而且提示行出現() {
        var applied = false
        show(stateOf(patch = DetailsPatch(place = "冬山河")), onApply = { applied = true })
        compose.onNodeWithText("將更新：地點　其他欄位維持各張原值").assertIsDisplayed()
        compose.onNodeWithText("套用到 2 張").performClick()
        assertTrue(applied)
    }

    @Test
    fun 打地點會往上回報() {
        var typed: String? = null
        show(stateOf(), onEditPlace = { typed = it })
        compose.onNodeWithContentDescription("地點").performTextReplacement("冬山河")
        assertEquals("冬山河", typed)
    }

    @Test
    fun 既有地點會出現在建議裡點了就填進去() {
        var typed: String? = null
        show(stateOf(), onEditPlace = { typed = it }, placeSuggestions = listOf("冬山河", "羅東"))
        compose.onNodeWithText("冬山河").performClick()
        assertEquals("冬山河", typed)
    }

    @Test
    fun 標籤以chip呈現按加號把輸入的字變成一個標籤() {
        var tags: List<String>? = null
        show(stateOf(), onEditTags = { tags = it })
        compose.onNodeWithContentDescription("新增標籤").performTextInput("玩水")
        compose.onNodeWithText("＋").performClick()
        assertEquals(listOf("玩水"), tags)
    }

    @Test
    fun 沒有勾選時抽屜收合但主按鈕還在() {
        show(stateOf(selected = emptySet()))
        compose.onNodeWithContentDescription("地點").assertDoesNotExist()
        compose.onNodeWithText("完成").assertIsDisplayed()
    }
}
