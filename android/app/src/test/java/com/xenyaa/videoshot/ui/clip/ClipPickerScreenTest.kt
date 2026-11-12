package com.xenyaa.videoshot.ui.clip

import java.time.YearMonth
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.media.MediaAccess
import com.xenyaa.videoshot.media.MediaAccess.DENIED
import com.xenyaa.videoshot.media.MediaAccess.FULL
import com.xenyaa.videoshot.media.MediaAccess.PARTIAL
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaThumbLoader
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
class ClipPickerScreenTest {
    @get:Rule val compose = createComposeRule()

    private val NOV = YearMonth.of(2026, 11)
    private val OCT = YearMonth.of(2026, 10)

    private fun photo(id: Long) = MediaItem(id, "content://media/$id", MediaKind.PHOTO, 1_000L * id, null, 1080, 1920, 0)
    private fun video(id: Long, durationMs: Long, width: Int = 1080, height: Int = 1920, rotation: Int = 0) =
        MediaItem(id, "content://media/$id", MediaKind.VIDEO, 1_000L * id, durationMs, width, height, rotation)
    private fun items(n: Int) = (1..n).map { photo(it.toLong()) }

    private fun state(
        access: MediaAccess,
        filter: PickerFilter = PickerFilter.ALL,
        items: List<MediaItem>? = emptyList(),
        selected: List<Long> = emptyList(),
        selectedMs: Long = 0,
        months: List<YearMonth> = listOf(NOV, OCT),
    ) = ClipPickerState(access = access, filter = filter, items = items, selected = selected, selectedMs = selectedMs, months = months)

