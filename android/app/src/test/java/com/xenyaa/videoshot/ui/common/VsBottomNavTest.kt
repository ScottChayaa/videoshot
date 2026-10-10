package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
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

    @Test fun 四格都有文字且沒有查詢() {
        show()
        for (label in listOf("照片", "取圖", "相簿", "帳號")) compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("查詢").assertDoesNotExist()
    }

    @Test fun 目前分頁標成已選() {
        show(current = Tab.FOLDERS)
        compose.onNodeWithText("相簿").assertIsSelected()
        compose.onNodeWithText("照片").assertIsNotSelected()
    }

    @Test fun 點一格回報分頁() {
        var picked: Tab? = null
        show(onSelect = { picked = it })
        compose.onNodeWithText("相簿").performClick()
        assertEquals(Tab.FOLDERS, picked)
    }

    /** 設計文件決定 4：連結後第四格顯示頭像字母，文字仍是「帳號」。 */
    @Test fun 有頭像字母時帳號格的無障礙名稱仍只有帳號() {
        show(initial = 'S')
        // 字母只是視覺頭像（clearAndSetSemantics 清掉了它的語意，所以語意樹裡找不到字母本身）；
        // 重點是帳號格合併後的文字只剩「帳號」，TalkBack 不會唸成「S，帳號」
        compose.onNodeWithText("S", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("帳號").assertIsDisplayed().assertTextEquals("帳號")
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
