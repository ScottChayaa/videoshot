package com.xenyaa.videoshot.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xenyaa.videoshot.ui.icons.VsIcons
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
class VsListRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 顯示主標與副標且可點() {
        var opened = false
        compose.setContent {
            VideoshotTheme { VsListRow("取圖", subtitle = "過濾相似強度：中", icon = VsIcons.Filter, onClick = { opened = true }) }
        }
        compose.onNodeWithText("過濾相似強度：中", substring = true).assertIsDisplayed()
        compose.onNodeWithText("取圖", substring = true).performClick()
        assertTrue(opened)
    }

    @Test fun 不可點的列沒有點擊動作() {
        compose.setContent { VideoshotTheme { VsListRow("版本", Modifier.testTag("r"), subtitle = "1.0") } }
        compose.onNodeWithTag("r").assertHasNoClickAction()
    }

    // 尾端元件只用 semantics { onClick } 標成可點、自己不 mergeDescendants（clickable 之類會自己當合併邊界，看不出差別）
    private val trailingTag = Modifier.semantics { role = Role.Button; onClick { true } }

    /** 預設合併語意：尾端元件的文字併進整列那一個節點，不是獨立節點。 */
    @Test fun 預設會合併子節點語意() {
        compose.setContent {
            VideoshotTheme { VsListRow("備份", trailing = { Text("開關", trailingTag) }) }
        }
        compose.onNodeWithText("備份").assert(hasTextList("備份", "開關"))
    }

    /** `mergeSemantics = false`：尾端的 Switch／按鈕自己是一個獨立、可點的節點。 */
    @Test fun 關掉合併後尾端元件是獨立可點節點() {
        compose.setContent {
            VideoshotTheme { VsListRow("備份", mergeSemantics = false, trailing = { Text("開關", trailingTag) }) }
        }
        compose.onNodeWithText("備份").assert(hasTextList("備份"))
        compose.onNodeWithText("開關").assertHasClickAction()
    }

    private fun hasTextList(vararg texts: String): SemanticsMatcher =
        SemanticsMatcher("文字清單恰為 ${texts.toList()}") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } == texts.toList()
        }
}
