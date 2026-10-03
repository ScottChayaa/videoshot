package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
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
class VsEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    /** 手冊 §六：沒有資料夾時空狀態帶一顆【新增資料夾】，不是只有一句灰字。 */
    @Test fun 有動作時顯示按鈕() {
        var clicked = false
        compose.setContent {
            VideoshotTheme { VsEmptyState("還沒有任何分類", icon = VsIcons.Folder, actionText = "新增資料夾", onAction = { clicked = true }) }
        }
        compose.onNodeWithText("還沒有任何分類").assertIsDisplayed()
        compose.onNodeWithText("新增資料夾").performClick()
        assertTrue(clicked)
    }

    @Test fun 沒有動作就只有訊息() {
        compose.setContent { VideoshotTheme { VsEmptyState("這個時間以前沒有標籤") } }
        compose.onNodeWithText("這個時間以前沒有標籤").assertIsDisplayed()
    }

    @Test fun 第二行說明顯示在訊息下方() {
        compose.setContent { VideoshotTheme { VsEmptyState("還沒有收藏", secondary = "按下方的【取圖】開始") } }
        compose.onNodeWithText("還沒有收藏").assertIsDisplayed()
        compose.onNodeWithText("按下方的【取圖】開始").assertIsDisplayed()
        val msgBottom = compose.onNodeWithText("還沒有收藏").getUnclippedBoundsInRoot().bottom
        val secTop = compose.onNodeWithText("按下方的【取圖】開始").getUnclippedBoundsInRoot().top
        assertTrue(secTop >= msgBottom)
    }

    /** 動作鈕的文字與回呼必須成對，只給一個一定是呼叫端漏接線。 */
    @Test(expected = IllegalArgumentException::class) fun 只給動作文字不給回呼會拒絕() {
        compose.setContent { VideoshotTheme { VsEmptyState("沒有資料", actionText = "重試") } }
    }

    @Test(expected = IllegalArgumentException::class) fun 只給回呼不給動作文字會拒絕() {
        compose.setContent { VideoshotTheme { VsEmptyState("沒有資料", onAction = {}) } }
    }

    @Test fun 動作鈕可以指定次要樣式並仍然可點() {
        var clicked = false
        compose.setContent {
            VideoshotTheme {
                VsEmptyState("沒有結果", actionText = "清除條件", onAction = { clicked = true }, actionVariant = ButtonVariant.Secondary)
            }
        }
        compose.onNodeWithText("清除條件").performClick()
        assertTrue(clicked)
    }
}
