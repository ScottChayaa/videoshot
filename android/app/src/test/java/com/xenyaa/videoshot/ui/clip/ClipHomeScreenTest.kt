package com.xenyaa.videoshot.ui.clip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.timeline.ClipOrientation
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
class ClipHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(onNewClip: () -> Unit = {}) {
        compose.setContent { VideoshotTheme { ClipHomeScreen(onNewClip = onNewClip) } }
    }

    private fun showSheet(onPick: (ClipOrientation) -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent { VideoshotTheme { OrientationSheet(onPick = onPick, onDismiss = onDismiss) } }
    }

    @Test fun 空狀態有說明與新增剪輯() {
        show()
        compose.onNodeWithText("剪輯").assertIsDisplayed()
        compose.onNodeWithText("把手機裡的照片和影片剪成一支片").assertIsDisplayed()
        compose.onNodeWithText("上傳到你的 YouTube（非公開）後，就能用取圖精靈挑畫面").assertIsDisplayed()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test fun 點新增剪輯通知呼叫端() {
        var clicked = 0
        show(onNewClip = { clicked++ })
        compose.onNodeWithText("新增剪輯").performClick()
        assertEquals(1, clicked)
    }

    @Test fun 選方向抽屜兩列都有說明() {
        showSheet()
        compose.onNodeWithText("影片方向").assertIsDisplayed()
        compose.onNodeWithText("9:16・3 分鐘內會成為 Shorts").assertIsDisplayed()
        compose.onNodeWithText("16:9・最長 10 分鐘").assertIsDisplayed()
    }

    @Test fun 點直式回報直式() {
        var picked: ClipOrientation? = null
        showSheet(onPick = { picked = it })
        compose.onNodeWithText("直式").performClick()
        assertEquals(ClipOrientation.PORTRAIT, picked)
    }

    @Test fun 點橫式回報橫式() {
        var picked: ClipOrientation? = null
        showSheet(onPick = { picked = it })
        compose.onNodeWithText("橫式").performClick()
        assertEquals(ClipOrientation.LANDSCAPE, picked)
    }

    @Test fun 沒點之前不回報方向() {
        var picked: ClipOrientation? = null
        showSheet(onPick = { picked = it })
        assertNull(picked)
    }
}
