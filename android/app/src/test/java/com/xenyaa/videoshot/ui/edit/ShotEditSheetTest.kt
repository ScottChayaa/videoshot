package com.xenyaa.videoshot.ui.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.data.library.dao.TagNameKind
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
class ShotEditSheetTest {

    @get:Rule val compose = createComposeRule()

    private var saved: DetailsPatch? = null
    private var dismissed = 0

    private val details = ShotDetails(
        eventDate = "2026-03-05",
        place = "宜蘭",
        description = "在海邊烤肉",
        tags = listOf("露營", "阿明"),
    )

    private fun show(value: ShotDetails = details) {
        compose.setContent {
            VideoshotTheme {
                ShotEditSheet(
                    details = value,
                    placeSuggestions = listOf("花蓮", "宜蘭", "花東縱谷"),
                    tagSuggestions = listOf(
                        TagNameKind("玩水", "topic"), TagNameKind("露營", "topic"),
                        TagNameKind("阿明", "person"), TagNameKind("夜潛", "other"),
                    ),
                    onDismiss = { dismissed++ },
                    onSave = { saved = it },
                )
            }
        }
    }

    @Test
    fun 帶入這一張現有的值() {
        show()
        compose.onNodeWithContentDescription("時間：2026年3月5日").assertIsDisplayed()
        compose.onNodeWithText("宜蘭").assertIsDisplayed()
        compose.onNodeWithText("在海邊烤肉").assertIsDisplayed()
        compose.onNodeWithText("露營").assertIsDisplayed()
        compose.onNodeWithText("阿明").assertIsDisplayed()
    }

    /** 沒動過任何欄位就沒有東西可存 —— 按鈕停用，避免無意義的寫入與 markChanged。 */
    @Test
    fun 沒動過時儲存停用() {
        show()
        compose.onNodeWithText("儲存").assertIsNotEnabled()
    }

    @Test
    fun 改了地點之後只送地點() {
        show()
        compose.onNodeWithContentDescription("地點").performTextReplacement("花蓮")
        compose.onNodeWithText("儲存").assertIsEnabled()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(DetailsPatch(place = "花蓮"), saved)
    }

    /** 空字串＝清空（`:core` 的約定），不是「沒動過」。 */
    @Test
    fun 清空地點送出的是空字串() {
        show()
        compose.onNodeWithContentDescription("地點").performTextClearance()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(DetailsPatch(place = ""), saved)
    }

    @Test
    fun 加一個標籤會整組送出並正規化() {
        show()
        compose.onNodeWithContentDescription("新增標籤").performTextInput("　玩水　")
        compose.onNodeWithText("加入").performClick()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(listOf("玩水", "露營", "阿明").sorted(), saved?.tags)
    }

    @Test
    fun 移除標籤也是整組送出() {
        show()
        compose.onNodeWithText("露營").performClick()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(listOf("阿明"), saved?.tags)
    }

    /** 時間是月曆選擇器：點欄位開月曆、點一天、【確定】才寫回（2026-10-17 由打字改成選擇器）。 */
    @Test
    fun 改日期會送日期() {
        show()
        compose.onNodeWithContentDescription("時間：2026年3月5日").performClick()
        // 月曆的日期格文字是完整日期（測試環境是英文「Monday, March 9, 2026」）
        compose.onNode(hasText(" 9, ", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("確定").performClick()
        compose.onNodeWithContentDescription("時間：2026年3月9日").assertIsDisplayed()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(DetailsPatch(eventDate = "2026-03-09"), saved)
    }

    @Test
    fun 月曆按取消日期不變() {
        show()
        compose.onNodeWithContentDescription("時間：2026年3月5日").performClick()
        compose.onNode(hasText(" 9, ", substring = true) and hasClickAction()).performClick()
        // 抽屜底部也有【取消】；月曆對話框在最上層，是最後一個
        compose.onAllNodesWithText("取消").onLast().performClick()
        compose.onNodeWithContentDescription("時間：2026年3月5日").assertIsDisplayed()
        compose.onNodeWithText("儲存").assertIsNotEnabled()
    }

    @Test
    fun 按取消不會存() {
        show()
        compose.onNodeWithContentDescription("地點").performTextReplacement("花蓮")
        compose.onNodeWithText("取消").performClick()
        assertEquals(null, saved)
        assertEquals(1, dismissed)
    }

    /** 時間與描述欄位下方沒有說明文字（2026-10-17 拿掉「預設帶入…」「留空，之後 AI 補」）。 */
    @Test
    fun 時間與描述沒有說明文字() {
        show()
        compose.onNodeWithText("預設帶入", substring = true).assertDoesNotExist()
        compose.onNodeWithText("留空", substring = true).assertDoesNotExist()
        compose.onNodeWithText("格式要是", substring = true).assertDoesNotExist()
    }

    // ---- 2026-10-12：候選即時過濾、新標籤選種類 ----

    @Test
    fun 打地點會即時過濾候選() {
        show()
        compose.onNodeWithText("花蓮").assertIsDisplayed()
        compose.onNodeWithContentDescription("地點").performTextReplacement("縱谷")
        compose.onNodeWithText("花東縱谷").assertIsDisplayed()
        compose.onNodeWithText("花蓮").assertDoesNotExist()
    }

    @Test
    fun 點地點候選會填進欄位() {
        show()
        compose.onNodeWithText("花蓮").performClick()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(DetailsPatch(place = "花蓮"), saved)
    }

    @Test
    fun 打標籤會即時過濾候選_已選的不列() {
        show()
        compose.onNodeWithText("玩水").assertIsDisplayed()
        compose.onNodeWithText("夜潛").assertIsDisplayed()
        compose.onNodeWithContentDescription("新增標籤").performTextInput("潛")
        compose.onNodeWithText("夜潛").assertIsDisplayed()
        compose.onNodeWithText("玩水").assertDoesNotExist()
    }

    @Test
    fun 新標籤加入後出現種類選擇_預設主題() {
        show()
        compose.onNodeWithContentDescription("新增標籤").performTextInput("海龜")
        compose.onNodeWithText("加入").performClick()
        compose.onNodeWithText("「海龜」是：").assertIsDisplayed()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(mapOf("海龜" to "topic"), saved?.newTagKinds)
    }

    @Test
    fun 新標籤可以改選種類() {
        show()
        compose.onNodeWithContentDescription("新增標籤").performTextInput("海龜")
        compose.onNodeWithText("加入").performClick()
        compose.onNodeWithText("動物").performClick()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(mapOf("海龜" to "pet"), saved?.newTagKinds)
        assertEquals(listOf("海龜", "露營", "阿明").sorted(), saved?.tags)
    }

    @Test
    fun 挑既有標籤不會出現種類選擇() {
        show()
        compose.onNodeWithText("玩水").performClick()
        compose.onNodeWithText("「玩水」是：").assertDoesNotExist()
        compose.onNodeWithText("儲存").performClick()
        assertEquals(emptyMap<String, String>(), saved?.newTagKinds)
    }

    @Test
    fun 移除新標籤就不送它的種類() {
        show()
        compose.onNodeWithContentDescription("新增標籤").performTextInput("海龜")
        compose.onNodeWithText("加入").performClick()
        compose.onNodeWithContentDescription("移除標籤 海龜").performClick()
        compose.onNodeWithText("「海龜」是：").assertDoesNotExist()
        compose.onNodeWithText("儲存").assertIsNotEnabled()
    }
}
