package com.xenyaa.videoshot.ui.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
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
                    placeSuggestions = listOf("花蓮"),
                    tagSuggestions = listOf("玩水"),
                    onDismiss = { dismissed++ },
                    onSave = { saved = it },
                )
            }
        }
    }

    @Test
    fun 帶入這一張現有的值() {
        show()
        compose.onNodeWithText("2026-03-05").assertIsDisplayed()
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
        compose.onNodeWithText("＋").performClick()
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

    @Test
    fun 改日期會送日期() {
        show()
        compose.onNodeWithContentDescription("時間").performTextReplacement("2026-01-09")
        compose.onNodeWithText("儲存").performClick()
        assertEquals(DetailsPatch(eventDate = "2026-01-09"), saved)
    }

    @Test
    fun 按取消不會存() {
        show()
        compose.onNodeWithContentDescription("地點").performTextReplacement("花蓮")
        compose.onNodeWithText("取消").performClick()
        assertEquals(null, saved)
        assertEquals(1, dismissed)
    }

    /** 時間欄位的說明是中性的，不是警告（手冊 §四第三步）。 */
    @Test
    fun 時間欄位有中性說明() {
        show()
        compose.onNodeWithText("預設帶入 YouTube 的上傳日期，可以改成實際拍攝日").assertIsDisplayed()
    }

    // ---- 時間欄位格式驗證：空白或打錯格式不能存進 library.db（案例：清空月份篩選找不到這張圖）----

    @Test
    fun 清空日期時儲存停用() {
        show()
        compose.onNodeWithContentDescription("時間").performTextClearance()
        compose.onNodeWithText("儲存").assertIsNotEnabled()
        compose.onNodeWithText("格式要是 YYYY-MM-DD，例如 2026-01-09").assertIsDisplayed()
    }

    @Test
    fun 日期格式不對時儲存停用() {
        show()
        compose.onNodeWithContentDescription("時間").performTextReplacement("3/5")
        compose.onNodeWithText("儲存").assertIsNotEnabled()
        compose.onNodeWithText("格式要是 YYYY-MM-DD，例如 2026-01-09").assertIsDisplayed()
    }

    @Test
    fun 日期格式正確時儲存可用() {
        show()
        compose.onNodeWithContentDescription("時間").performTextReplacement("2026-01-09")
        compose.onNodeWithText("儲存").assertIsEnabled()
    }
}