    private fun show(
        state: ClipPickerState,
        orientation: ClipOrientation = ClipOrientation.PORTRAIT,
        onRequestAccess: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        canAskAgain: Boolean = true,
        onToggle: (Long) -> Unit = {},
        onFilter: (PickerFilter) -> Unit = {},
        onNext: () -> Unit = {},
        onPreview: (MediaItem) -> Unit = {},
        onAutoPick: (YearMonth, AutoPick) -> Unit = { _, _ -> },
        onClear: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                ClipPickerScreen(
                    state = state,
                    orientation = orientation,
                    thumbLoader = MediaThumbLoader(decode = { null }),
                    onBack = {},
                    onFilter = onFilter,
                    onToggle = onToggle,
                    onPreview = onPreview,
                    onAutoPick = onAutoPick,
                    onClear = onClear,
                    onRequestAccess = onRequestAccess,
                    onOpenSettings = onOpenSettings,
                    canAskAgain = canAskAgain,
                    onNext = onNext,
                )
            }
        }
    }

    @Test fun 四個分頁與標題() {
        show(state(FULL))
        listOf("選素材", "全部", "直影片", "橫影片", "照片").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun 沒選時下一步停用() {
        show(state(FULL, selected = emptyList()))
        compose.onNodeWithText("下一步").assertIsNotEnabled()
    }

    @Test fun 選了三個顯示下一步3() {
        show(state(FULL, selected = listOf(1, 2, 3)))
        compose.onNodeWithText("下一步（3）").assertIsEnabled()
    }

    @Test fun 已選格子打勾_沒選的空圈() {
        show(state(FULL, items = items(3), selected = listOf(2)))
        val boxes = compose.onAllNodes(isToggleable())
        boxes.assertCountEquals(3)
        boxes[0].assertIsOff()
        boxes[1].assertIsOn()
        boxes[2].assertIsOff()
    }

    @Test fun 點圓框選取_點格子其他地方預覽() {
        val toggled = mutableListOf<Long>()
        val previewed = mutableListOf<Long>()
        show(state(FULL, items = items(2)), onToggle = { toggled += it }, onPreview = { previewed += it.id })
        compose.onAllNodes(isToggleable())[1].performClick()
        assertEquals(listOf(2L), toggled)
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        assertEquals(listOf(1L), previewed)
        assertEquals(listOf(2L), toggled)
    }

    @Test fun 快速選取先選月份再選方向_點了關抽屜() {
        val picks = mutableListOf<Pair<YearMonth, AutoPick>>()
        show(state(FULL, items = items(2)), onAutoPick = { m, p -> picks += m to p })
        compose.onNodeWithText("快速選取").performClick()
        compose.onNodeWithText("2026 年 11 月").assertIsDisplayed()
        compose.onNodeWithText("2026 年 10 月").performClick()
        compose.onNodeWithText("不分方向").assertIsDisplayed()
        compose.onNodeWithText("橫式素材").assertIsDisplayed()
        compose.onNodeWithText("直式素材").performClick()
        compose.waitForIdle()
        assertEquals(listOf(OCT to AutoPick.PORTRAIT), picks)
        compose.onNodeWithText("橫式素材").assertDoesNotExist()
    }

    @Test fun 選了月份後可以換月份() {
        show(state(FULL, items = items(2)))
        compose.onNodeWithText("快速選取").performClick()
        compose.onNodeWithText("2026 年 11 月").performClick()
        compose.onNodeWithText("換月份").performClick()
        compose.onNodeWithText("2026 年 10 月").assertIsDisplayed()
    }

    @Test fun 直式影片的快速選取直式排第一() {
        show(state(FULL, items = items(2)), orientation = ClipOrientation.PORTRAIT)
        compose.onNodeWithText("快速選取").performClick()
        compose.onNodeWithText("2026 年 11 月").performClick()
        val p = compose.onNodeWithText("直式素材").fetchSemanticsNode().boundsInRoot.top
        val any = compose.onNodeWithText("不分方向").fetchSemanticsNode().boundsInRoot.top
        val l = compose.onNodeWithText("橫式素材").fetchSemanticsNode().boundsInRoot.top
        assertTrue(p < any && any < l)
    }

    @Test fun 橫式影片的快速選取橫式排第一() {
        show(state(FULL, items = items(2)), orientation = ClipOrientation.LANDSCAPE)
        compose.onNodeWithText("快速選取").performClick()
        compose.onNodeWithText("2026 年 11 月").performClick()
        val l = compose.onNodeWithText("橫式素材").fetchSemanticsNode().boundsInRoot.top
        val any = compose.onNodeWithText("不分方向").fetchSemanticsNode().boundsInRoot.top
        val p = compose.onNodeWithText("直式素材").fetchSemanticsNode().boundsInRoot.top
        assertTrue(l < any && any < p)
    }

    @Test fun 有選取才有清除_點了回報() {
        var cleared = 0
        show(state(FULL, items = items(2), selected = listOf(1)), onClear = { cleared++ })
        compose.onNodeWithText("清除").performClick()
        assertEquals(1, cleared)
    }

    @Test fun 沒選取時沒有清除() {
        show(state(FULL, items = items(2)))
        compose.onNodeWithText("清除").assertDoesNotExist()
    }

    @Test fun 沒權限時沒有快速選取() {
        show(state(DENIED))
        compose.onNodeWithText("快速選取").assertDoesNotExist()
    }

    @Test fun 影片格顯示長度() {
        show(state(FULL, items = listOf(video(id = 1, durationMs = 75_000))))
        compose.onNodeWithText("1:15").assertIsDisplayed()
    }

    @Test fun 影片格標出直式或橫式_旋轉過的照轉後算_正方形不標() {
        show(
            state(
                FULL,
                items = listOf(
                    video(1, 10_000),
                    video(2, 10_000, width = 1920, height = 1080),
                    video(3, 10_000, width = 1920, height = 1080, rotation = 90),
                    video(4, 10_000, width = 1080, height = 1080),
                ),
            ),
        )
        compose.onAllNodesWithContentDescription("直式影片", substring = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription("橫式影片", substring = true).assertCountEquals(1)
    }

    @Test fun 底部顯示方向與已選總長() {
        show(state(FULL, selected = listOf(1, 2), selectedMs = 45_000))
        compose.onNodeWithText("直式").assertIsDisplayed()
        compose.onNodeWithText("0:45 / 3:00").assertIsDisplayed()
        compose.onNodeWithText("下一步（2）").assertIsEnabled()
    }

    @Test fun 直式超過3分鐘只警告_下一步還是可以按() {
        show(state(FULL, selected = listOf(1, 2), selectedMs = 192_000))
        compose.onNodeWithText("3:12 / 3:00・超過 12 秒").assertIsDisplayed()
        compose.onNodeWithText("下一步（2）").assertIsEnabled()
    }

    @Test fun 橫式上限10分鐘() {
        show(state(FULL, selected = listOf(1), selectedMs = 300_000), orientation = ClipOrientation.LANDSCAPE)
        compose.onNodeWithText("橫式").assertIsDisplayed()
        compose.onNodeWithText("5:00 / 10:00").assertIsDisplayed()
        compose.onNodeWithText("下一步（1）").assertIsEnabled()
    }

    @Test fun 剛好等於上限還可以下一步() {
        show(state(FULL, selected = listOf(1), selectedMs = 180_000))
        compose.onNodeWithText("3:00 / 3:00").assertIsDisplayed()
        compose.onNodeWithText("下一步（1）").assertIsEnabled()
    }

    @Test fun 部分允許顯示橫幅與選取更多() {
        var asked = false
        show(state(PARTIAL), onRequestAccess = { asked = true })
        compose.onNodeWithText("你只允許存取部分相片和影片").assertIsDisplayed()
        compose.onNodeWithText("選取更多").performClick()
        assertTrue(asked)
    }

    @Test fun 拒絕且還能再問顯示允許存取() {
        var asked = false
        show(state(DENIED), canAskAgain = true, onRequestAccess = { asked = true })
        compose.onNodeWithText("需要相片和影片的存取權").assertIsDisplayed()
        compose.onNodeWithText("videoshot 只會讀取你選的素材；上傳完成後，也可以選擇刪除它們").assertIsDisplayed()
        compose.onNodeWithText("允許存取").assertIsDisplayed().performClick()
        assertTrue(asked)
    }

    @Test fun 拒絕且不能再問顯示前往設定() {
        var opened = false
        show(state(DENIED), canAskAgain = false, onOpenSettings = { opened = true })
        compose.onNodeWithText("允許存取").assertDoesNotExist()
        compose.onNodeWithText("前往設定").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test fun 讀取中顯示骨架屏() {
        show(state(FULL, items = null))
        compose.onNodeWithContentDescription("載入中…").assertExists()
        compose.onNodeWithContentDescription("照片").assertDoesNotExist()
        compose.onNodeWithContentDescription("影片").assertDoesNotExist()
    }

    @Test fun 空的直影片分頁有說明() {
        show(state(FULL, filter = PickerFilter.PORTRAIT_VIDEO, items = emptyList()))
        compose.onNodeWithText("手機裡還沒有直式影片").assertIsDisplayed()
    }

    @Test fun 空的橫影片分頁有說明() {
        show(state(FULL, filter = PickerFilter.LANDSCAPE_VIDEO, items = emptyList()))
        compose.onNodeWithText("手機裡還沒有橫式影片").assertIsDisplayed()
    }

    @Test fun 空的全部分頁有說明() {
        show(state(FULL, filter = PickerFilter.ALL, items = emptyList()))
        compose.onNodeWithText("手機裡還沒有照片或影片").assertIsDisplayed()
    }

    @Test fun 空的照片分頁有說明() {
        show(state(FULL, filter = PickerFilter.PHOTO, items = emptyList()))
        compose.onNodeWithText("手機裡還沒有照片").assertIsDisplayed()
    }

    @Test fun 點圓框_點分頁_點下一步都回報給呼叫端() {
        val toggled = mutableListOf<Long>()
        val filters = mutableListOf<PickerFilter>()
        var next = 0
        show(
            state(FULL, items = items(2), selected = listOf(1)),
            onToggle = { toggled += it }, onFilter = { filters += it }, onNext = { next++ },
        )
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText("直影片").performClick()
        compose.onNodeWithText("橫影片").performClick()
        compose.onNodeWithText("下一步（1）").performClick()
        assertEquals(listOf(1L), toggled)
        assertEquals(listOf(PickerFilter.PORTRAIT_VIDEO, PickerFilter.LANDSCAPE_VIDEO), filters)
        assertEquals(1, next)
    }
}
