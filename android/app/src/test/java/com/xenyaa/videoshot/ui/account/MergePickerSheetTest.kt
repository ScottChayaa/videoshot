package com.xenyaa.videoshot.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import com.xenyaa.videoshot.ui.icons.VsIcons
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
class MergePickerSheetTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 列出候選不含來源且點選回報id() {
        var picked: Long? = null
        compose.setContent {
            VideoshotTheme {
                MergePickerSheet(
                    sourceName = "礁溪",
                    candidates = listOf(
                        MergeCandidate(1, "宜蘭礁溪", 2, VsIcons.MapPin, Color.Red),
                        MergeCandidate(2, "花蓮", 1, VsIcons.MapPin, Color.Red),
                    ),
                    onPick = { picked = it },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("把「礁溪」合併到…").assertIsDisplayed()
        compose.onNodeWithText("宜蘭礁溪").assertIsDisplayed()
        compose.onNodeWithText("2 張").assertIsDisplayed()
        compose.onNodeWithText("花蓮").assertIsDisplayed()
        compose.onNodeWithText("礁溪").assertDoesNotExist()
        compose.onNodeWithText("宜蘭礁溪").performClick()
        assert(picked == 1L)
    }
}
