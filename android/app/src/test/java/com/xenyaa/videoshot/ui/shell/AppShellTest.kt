package com.xenyaa.videoshot.ui.shell

import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.xenyaa.videoshot.ui.common.VsActionDock
import androidx.compose.ui.test.assertIsDisplayed
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

    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun show(nav: NavState = NavState(), onSelectTab: (Tab) -> Unit = {}) {
        compose.setContent {
            VideoshotTheme {
                AppShell(nav = nav, onSelectTab = onSelectTab) { tab -> Text("內容：${tab.label}") }
            }
        }
    }

    @Test
    fun 四格都有文字() {
        show()
        for (label in listOf("首頁", "取圖", "相簿", "帳號")) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun 點導覽會回報要切到哪一格() {
        var picked: Tab? = null
        show(onSelectTab = { picked = it })
        compose.onNodeWithText("相簿").performClick()
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
        compose.onNodeWithText("相簿").assertDoesNotExist()
        compose.onNodeWithText("首頁").assertDoesNotExist()
    }

    @Test
    fun 外殼畫得出_snackbar() {
        val host = androidx.compose.material3.SnackbarHostState()
        compose.setContent {
            VideoshotTheme {
                // 用 LaunchedEffect 而不是在測試裡 runBlocking：showSnackbar 會**一直掛著**
                // 直到那則訊息消失，在測試執行緒上等它回來，等到的一定是已經不見的畫面
                androidx.compose.runtime.LaunchedEffect(Unit) { host.showSnackbar("已新增 8 張") }
                AppShell(nav = NavState(), onSelectTab = {}, snackbarHostState = host) { Text("內容") }
            }
        }
        compose.onNodeWithText("已新增 8 張").assertIsDisplayed()
    }

    /**
     * 15A 最終審查 Minor 2：Scaffold 把導覽列 inset 換成 padding 給內容，但沒標記成已消耗，
     * 內容裡的 VsActionDock（自己 navigationBarsPadding）會再墊一次導覽列高度。
     * 先量沒有 inset 時 dock 的高度，再用 ViewCompat 派一個導覽列 inset，dock 高度不該變。
     */
    @Test
    fun 內容裡的動作列不重複墊導覽列() {
        var navBottomPx = 0
        compose.setContent {
            VideoshotTheme {
                AppShell(nav = NavState(), onSelectTab = {}) {
                    navBottomPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                    VsActionDock(Modifier.testTag("dock")) { Text("按鈕") }
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("dock").fetchSemanticsNode().size.height
        assertEquals(0, navBottomPx)

        val inset = 120 // px
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            ViewCompat.dispatchApplyWindowInsets(
                decor,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, inset))
                    .build(),
            )
        }
        compose.waitForIdle()
        // 守門：確認 inset 真的派進了 Compose，不然下面的相等斷言是空的
        assertEquals(inset, navBottomPx)
        val after = compose.onNodeWithTag("dock").fetchSemanticsNode().size.height
        assertEquals("導覽列 inset 已由外殼的 padding 處理，動作列不能再墊一次", before, after)
    }
}
