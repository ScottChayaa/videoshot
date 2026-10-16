package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class VsDateFieldTest {
    @get:Rule val compose = createComposeRule()

    private val picked = mutableListOf<String>()

    /** 月曆上的日期格；文字是完整日期（測試環境是英文「Saturday, August 15, 2026」，手機上是中文）。 */
    private fun day15() = compose.onNode(hasText(" 15, ", substring = true) and hasClickAction())

    private fun show(value: String, mixed: Boolean = false) {
        compose.setContent {
            VideoshotTheme { VsDateField("時間", value, onPick = { picked += it }, mixed = mixed) }
        }
    }

    @Test fun 顯示中文日期_整格是一顆按鈕() {
        show("2026-08-03")
        compose.onNodeWithText("時間").assertIsDisplayed()
        compose.onNodeWithContentDescription("時間：2026年8月3日").assertIsDisplayed()
    }

    @Test fun 點了開月曆_確定寫回原本那天() {
        show("2026-08-03")
        compose.onNodeWithContentDescription("時間：2026年8月3日").performClick()
        compose.onNodeWithText("確定").assertIsDisplayed().performClick()
        assertEquals(listOf("2026-08-03"), picked)
        compose.onNodeWithText("確定").assertDoesNotExist()
    }

    @Test fun 點月曆上另一天再確定() {
        show("2026-08-03")
        compose.onNodeWithContentDescription("時間：2026年8月3日").performClick()
        day15().performClick()
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf("2026-08-15"), picked)
    }

    @Test fun 取消不寫回() {
        show("2026-08-03")
        compose.onNodeWithContentDescription("時間：2026年8月3日").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(emptyList<String>(), picked)
        compose.onNodeWithText("確定").assertDoesNotExist()
    }

    @Test fun 多個值_顯示多個值_沒選日期前不能確定() {
        show("", mixed = true)
        compose.onNodeWithContentDescription("時間：〈多個值〉").performClick()
        compose.onNodeWithText("確定").assertIsNotEnabled()
        day15().performClick()
        compose.onNodeWithText("確定").assertIsEnabled().performClick()
        assertEquals(1, picked.size)
        assert(picked.single().endsWith("-15")) { picked.single() }
    }
}
