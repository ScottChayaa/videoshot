package com.xenyaa.videoshot.wizard

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
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
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Step3FieldBarTest {

    @get:Rule val compose = createComposeRule()

    private val applied = mutableListOf<DetailsPatch>()
    private var finished = 0
    private var selectAll = 0

    private fun stateOf(
        details: Map<Int, ShotDetails> = (0..1).associateWith { ShotDetails("2026-01-01") },
        selected: Set<Int> = setOf(0, 1),
    ) = Step3State(
        cells = details.keys.sorted().map { Step3Cell(it, it * 70.0, manual = false) },
        details = details,
        selected = selected,
        cropping = false,
    )

    private fun show(state: Step3State) = compose.setContent {
        VideoshotTheme {
            Step3DetailsScreen(
                state = state,
                bitmapFor = { null },
                onToggle = {},
                onSelectAll = { selectAll++ },
                onApply = { applied += it },
                onFinish = { finished++ },
                placeSuggestions = listOf("冬山河"),
            )
        }
    }

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test fun 底部是五顆欄位按鈕加完成_沒有舊的快捷列與抽屜() {
        show(stateOf())
        listOf("全選", "時間", "地點", "標籤", "描述").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onNodeWithText("完成").assertIsDisplayed()
        listOf("反選", "照片詳細資訊", "套用").forEach { compose.onAllNodesWithText(it, substring = true).assertCountEquals(0) }
        compose.onAllNodesWithText("已完成", substring = true).assertCountEquals(0)
    }

    @Test fun 時間一進來就打勾_地點沒有() {
        show(stateOf())
        compose.onNodeWithContentDescription("時間").assert(state("已完成"))
        compose.onNodeWithContentDescription("地點").assert(state("未完成"))
    }

    @Test fun 全選回報() {
        show(stateOf())
        compose.onNodeWithContentDescription("全選").performClick()
        assertEquals(1, selectAll)
    }

    @Test fun 沒勾圖片時按地點只跳提示() {
        show(stateOf(selected = emptySet()))
        compose.onNodeWithContentDescription("地點").performClick()
        compose.onNodeWithText("先勾選圖片").assertIsDisplayed()
        compose.onAllNodesWithText("地點 · 套用到", substring = true).assertCountEquals(0)
    }

    @Test fun 地點選擇器確定後回報修改() {
        show(stateOf())
        compose.onNodeWithContentDescription("地點").performClick()
        compose.onNodeWithContentDescription("地點名稱").performTextReplacement("羅東")
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(place = "羅東")), applied)
        // 確定之後抽屜關掉
        compose.onAllNodesWithText("地點 · 套用到", substring = true).assertCountEquals(0)
    }

    @Test fun 時間打開月曆_選一天回報那一天() {
        show(stateOf())
        compose.onNodeWithContentDescription("時間").performClick()
        compose.onNode(hasText(" 15, ", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(eventDate = "2026-01-15")), applied)
    }

    @Test fun 時間選回原本那天不回報() {
        show(stateOf(details = (0..1).associateWith { ShotDetails("2026-01-15") }))
        compose.onNodeWithContentDescription("時間").performClick()
        compose.onNodeWithText("確定").performClick()
        assertTrue(applied.isEmpty())
    }

    @Test fun 還沒每一張都有地點時按完成只跳提示() {
        show(stateOf())
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("每一張都要設定【時間】和【地點】才能完成").assertIsDisplayed()
        assertEquals(0, finished)
    }

    @Test fun 沒有任何圖片時按完成跳另一則提示_不會完成() {
        show(stateOf(details = emptyMap(), selected = emptySet()))
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("沒有圖片，回上一步挑幾張").assertIsDisplayed()
        compose.onAllNodesWithText("每一張都要設定【時間】和【地點】才能完成").assertCountEquals(0)
        assertEquals(0, finished)
    }

    @Test fun 每一張都有時間與地點就能完成() {
        show(stateOf(details = (0..1).associateWith { ShotDetails("2026-01-01", place = "冬山河") }))
        compose.onNodeWithText("完成").performClick()
        assertEquals(1, finished)
    }
}
