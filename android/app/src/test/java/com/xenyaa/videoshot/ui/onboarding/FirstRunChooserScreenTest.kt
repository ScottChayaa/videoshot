package com.xenyaa.videoshot.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FirstRunChooserScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 兩個按鈕都顯示() {
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = {}) } }
        compose.onNodeWithText("從 Google Drive 還原").assertIsDisplayed()
        compose.onNodeWithText("全新開始").assertIsDisplayed()
    }

    @Test
    fun 點還原呼叫onRestoreClick() {
        var called = false
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = { called = true }, onStartFreshClick = {}) } }
        compose.onNodeWithText("從 Google Drive 還原").performClick()
        assert(called)
    }

    @Test
    fun 點全新開始呼叫onStartFreshClick() {
        var called = false
        compose.setContent { VideoshotTheme { FirstRunChooserScreen(onRestoreClick = {}, onStartFreshClick = { called = true }) } }
        compose.onNodeWithText("全新開始").performClick()
        assert(called)
    }
}
