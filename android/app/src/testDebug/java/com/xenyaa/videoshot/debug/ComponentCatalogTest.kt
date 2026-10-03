package com.xenyaa.videoshot.debug

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
class ComponentCatalogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 總覽頁列出每一區() {
        compose.setContent { VideoshotTheme { ComponentCatalog() } }
        for (section in listOf("頂欄", "底部導覽", "標籤小膠囊", "按鈕", "清單列", "空狀態", "步驟條", "輸入欄", "底線分頁", "工具列小按鈕", "提示卡", "底部動作列", "下拉欄位", "設定群組", "單選列", "步進器")) {
            compose.onAllNodesWithText(section, useUnmergedTree = true).onFirst().assertExists()
        }
    }
}
