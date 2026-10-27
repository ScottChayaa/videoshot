package com.xenyaa.videoshot.ui.clip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ClipHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        onNewClip: () -> Unit = {},
        draft: ClipDraftSummary? = null,
        onOpenDraft: () -> Unit = {},
        onDiscardDraft: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                ClipHomeScreen(onNewClip = onNewClip, draft = draft, onOpenDraft = onOpenDraft, onDiscardDraft = onDiscardDraft)
            }
        }
    }

    private val summary = ClipDraftSummary(ClipOrientation.PORTRAIT, 4, 108_000)

    private fun showSheet(onPick: (ClipOrientation) -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent { VideoshotTheme { OrientationSheet(onPick = onPick, onDismiss = onDismiss) } }
    }

    @Test fun 空狀態有說明與新增剪輯() {
        show()
        compose.onNodeWithText("剪輯").assertIsDisplayed()
        compose.onNodeWithText("把手機裡的照片和影片剪成一支片").assertIsDisplayed()
        compose.onNodeWithText("上傳到你的 YouTube（非公開）後，就能用取圖精靈挑畫面").assertIsDisplayed()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test fun 點新增剪輯通知呼叫端() {
        var clicked = 0
        show(onNewClip = { clicked++ })
        compose.onNodeWithText("新增剪輯").performClick()
        assertEquals(1, clicked)
    }

    @Test fun 選方向抽屜兩列都有說明() {
        showSheet()
        compose.onNodeWithText("影片方向").assertIsDisplayed()
        compose.onNodeWithText("9:16・3 分鐘內會成為 Shorts").assertIsDisplayed()
        compose.onNodeWithText("16:9・最長 10 分鐘").assertIsDisplayed()
    }

    @Test fun 點直式回報直式() {
        var picked: ClipOrientation? = null
        showSheet(onPick = { picked = it })
        compose.onNodeWithText("直式").performClick()
        assertEquals(ClipOrientation.PORTRAIT, picked)
    }

    @Test fun 點橫式回報橫式() {
        var picked: ClipOrientation? = null
        showSheet(onPick = { picked = it })
        compose.onNodeWithText("橫式").performClick()
        assertEquals(ClipOrientation.LANDSCAPE, picked)
    }

    @Test fun 沒點之前不回報方向() {
        var picked: ClipOrientation? = null
        showSheet(onPick = { picked = it })
        assertNull(picked)
    }

    @Test fun 有草稿時最上面是編輯中() {
        show(draft = summary)
        compose.onNodeWithText("編輯中").assertIsDisplayed()
        compose.onNodeWithText("直式・4 個素材・1:48").assertIsDisplayed()
        // 有草稿時仍然看得到新增剪輯，空狀態的說明不再出現
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
        compose.onNodeWithText("把手機裡的照片和影片剪成一支片").assertDoesNotExist()
    }

    @Test fun 橫式草稿的副標寫橫式() {
        show(draft = ClipDraftSummary(ClipOrientation.LANDSCAPE, 2, 6_000))
        compose.onNodeWithText("橫式・2 個素材・0:06").assertIsDisplayed()
    }

    @Test fun 沒草稿時沒有編輯中() {
        show()
        compose.onNodeWithText("編輯中").assertDoesNotExist()
    }

    @Test fun 點編輯中列通知呼叫端續做() {
        var opened = 0
        show(draft = summary, onOpenDraft = { opened++ })
        compose.onNodeWithText("編輯中").performClick()
        assertEquals(1, opened)
    }

    @Test fun 放棄要確認() {
        var discarded = 0
        show(draft = summary, onDiscardDraft = { discarded++ })
        compose.onNodeWithContentDescription("編輯中的更多操作").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.onNodeWithText("放棄目前編輯中的剪輯？").assertIsDisplayed()
        compose.onNodeWithText("時間軸和復原紀錄都會清掉，手機裡的照片和影片不受影響。").assertIsDisplayed()
        assertEquals(0, discarded)

        compose.onNodeWithText("放棄").performClick()
        assertEquals(1, discarded)
        compose.onNodeWithText("放棄目前編輯中的剪輯？").assertDoesNotExist()
    }

    @Test fun 取消放棄不通知() {
        var discarded = 0
        show(draft = summary, onDiscardDraft = { discarded++ })
        compose.onNodeWithContentDescription("編輯中的更多操作").performClick()
        compose.onNodeWithText("放棄").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, discarded)
        compose.onNodeWithText("放棄目前編輯中的剪輯？").assertDoesNotExist()
    }

    @Test fun 長按編輯中列也能放棄() {
        var discarded = 0
        show(draft = summary, onDiscardDraft = { discarded++ })
        compose.onNodeWithText("編輯中").performTouchInput { longClick() }
        compose.onNodeWithText("放棄").performClick()
        compose.onNodeWithText("放棄目前編輯中的剪輯？").assertIsDisplayed()
        compose.onNodeWithText("放棄").performClick()
        assertEquals(1, discarded)
    }

    @Test fun 有草稿時新增剪輯先問放棄_確認後先放棄再新增() {
        val calls = mutableListOf<String>()
        show(draft = summary, onNewClip = { calls += "new" }, onDiscardDraft = { calls += "discard" })
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("放棄目前編輯中的剪輯？").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithText("放棄").performClick()
        assertEquals(listOf("discard", "new"), calls)
    }

    @Test fun 有草稿時新增剪輯取消後什麼都沒發生() {
        val calls = mutableListOf<String>()
        show(draft = summary, onNewClip = { calls += "new" }, onDiscardDraft = { calls += "discard" })
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText("編輯中").assertIsDisplayed()
    }
}
