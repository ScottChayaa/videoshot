package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.ui.shell.Tab
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class VsBottomNavTest {
    @get:Rule val compose = createComposeRule()

    private fun show(current: Tab = Tab.HOME, initial: Char? = null, onSelect: (Tab) -> Unit = {}) {
        compose.setContent { VideoshotTheme { VsBottomNav(current, onSelect, initial) } }
    }

    @Test fun 五格都有文字() {
        show()
        for (label in listOf("首頁", "查詢", "取圖", "分類", "帳號")) compose.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun 目前分頁標成已選() {
        show(current = Tab.FOLDERS)
        compose.onNodeWithText("分類").assertIsSelected()
        compose.onNodeWithText("首頁").assertIsNotSelected()
    }

    @Test fun 點一格回報分頁() {
        var picked: Tab? = null
        show(onSelect = { picked = it })
        compose.onNodeWithText("查詢").performClick()
        assertEquals(Tab.SEARCH, picked)
    }

    /** 設計文件決定 4：連結後第五格顯示頭像字母，文字仍是「帳號」。 */
    @Test fun 有頭像字母就顯示字母() {
        show(initial = 'S')
        compose.onNodeWithText("S").assertIsDisplayed()
        compose.onNodeWithText("帳號").assertIsDisplayed()
    }

    @Test fun 沒連結不顯示任何字母() {
        show(initial = null)
        compose.onNodeWithText("S").assertDoesNotExist()
    }

    @Test fun 頭像字母取名稱首字大寫() {
        assertEquals('S', avatarInitialOf("scott lin"))
        assertEquals('林', avatarInitialOf("林小明"))
        assertNull(avatarInitialOf(""))
        assertNull(avatarInitialOf(null))
    }
}
