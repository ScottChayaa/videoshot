package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
class VsTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 顯示標題() {
        compose.setContent { VideoshotTheme { VsTopBar("收藏") } }
        compose.onNodeWithText("收藏").assertIsDisplayed()
    }

    /** 原型 --head-h：44 觸控鈕＋上下 8＋1 分隔線＝61，有沒有按鈕都同高。 */
    @Test fun 沒有按鈕也是61dp高() {
        compose.setContent { VideoshotTheme { VsTopBar("收藏", Modifier.testTag("bar")) } }
        compose.onNodeWithTag("bar").assertHeightIsEqualTo(61.dp)
    }

    @Test fun 返回鍵有名稱且會回呼() {
        var backed = false
        compose.setContent { VideoshotTheme { VsTopBar("資料夾", nav = TopBarNav.Back({ backed = true })) } }
        compose.onNodeWithContentDescription("返回").performClick()
        assertTrue(backed)
    }

    @Test fun 關閉鍵有名稱() {
        compose.setContent { VideoshotTheme { VsTopBar("批次編輯圖資", nav = TopBarNav.Close({})) } }
        compose.onNodeWithContentDescription("關閉").assertIsDisplayed()
    }

    @Test fun 右側動作鈕會顯示並回呼() {
        var opened = false
        compose.setContent {
            VideoshotTheme {
                VsTopBar("收藏") { TopBarIconButton(VsIcons.Calendar, "依時間篩選", { opened = true }) }
            }
        }
        compose.onNodeWithContentDescription("依時間篩選").performClick()
        assertTrue(opened)
    }
}
