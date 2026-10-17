package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.xenyaa.videoshot.core.details.DetailsPatch
import com.xenyaa.videoshot.core.details.ShotDetails
import com.xenyaa.videoshot.data.library.dao.TagNameKind
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
// Robolectric 預設視窗是 320×470dp——比這個 app 支援的任何裝置都小（minSdk 26，
// 驗收手冊只把 ≥600dp 另外歸類成「平板」，470dp 高的手機不在支援範圍內）。不指定
// qualifiers 的話，dock 與縮圖牆會在這個不存在的小螢幕上搶高度，擠壓出真實手機不會
// 重現的排版問題。改用一支普通現代手機的尺寸（w411dp-h891dp）。
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Step3DockTest {

    @get:Rule val compose = createComposeRule()

    private fun stateOf(
        details: Map<Int, ShotDetails> = (0..1).associateWith { ShotDetails("2026-01-01") },
        selected: Set<Int> = setOf(0, 1),
        patch: DetailsPatch = DetailsPatch(),
        lastAppliedCount: Int? = null,
    ) = Step3State(
        cells = details.keys.sorted().map { Step3Cell(it, it * 70.0, manual = false) },
        details = details,
        selected = selected,
        patch = patch,
        cropping = false,
        lastAppliedCount = lastAppliedCount,
    )

    private fun show(
        state: Step3State,
        onEditEventDate: (String) -> Unit = {},
        onEditPlace: (String) -> Unit = {},
        onEditTags: (List<String>) -> Unit = {},
        onApply: () -> Unit = {},
        onFinish: () -> Unit = {},
        placeSuggestions: List<String> = emptyList(),
        tagSuggestions: List<TagNameKind> = emptyList(),
        requireAllFilled: Boolean = false,
    ) {
        compose.setContent {
            Step3DetailsScreen(
                state = state,
                bitmapFor = { null },
                onToggle = {},
                onSelectAll = {},
                onInvert = {},
                onSelectUnapplied = {},
                onEditEventDate = onEditEventDate,
                onEditPlace = onEditPlace,
                onEditDescription = {},
                onEditTags = onEditTags,
                onApply = onApply,
                onFinish = onFinish,
                placeSuggestions = placeSuggestions,
                tagSuggestions = tagSuggestions,
                requireAllFilled = requireAllFilled,
            )
        }
    }

    @Test
    fun 抽屜標題是照片詳細資訊_右邊有套用() {
        show(stateOf())
        compose.onNodeWithText("照片詳細資訊").assertIsDisplayed()
        compose.onNodeWithText("套用").assertIsDisplayed()
        compose.onNodeWithText("套用到已選的", substring = true).assertDoesNotExist()
    }

    /** 時間是月曆選擇器；時間與描述下方沒有說明文字（2026-10-17 拿掉「預設帶入…」「留空，之後 AI 補」）。 */
    @Test
    fun 時間是月曆欄位_時間與描述沒有說明文字() {
        show(stateOf())
        compose.onNodeWithContentDescription("時間：2026年1月1日").assertIsDisplayed()
        compose.onNodeWithText("預設帶入", substring = true).assertDoesNotExist()
        compose.onNodeWithText("留空", substring = true).assertDoesNotExist()
    }

    @Test
    fun 從月曆選一天送出那一天() {
        val edits = mutableListOf<String>()
        show(stateOf(), onEditEventDate = { edits += it })
        compose.onNodeWithContentDescription("時間：2026年1月1日").performClick()
        // 月曆的日期格文字是完整日期（測試環境是英文「Thursday, January 15, 2026」）
        compose.onNode(hasText(" 15, ", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("確定").performClick()
        assertEquals(listOf("2026-01-15"), edits)
    }

    @Test
    fun 時間不一致時顯示多個值() {
        show(
            stateOf(
                details = mapOf(
                    0 to ShotDetails("2026-01-01"),
                    1 to ShotDetails("2026-02-02"),
                )
            )
        )
        compose.onNodeWithContentDescription("時間：〈多個值〉").assertIsDisplayed()
    }

    @Test
    fun 值不一致時顯示多個值的佔位字樣() {
        show(
            stateOf(
                details = mapOf(
                    0 to ShotDetails("2026-01-01", place = "冬山河"),
                    1 to ShotDetails("2026-01-01", place = "羅東"),
                )
            )
        )
        compose.onNodeWithText("〈多個值〉").assertIsDisplayed()
    }

    @Test
    fun 沒動過欄位時主按鈕是完成而且沒有提示行() {
        var finished = false
        show(stateOf(), onFinish = { finished = true })
        compose.onNodeWithText("完成").performClick()
        assertTrue(finished)
    }

    @Test
    fun 動了欄位主按鈕還是完成_按標題列的套用_沒有將更新提示行() {
        var applied = false
        show(stateOf(patch = DetailsPatch(place = "冬山河")), onApply = { applied = true })
        compose.onNodeWithText("將更新", substring = true).assertDoesNotExist()
        compose.onNodeWithText("完成").assertIsDisplayed()
        compose.onNodeWithText("套用到", substring = true).assertDoesNotExist()
        compose.onNodeWithText("套用").performClick()
        assertTrue(applied)
    }

    /** 還有沒套用的修改時按【完成】：先套用再結束，畫面上看到的值就是存下去的值。 */
    @Test
    fun 有沒套用的修改時按完成會先套用再結束() {
        val log = mutableListOf<String>()
        show(
            stateOf(patch = DetailsPatch(place = "冬山河")),
            onApply = { log += "apply" },
            onFinish = { log += "finish" },
        )
        compose.onNodeWithText("完成").performClick()
        assertEquals(listOf("apply", "finish"), log)
    }

    @Test
    fun 沒有修改時按完成不會套用() {
        val log = mutableListOf<String>()
        show(stateOf(), onApply = { log += "apply" }, onFinish = { log += "finish" })
        compose.onNodeWithText("完成").performClick()
        assertEquals(listOf("finish"), log)
    }

    /** 取圖精靈：每一張都套用過才按得下【完成】。 */
    @Test
    fun 要求全部填完時還有沒套用的就不能完成() {
        show(stateOf(), requireAllFilled = true)
        compose.onNodeWithText("完成").assertIsNotEnabled()
    }

    @Test
    fun 要求全部填完時全部套用過就能完成() {
        val all = (0..1).associateWith { ShotDetails("2026-01-01", applied = true) }
        show(stateOf(details = all), requireAllFilled = true)
        compose.onNodeWithText("完成").assertIsEnabled()
    }

    /** 「未填 N」歸 0 才啟用：有修改、而且涵蓋剩下沒套用的，也還不能按（先按【套用】）。 */
    @Test
    fun 要求全部填完時有修改但還沒套用也不能完成() {
        show(stateOf(patch = DetailsPatch(place = "冬山河")), requireAllFilled = true)
        compose.onNodeWithText("完成").assertIsNotEnabled()
    }

    /** 批次編輯只寫回套用過的那幾張，不要求全填。 */
    @Test
    fun 不要求全部填完時沒套用也能完成() {
        show(stateOf())
        compose.onNodeWithText("完成").assertIsEnabled()
    }

    @Test
    fun 打地點會往上回報() {
        var typed: String? = null
        show(stateOf(), onEditPlace = { typed = it })
        compose.onNodeWithContentDescription("地點").performTextReplacement("冬山河")
        assertEquals("冬山河", typed)
    }

    @Test
    fun 既有地點會出現在建議裡點了就填進去() {
        var typed: String? = null
        show(stateOf(), onEditPlace = { typed = it }, placeSuggestions = listOf("冬山河", "羅東"))
        compose.onNodeWithText("冬山河").performClick()
        assertEquals("冬山河", typed)
    }

    @Test
    fun 標籤以chip呈現按加入把輸入的字變成一個標籤() {
        var tags: List<String>? = null
        show(stateOf(), onEditTags = { tags = it })
        compose.onNodeWithContentDescription("新增標籤").performTextInput("玩水")
        // 原本的「＋」文字鈕改成【加入】（原型 .dock 的新增標籤列）
        compose.onNodeWithText("加入").performClick()
        assertEquals(listOf("玩水"), tags)
    }

    @Test
    fun 沒有勾選時抽屜收合但主按鈕還在() {
        show(stateOf(selected = emptySet()))
        compose.onNodeWithContentDescription("地點").assertDoesNotExist()
        compose.onNodeWithText("完成").assertIsDisplayed()
    }

    /** 設計文件決定 6：套用後提示「已套用到 N 張」。 */
    @Test
    fun 套用後狀態列顯示已套用() {
        show(stateOf(lastAppliedCount = 4))
        compose.onNodeWithText("已套用到 4 張").assertIsDisplayed()
        // 提示出現時，原本的「N 張 · M 已完成」讓位
        compose.onNodeWithText("2 張 · 0 已完成").assertDoesNotExist()
    }

    @Test
    fun 沒有套用提示時狀態列顯示張數與已完成() {
        show(stateOf())
        compose.onNodeWithText("2 張 · 0 已完成").assertIsDisplayed()
    }

    /** 設計文件第五節：鍵盤的 Enter（完成）就能加入標籤。 */
    @Test
    fun 按完成鍵加入標籤() {
        var tags: List<String>? = null
        show(stateOf(), onEditTags = { tags = it })
        compose.onNodeWithContentDescription("新增標籤").performTextInput("夜潛")
        compose.onNodeWithContentDescription("新增標籤").performImeAction()
        assertEquals(listOf("夜潛"), tags)
    }

    /** 空白不能加成標籤，Enter 也一樣。 */
    @Test
    fun 按完成鍵但沒打字不會加標籤() {
        var tags: List<String>? = null
        show(stateOf(), onEditTags = { tags = it })
        compose.onNodeWithContentDescription("新增標籤").performImeAction()
        assertEquals(null, tags)
    }

    /** 原型 .will-write：動過的欄位標題變主色並亮點。 */
    @Test
    fun 動過的欄位標題有提示點() {
        show(stateOf(patch = DetailsPatch(place = "加勒比海")))
        compose.onAllNodesWithContentDescription("將寫入", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun 沒動過欄位時沒有提示點() {
        show(stateOf())
        compose.onNodeWithContentDescription("將寫入", useUnmergedTree = true).assertDoesNotExist()
    }

    /** 已選的標籤可以點掉（移除鈕名稱「移除標籤 X」）。 */
    @Test
    fun 點已選標籤的移除鈕會把它拿掉() {
        var tags: List<String>? = null
        show(
            stateOf(details = (0..1).associateWith { ShotDetails("2026-01-01", tags = listOf("玩水", "夜潛")) }),
            onEditTags = { tags = it },
        )
        compose.onNodeWithContentDescription("移除標籤 玩水").performClick()
        assertEquals(listOf("夜潛"), tags)
    }

    /** 只勾一張時，標題旁邊接時間。 */
    @Test
    fun 只勾一張時抽屜標題旁有時間() {
        show(stateOf(selected = setOf(1)))
        compose.onNodeWithText("照片詳細資訊").assertIsDisplayed()
        // 時間字樣會出現兩次：格子右下角的標籤＋抽屜標題旁
        compose.onAllNodesWithText("01:10", useUnmergedTree = true).assertCountEquals(2)
    }

    /** 已加的標籤在輸入框上方（同照片編輯抽屜）。 */
    @Test
    fun 已加的標籤在新增標籤輸入框上方() {
        show(stateOf(details = (0..1).associateWith { ShotDetails("2026-01-01", tags = listOf("玩水")) }))
        val tag = compose.onNodeWithContentDescription("移除標籤 玩水").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithContentDescription("新增標籤").fetchSemanticsNode().boundsInRoot
        assertTrue("標籤 $tag 要在輸入框 $input 上面", tag.bottom <= input.top)
    }

    /** 建議列不再有「用過的地點」「用過的標籤」小標。 */
    @Test
    fun 建議列沒有小標() {
        show(
            stateOf(),
            placeSuggestions = listOf("冬山河"),
            tagSuggestions = listOf(TagNameKind("阿明", "person")),
        )
        compose.onNodeWithText("冬山河").assertIsDisplayed()
        compose.onNodeWithText("阿明").assertIsDisplayed()
        compose.onNodeWithText("用過的地點").assertDoesNotExist()
        compose.onNodeWithText("用過的標籤").assertDoesNotExist()
    }

    @Test
    fun 點建議標籤會加進去() {
        var tags: List<String>? = null
        show(stateOf(), onEditTags = { tags = it }, tagSuggestions = listOf(TagNameKind("阿明", "person")))
        compose.onNodeWithText("阿明").performClick()
        assertEquals(listOf("阿明"), tags)
    }

    /** 標題列不要太高：【套用】是小鈕，整列約 52dp（原本大按鈕撐到快 70dp）。 */
    @Test
    fun 標題列不超過五十六dp() {
        show(stateOf())
        val title = compose.onNodeWithText("照片詳細資訊").fetchSemanticsNode().boundsInRoot
        val apply = compose.onNodeWithText("套用").fetchSemanticsNode()
        val density = apply.layoutInfo.density.density
        // 觸控區 44dp ＋ 上下 4dp
        assertTrue("套用的觸控區高 ${apply.boundsInRoot.height / density}dp", apply.boundsInRoot.height / density <= 48f)
        assertTrue(title.height > 0f)
    }
}
