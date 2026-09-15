package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 截不到時畫面要說什麼（規格第五節、手冊 §四第二步第 92 行）。
 * 重點是**廣告與黑畫面不能講成同一句話** —— 兩者的處置不同。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Step2CaptureUiTest {

    @get:Rule val compose = createComposeRule()

    private val source = FakeFrameSource.of(frameCount = 4, intervalSec = 10.0)

    private fun show(
        error: CaptureError?,
        onPickFromGallery: () -> Unit = {},
        onDismissCaptureError: () -> Unit = {},
    ) {
        compose.setContent {
            Step2GridScreen(
                state = Step2State(plan = source.plan, converging = false, hintSeen = true),
                bitmapFor = { source.bitmapOf(it) },
                haptics = FakeHaptics(),
                onToggle = {},
                onTakenTap = {},
                onPlayFrame = {},
                onSelectAll = {},
                onTakeShot = {},
                onShowAll = {},
                onOnlySelected = {},
                onDismissHint = {},
                onNext = {},
                captureError = error,
                onPickFromGallery = onPickFromGallery,
                onDismissCaptureError = onDismissCaptureError,
            )
        }
    }

    private fun showCell(fromGallery: Boolean, onNudge: (Int, Double) -> Unit = { _, _ -> }) {
        val plan = source.plan
        val cell = plan.frameCount
        compose.setContent {
            Step2GridScreen(
                state = Step2State(
                    plan = plan,
                    converging = false,
                    hintSeen = true,
                    ready = setOf(cell),
                    manual = listOf(ManualCell(cell, 20.0, java.io.File("/tmp/a.webp"), fromGallery)),
                ),
                bitmapFor = { null },
                haptics = FakeHaptics(),
                onToggle = {},
                onTakenTap = {},
                onPlayFrame = {},
                onSelectAll = {},
                onTakeShot = {},
                onShowAll = {},
                onOnlySelected = {},
                onDismissHint = {},
                onNext = {},
                onNudgeManual = onNudge,
            )
        }
    }

    @Test
    fun 相簿來的格子有正負1秒的微調鈕() {
        showCell(fromGallery = true)
        compose.onNodeWithContentDescription("往前 1 秒").assertIsDisplayed()
        compose.onNodeWithContentDescription("往後 1 秒").assertIsDisplayed()
    }

    @Test
    fun 截圖來的格子沒有微調鈕() {
        // 圖與秒數同一瞬間取的，給了微調鈕就是給錯誤的承諾（規格第五節）
        showCell(fromGallery = false)
        compose.onAllNodesWithContentDescription("往前 1 秒").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("往後 1 秒").assertCountEquals(0)
    }

    @Test
    fun 按微調鈕會帶著格號與秒數回報() {
        var got: Pair<Int, Double>? = null
        showCell(fromGallery = true, onNudge = { c, d -> got = c to d })
        compose.onNodeWithContentDescription("往後 1 秒").performClick()
        assertEquals(source.plan.frameCount to 1.0, got)
    }

    @Test
    fun 黑畫面時顯示這一格截不到並提供從相簿選() {
        show(CaptureError.BLACK_FRAME)
        compose.onNodeWithText("這一格截不到", substring = true).assertIsDisplayed()
        compose.onNodeWithText("從相簿選").assertIsDisplayed()
    }

    @Test
    fun 解不出圖與黑畫面同一種處置() {
        show(CaptureError.NOT_DECODABLE)
        compose.onNodeWithText("這一格截不到", substring = true).assertIsDisplayed()
        compose.onNodeWithText("從相簿選").assertIsDisplayed()
    }

    @Test
    fun 廣告播放中的說法不一樣() {
        // 廣告是暫時的：等一下再截就好，不必請使用者去翻相簿
        show(CaptureError.AD_PLAYING)
        compose.onNodeWithText("廣告", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("從相簿選").assertCountEquals(0)
    }

    @Test
    fun 存檔失敗不給相簿退路() {
        // 存不進去的話，從相簿選一張同樣存不進去
        show(CaptureError.SAVE_FAILED)
        compose.onNodeWithText("儲存空間", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("從相簿選").assertCountEquals(0)
    }

    @Test
    fun 沒有錯誤時不顯示提示() {
        show(null)
        compose.onAllNodesWithText("這一格截不到", substring = true).assertCountEquals(0)
    }

    @Test
    fun 按從相簿選會回呼() {
        var picked = false
        show(CaptureError.BLACK_FRAME, onPickFromGallery = { picked = true })
        compose.onNodeWithText("從相簿選").performClick()
        assertTrue(picked)
    }

    @Test
    fun 可以關掉提示() {
        var dismissed = false
        show(CaptureError.BLACK_FRAME, onDismissCaptureError = { dismissed = true })
        compose.onNodeWithContentDescription("關閉").performClick()
        assertTrue(dismissed)
    }
}
