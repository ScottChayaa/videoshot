package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 回歸（最終審查 I-1）：橫向／矮螢幕時第三步的主按鈕不能被抽屜擠成 0 高。
 *
 * 原本抽屜 Column 只有 `heightIn(max = …)`（下限被 coerceAtLeast(120.dp) 墊著），
 * 空間不夠時最後才量的 dock 按鈕被壓到消失。抽屜改成 `weight(1f, fill = false)` 之後，
 * 提示行與主按鈕先量，抽屜自己縮短並捲動。
 *
 * 外面包一段固定 150dp 的標頭，模擬外殼的頂欄＋步驟條。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w731dp-h411dp")
class Step3ShortScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun show(patch: DetailsPatch) {
        val details = (0..3).associateWith { ShotDetails("2026-01-01") }
        val state = Step3State(
            cells = details.keys.sorted().map { Step3Cell(it, it * 70.0, manual = false) },
            details = details,
            selected = setOf(0, 1),
            patch = patch,
            cropping = false,
        )
        compose.setContent {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.height(150.dp))
                Step3DetailsScreen(
                    state = state,
                    bitmapFor = { null },
                    onToggle = {},
                    onSelectAll = {},
                    onSelectNone = {},
                    onInvert = {},
                    onSelectUnapplied = {},
                    onEditEventDate = {},
                    onEditPlace = {},
                    onEditDescription = {},
                    onEditTags = {},
                    onApply = {},
                    onFinish = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Test
    fun 矮螢幕改過欄位時套用鈕仍看得到() {
        show(DetailsPatch(place = "沖繩"))
        compose.onNodeWithText("套用到 2 張").assertIsDisplayed()
    }

    @Test
    fun 矮螢幕沒改欄位時完成鈕仍看得到() {
        show(DetailsPatch())
        compose.onNodeWithText("完成").assertIsDisplayed()
    }
}
