package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardSkeletonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun 第二步骨架有縮圖牆骨架與停用的底部按鈕() {
        compose.setContent { VideoshotTheme { WizardSkeleton(WizardStep.PICK, error = null, onRetry = {}) } }
        compose.onNodeWithContentDescription("正在載入縮圖…").assertIsDisplayed()
        compose.onNodeWithContentDescription("全選").assertIsNotEnabled()
        compose.onNodeWithContentDescription("截圖").assertIsNotEnabled()
        compose.onNodeWithText("下一步").assertIsNotEnabled()
    }

    @Test
    fun 第三步骨架的完成鈕停用() {
        compose.setContent { VideoshotTheme { WizardSkeleton(WizardStep.DETAILS, error = null, onRetry = {}) } }
        compose.onNodeWithContentDescription("正在載入縮圖…").assertIsDisplayed()
        compose.onNodeWithText("完成").assertIsNotEnabled()
        compose.onNodeWithContentDescription("地點").assertIsNotEnabled()
    }

    @Test
    fun 接不上時顯示原因與重試() {
        var retries = 0
        compose.setContent {
            VideoshotTheme { WizardSkeleton(WizardStep.PICK, error = "取圖需要網路。", onRetry = { retries++ }) }
        }
        compose.onNodeWithText("取圖需要網路。").assertIsDisplayed()
        compose.onNodeWithText("重試").performClick()
        assertEquals(1, retries)
    }
}
