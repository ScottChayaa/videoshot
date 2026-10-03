package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class VsTagChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 顯示名稱與張數() {
        compose.setContent { VideoshotTheme { VsTagChip("加勒比海", ChipKind.PLACE, count = 4, onClick = {}) } }
        compose.onNodeWithText("加勒比海", substring = true).assertIsDisplayed()
        compose.onNodeWithText("4", substring = true).assertIsDisplayed()
    }

    /** 手冊 §五：選取後除了變色還多一個打勾。 */
    @Test fun 選取時多一個打勾() {
        compose.setContent { VideoshotTheme { VsTagChip("夜潛", ChipKind.TOPIC, selected = true, onClick = {}) } }
        compose.onNodeWithText("夜潛", substring = true).assertIsSelected()
        compose.onNodeWithContentDescription("已選", useUnmergedTree = true).assertExists()
    }

    @Test fun 可點的一般尺寸至少44高() {
        compose.setContent { VideoshotTheme { VsTagChip("夜潛", ChipKind.TOPIC, Modifier.testTag("c"), onClick = {}) } }
        compose.onNodeWithTag("c").assertHeightIsAtLeast(44.dp)
    }

    @Test fun 點一下會回呼() {
        var clicked = false
        compose.setContent { VideoshotTheme { VsTagChip("夜潛", ChipKind.TOPIC, onClick = { clicked = true }) } }
        compose.onNodeWithText("夜潛", substring = true).performClick()
        assertTrue(clicked)
    }

    @Test fun 迷你尺寸不可點() {
        compose.setContent { VideoshotTheme { VsTagChip("龍蝦", ChipKind.OTHER, Modifier.testTag("m"), size = ChipSize.Mini) } }
        compose.onNodeWithTag("m").assertHasNoClickAction()
    }

    /** 首頁月份標籤是迷你外觀但可點——觸控區仍要 44dp（手冊 §零）。 */
    @Test fun 可點的迷你尺寸觸控區至少44() {
        compose.setContent { VideoshotTheme { VsTagChip("加勒比海", ChipKind.PLACE, Modifier.testTag("m"), size = ChipSize.Mini, onClick = {}) } }
        compose.onNodeWithTag("m").assertHeightIsAtLeast(44.dp)
    }

    @Test fun 可點的迷你尺寸點一下會回呼() {
        var clicked = false
        compose.setContent { VideoshotTheme { VsTagChip("加勒比海", ChipKind.PLACE, size = ChipSize.Mini, onClick = { clicked = true }) } }
        compose.onNodeWithText("加勒比海", substring = true).performClick()
        assertTrue(clicked)
    }
}
