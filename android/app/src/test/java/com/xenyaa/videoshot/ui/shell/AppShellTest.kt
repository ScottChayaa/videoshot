package com.xenyaa.videoshot.ui.shell

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppShellTest {

    @get:Rule val compose = createComposeRule()

    private fun show(nav: NavState = NavState(), onSelectTab: (Tab) -> Unit = {}) {
        compose.setContent {
            VideoshotTheme {
                AppShell(nav = nav, onSelectTab = onSelectTab) { tab -> Text("內容：${tab.label}") }
            }
        }
    }

    @Test
    fun 五格都有文字() {
        show()
        for (label in listOf("首頁", "查詢", "取圖", "分類", "帳號")) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun 點導覽會回報要切到哪一格() {
        var picked: Tab? = null
        show(onSelectTab = { picked = it })
        compose.onNodeWithText("分類").performClick()
        assertEquals(Tab.FOLDERS, picked)
    }

    @Test
    fun 顯示目前分頁的內容() {
        show(NavState().select(Tab.ACCOUNT))
        compose.onNodeWithText("內容：帳號").assertIsDisplayed()
    }

    /** 手冊 §四：取圖三個步驟都沒有底部導覽。 */
    @Test
    fun 取圖分頁沒有底部導覽() {
        show(NavState().select(Tab.CAPTURE))
        compose.onNodeWithText("內容：取圖").assertIsDisplayed()
        compose.onNodeWithText("分類").assertDoesNotExist()
        compose.onNodeWithText("首頁").assertDoesNotExist()
    }
}
