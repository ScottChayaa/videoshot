package com.xenyaa.videoshot.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import com.xenyaa.videoshot.ui.testing.pixelsAround
import com.xenyaa.videoshot.ui.testing.ringPixels
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 手冊 §零：實體鍵盤走得到導覽五格，而且看得到焦點框。
 *
 * `NavigationBarItem` 把 focusable 藏在內部，外面只套得到容器 —— `focusRing` 判斷用 `hasFocus`
 * 就是為了這種元件（見 `FocusRingTest`）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ShellFocusTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputMode: InputModeManager

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun 導覽格拿到焦點會畫出框() {
        compose.setContent {
            inputMode = LocalInputModeManager.current
            VideoshotTheme {
                AppShell(nav = NavState(), onSelectTab = {}) { Text("內容") }
            }
        }
        val folders = compose.onNodeWithText("分類")
        val before = compose.pixelsAround(folders)

        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        folders.requestFocus()

        folders.assertIsFocused()
        val ring = ringPixels(before, compose.pixelsAround(folders))
        assertTrue("導覽格拿到焦點時要畫出焦點框（只找到 $ring 個框的像素）", ring > 50)
    }
}
