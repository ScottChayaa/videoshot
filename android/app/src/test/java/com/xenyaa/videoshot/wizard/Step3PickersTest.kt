package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.details.Common
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.data.library.dao.TagNameKind
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
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
class Step3PickersTest {
    @get:Rule val compose = createComposeRule()

    private val confirmed = mutableListOf<DetailsPatch>()
    private var dismissed = 0

    private fun place(field: Common<String?>, suggestions: List<String> = emptyList()) = compose.setContent {
        VideoshotTheme { PlacePickerSheet(field, 2, suggestions, { confirmed += it }, { dismissed++ }) }
    }

    private fun tags(field: Common<List<String>>, suggestions: List<TagNameKind> = emptyList()) = compose.setContent {
        VideoshotTheme { TagPickerSheet(field, 2, suggestions, { confirmed += it }, { dismissed++ }) }
    }

    private fun description(field: Common<String?>) = compose.setContent {
        VideoshotTheme { DescriptionPickerSheet(field, 2, { confirmed += it }, { dismissed++ }) }
    }

    // ---- 地點 ----

    @Test fun 地點_帶出共同值_改了按確定送出新值() {
        place(Common.One("冬山河"))
        compose.onNodeWithText("冬山河").assertIsDisplayed()
        compose.onNodeWithContentDescription("地點名稱").performTextReplacement("羅東")
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(place = "羅東")), confirmed)
    }

    @Test fun 地點_沒動過按確定什麼都不送() {
        place(Common.One("冬山河"))
        compose.onNodeWithText("確定").performClick()
        assertTrue(confirmed.isEmpty())
        assertEquals(1, dismissed)
    }

    @Test fun 地點_多個值時輸入框空著_確定不能按() {
        place(Common.Mixed)
        compose.onNodeWithText("〈多個值〉").assertIsDisplayed()
        compose.onNodeWithText("確定").assertIsNotEnabled()
    }

    @Test fun 地點_點用過的地點填進輸入框() {
        place(Common.One(null), suggestions = listOf("冬山河", "羅東"))
        compose.onNodeWithText("羅東").performClick()
        compose.onNodeWithText("確定").assertIsEnabled().performClick()
        assertEquals(listOf(DetailsPatch(place = "羅東")), confirmed)
    }

    @Test fun 地點_前後空白會去掉() {
        place(Common.One(null))
        compose.onNodeWithContentDescription("地點名稱").performTextInput("  羅東 ")
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(place = "羅東")), confirmed)
    }

    @Test fun 地點_清除送空字串() {
        place(Common.Mixed)
        compose.onNodeWithText("清除").performClick()
        assertEquals(listOf(DetailsPatch(place = "")), confirmed)
    }

    @Test fun 地點_本來就都沒有時清除不能按() {
        place(Common.One(null))
        compose.onNodeWithText("清除").assertIsNotEnabled()
    }

    // ---- 標籤 ----

    @Test fun 標籤_加入後確定是整組取代() {
        tags(Common.One(listOf("露營")))
        compose.onNodeWithContentDescription("新增標籤").performTextInput("玩水")
        compose.onNodeWithText("加入").performClick()
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(tags = listOf("玩水", "露營"))), confirmed)
    }

    @Test fun 標籤_鍵盤完成鍵也是加入() {
        tags(Common.One(emptyList()))
        compose.onNodeWithContentDescription("新增標籤").performTextInput("玩水")
        compose.onNodeWithContentDescription("新增標籤").performImeAction()
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(tags = listOf("玩水"))), confirmed)
    }

    @Test fun 標籤_全部移除再確定是清除() {
        tags(Common.One(listOf("露營")))
        compose.onNodeWithContentDescription("移除標籤 露營", substring = true).performClick()
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(tags = emptyList())), confirmed)
    }

    @Test fun 標籤_多個值時沒動過按確定什麼都不送() {
        tags(Common.Mixed)
        compose.onNodeWithText("〈多個值〉").assertIsDisplayed()
        compose.onNodeWithText("確定").performClick()
        assertTrue(confirmed.isEmpty())
        assertEquals(1, dismissed)
    }

    @Test fun 標籤_點用過的標籤會加進去() {
        tags(Common.One(emptyList()), suggestions = listOf(TagNameKind("阿明", "person")))
        compose.onNodeWithText("阿明").performClick()
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(tags = listOf("阿明"))), confirmed)
    }

    @Test fun 標籤_清除送空清單() {
        tags(Common.Mixed)
        compose.onNodeWithText("清除").performClick()
        assertEquals(listOf(DetailsPatch(tags = emptyList())), confirmed)
    }

    // ---- 描述 ----

    @Test fun 描述_改了按確定送出() {
        description(Common.One(null))
        compose.onNodeWithContentDescription("描述").performTextInput("第一次下水")
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(description = "第一次下水")), confirmed)
    }

    @Test fun 描述_清空再確定是清除() {
        description(Common.One("舊的"))
        compose.onNodeWithContentDescription("描述").performTextReplacement("")
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf(DetailsPatch(description = "")), confirmed)
    }

    @Test fun 描述_多個值時沒動過按確定什麼都不送() {
        description(Common.Mixed)
        compose.onNodeWithText("確定").performClick()
        assertTrue(confirmed.isEmpty())
    }

    @Test fun 標題寫出要套用到幾張() {
        description(Common.One(null))
        compose.onNodeWithText("套用到已勾選的 2 張", substring = true).assertIsDisplayed()
    }
}
