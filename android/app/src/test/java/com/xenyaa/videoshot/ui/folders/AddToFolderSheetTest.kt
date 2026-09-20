package com.xenyaa.videoshot.ui.folders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.xenyaa.videoshot.data.repo.model.FolderNode
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
class AddToFolderSheetTest {

    @get:Rule val compose = createComposeRule()

    private var toggled: Pair<Long, Boolean>? = null
    private var created: String? = null

    private val tree = listOf(
        FolderNode(1, null, "旅行", 1),
        FolderNode(2, 1, "宜蘭", 2),
        FolderNode(3, null, "貓", 1),
    )

    private fun show(tree: List<FolderNode>, checked: Set<Long>) {
        compose.setContent {
            VideoshotTheme {
                AddToFolderSheet(
                    tree = tree,
                    checked = checked,
                    onToggle = { id, on -> toggled = id to on },
                    onCreate = { created = it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun 樹是父在前子在後同層照名稱() {
        assertEquals(
            listOf("旅行", "宜蘭", "貓"),
            flattenTree(listOf(FolderNode(3, null, "貓", 1), FolderNode(1, null, "旅行", 1), FolderNode(2, 1, "宜蘭", 2)))
                .map { it.name },
        )
    }

    @Test
    fun 已經在裡面的資料夾是勾起來的() {
        show(tree = tree, checked = setOf(2L))
        compose.onNodeWithContentDescription("宜蘭").assertIsOn()
        compose.onNodeWithContentDescription("旅行").assertIsOff()
    }

    @Test
    fun 勾起來就立刻回報要加入() {
        show(tree = tree, checked = emptySet())
        compose.onNodeWithContentDescription("旅行").performClick()
        assertEquals(1L to true, toggled)
    }

    @Test
    fun 取消勾選就立刻回報要移出() {
        show(tree = tree, checked = setOf(1L))
        compose.onNodeWithContentDescription("旅行").performClick()
        assertEquals(1L to false, toggled)
    }

    @Test
    fun 沒有任何分類時帶一顆新增資料夾() {
        show(tree = emptyList(), checked = emptySet())
        compose.onNodeWithText("還沒有任何分類").assertIsDisplayed()
        compose.onNodeWithText("新增資料夾").performClick()
        compose.onNodeWithContentDescription("資料夾名稱").performTextInput("旅行")
        compose.onNodeWithText("建立").performClick()
        assertEquals("旅行", created)
    }

    /** 「貓」排序在「旅行」前面時，子資料夾仍然要跟著自己的父層，不能被排到別人底下。 */
    @Test
    fun 子資料夾縮排在自己的父層底下() {
        show(tree = tree, checked = emptySet())
        // 同一個 sheet 裡，宜蘭要出現在旅行之後
        val labels = compose.onAllNodesWithTag("folderRow").fetchSemanticsNodes()
        assertEquals(3, labels.size)
    }
}
