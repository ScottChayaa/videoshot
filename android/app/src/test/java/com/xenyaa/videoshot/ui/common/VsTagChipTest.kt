package com.xenyaa.videoshot.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.semantics.SemanticsProperties
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
        compose.setContent { VideoshotTheme { VsTagChip("夜潛", ChipKind.TOPIC, selected = true, isToggle = true, onClick = {}) } }
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

    /** 外框是唯一的語意節點：TalkBack 停在這一個，要有名字、選取狀態與點擊。 */
    @Test fun 可點的迷你外框節點帶有文字() {
        compose.setContent { VideoshotTheme { VsTagChip("加勒比海", ChipKind.PLACE, Modifier.testTag("m"), size = ChipSize.Mini, onClick = {}) } }
        compose.onNodeWithTag("m").assertTextContains("加勒比海", substring = true)
        compose.onAllNodesWithText("加勒比海", substring = true).assertCountEquals(1)
    }

    @Test fun 選取的可點迷你外框節點是選取狀態() {
        compose.setContent { VideoshotTheme { VsTagChip("加勒比海", ChipKind.PLACE, Modifier.testTag("m"), size = ChipSize.Mini, selected = true, isToggle = true, onClick = {}) } }
        compose.onNodeWithTag("m").assertIsSelected().assertTextContains("加勒比海", substring = true)
        compose.onAllNodesWithText("加勒比海", substring = true).assertCountEquals(1)
    }

    /** 動作／導覽型（首頁月份標籤）不是切換：沒有選取語意，TalkBack 不會唸「未勾選，核取方塊」。 */
    @Test fun 動作型的迷你外框沒有選取語意() {
        compose.setContent { VideoshotTheme { VsTagChip("加勒比海", ChipKind.PLACE, Modifier.testTag("m"), size = ChipSize.Mini, onClick = {}) } }
        compose.onNodeWithTag("m")
            .assertHasClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assertTextContains("加勒比海", substring = true)
    }

    @Test fun 動作型的一般尺寸沒有選取語意() {
        compose.setContent { VideoshotTheme { VsTagChip("夜潛", ChipKind.TOPIC, Modifier.testTag("c"), onClick = {}) } }
        compose.onNodeWithTag("c")
            .assertHasClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test fun 切換型未選取時有選取語意且為否() {
        compose.setContent { VideoshotTheme { VsTagChip("夜潛", ChipKind.TOPIC, Modifier.testTag("c"), isToggle = true, onClick = {}) } }
        compose.onNodeWithTag("c").assertIsNotSelected()
    }
}
