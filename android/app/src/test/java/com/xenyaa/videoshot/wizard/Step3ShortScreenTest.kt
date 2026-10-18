package com.xenyaa.videoshot.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 回歸：橫向／矮螢幕時第三步的欄位按鈕列與【完成】不能被縮圖牆擠成 0 高。
 * 底部列先量、縮圖牆吃剩下的高度（`weight(1f)`）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w731dp-h411dp")
class Step3ShortScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 矮螢幕欄位按鈕與完成鈕都看得到() {
        compose.setContent {
            VideoshotTheme {
                // 外面包一段固定 150dp 的標頭，模擬外殼的頂欄＋步驟條
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.height(150.dp))
                    Step3DetailsScreen(
                        state = Step3State(
                            cells = List(40) { Step3Cell(it, it * 10.0, false) },
                            details = (0 until 40).associateWith { ShotDetails("2026-01-01") },
                            selected = (0 until 40).toSet(),
                            cropping = false,
                        ),
                        bitmapFor = { null }, onToggle = {}, onSelectAll = {}, onApply = {}, onFinish = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        listOf("全選", "時間", "地點", "標籤", "描述").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onNodeWithText("完成").assertIsDisplayed()
    }
}
