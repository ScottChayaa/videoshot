package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.ui.icons.VsIcons
import com.xenyaa.videoshot.ui.theme.Spacing
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 多選時取代底部導覽的動作列（照片頁、相簿畫面、封存相簿畫面共用）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class VsSelectionBarTest {
    @get:Rule val compose = createComposeRule()

    private var renameCalls = 0
    private var deleteCalls = 0

    private fun show(renameEnabled: Boolean) {
        compose.setContent {
            VideoshotTheme {
                VsSelectionBar(
                    listOf(
                        SelectionBarAction(VsIcons.Edit, "重新命名", { renameCalls++ }, enabled = renameEnabled),
                        SelectionBarAction(VsIcons.Trash, "刪除", { deleteCalls++ }),
                    ),
                )
            }
        }
    }

    @Test fun 每一格都是圖示加文字的按鈕() {
        show(renameEnabled = true)
        compose.onNodeWithText("重新命名").assertIsDisplayed().assertHasClickAction().assertIsEnabled()
        compose.onNodeWithText("刪除").performClick()
        assertEquals(1, deleteCalls)
    }

    @Test fun 不能用的格子帶停用語意且按了沒反應() {
        show(renameEnabled = false)
        compose.onNodeWithText("重新命名").assertIsNotEnabled()
        compose.onNodeWithText("重新命名").performClick()
        assertEquals(0, renameCalls)
        compose.onNodeWithText("刪除").assertIsEnabled()
    }

    /** 高度跟底部導覽一樣，兩者互換時畫面不跳。 */
    @Test fun 格子高度跟底部導覽一樣() {
        show(renameEnabled = true)
        compose.onNodeWithText("刪除").assertHeightIsEqualTo(Spacing.navHeight)
    }
}
