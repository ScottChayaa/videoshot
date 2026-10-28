package com.xenyaa.videoshot.ui.clip

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaThumbLoader
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
class ClipExportScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun source(id: Long) = ClipSource(
        id = id, uri = "content://media/$id", kind = SourceKind.VIDEO, takenAtMs = 0,
        durationMs = 60_000, width = 1080, height = 1920, rotation = 0,
    )

    /** 第一段用素材 7、第二段用素材 8；總長 48 秒。 */
    private fun task(
        state: EditTaskState,
        progress: Int = 0,
        failReason: String? = null,
        skipped: Int = 0,
        galleryUri: String? = null,
        autoUpload: Boolean = true,
    ): EditTask {
        val timeline = Timeline(listOf(Segment(7, 0, 30_000), Segment(8, 0, 18_000)))
        return EditTask(
            id = 1, state = state, progress = progress, title = "我的旅行",
            draft = ClipDraft(ClipOrientation.PORTRAIT, listOf(source(7), source(8)), EditHistory(timeline)),
            autoUpload = autoUpload, failReason = failReason, skippedSources = skipped,
            galleryUri = galleryUri, createdAt = 0,
        )
    }

    private val loaded = mutableListOf<MediaItem>()
    private val loader = MediaThumbLoader(decode = { item -> loaded += item; null as ImageBitmap? })

    private fun show(
        task: EditTask,
        onAutoUploadChange: (Boolean) -> Unit = {},
        onCancel: () -> Unit = {},
        onRetry: () -> Unit = {},
        onPlay: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                ClipExportScreen(task, loader, onBack, onAutoUploadChange, onCancel, onRetry, onPlay)
            }
        }
    }

    private fun noNode(text: String) = compose.onAllNodes(hasText(text)).assertCountEquals(0)

    @Test
    fun 等待中顯示等待與勾選框和取消() {
        show(task(EditTaskState.QUEUED))

        compose.onNodeWithText("導出").assertIsDisplayed()
        compose.onNodeWithText("等待中…").assertIsDisplayed()
        compose.onNodeWithText("完成後自動上傳到 YouTube").assertIsDisplayed()
        compose.onNodeWithText("取消導出").assertIsDisplayed()
        noNode("輸出影片中…")
        noNode("0%")
        compose.onNodeWithTag("exportThumb").assertHasNoClickAction()
    }

    @Test
    fun 輸出中顯示百分比提示與勾選框() {
        show(task(EditTaskState.RENDERING, progress = 42))

        compose.onNodeWithText("輸出影片中…").assertIsDisplayed()
        compose.onNodeWithText("42%").assertIsDisplayed()
        // 大的百分比疊在縮圖上，縮圖本身就是進度（亮的部分到 42%）；輸出中不能點
        compose.onNode(hasText("42%") and hasAnyAncestor(hasTestTag("exportThumb"))).assertIsDisplayed()
        compose.onNodeWithTag("exportThumb").assertRangeInfoEquals(ProgressBarRangeInfo(0.42f, 0f..1f))
        compose.onNodeWithTag("exportThumb").assertHasNoClickAction()
        compose.onNodeWithText("留在這個畫面會比較快；離開也會在背景繼續").assertIsDisplayed()
        compose.onNodeWithText("完成後自動上傳到 YouTube").assertIsDisplayed()
        compose.onNodeWithText("取消導出").assertIsDisplayed()
    }

    @Test
    fun 完成顯示標題與長度和播放檢查且沒有勾選框() {
        var played = false
        show(task(EditTaskState.RENDERED), onPlay = { played = true })

        compose.onNodeWithText("影片輸出完成").assertIsDisplayed()
        compose.onNodeWithText("我的旅行・0:48").assertIsDisplayed()
        compose.onNodeWithText("播放檢查").performClick()
        assertEquals(true, played)
        noNode("完成後自動上傳到 YouTube")
        noNode("取消導出")
        noNode("已存到相簿")
        noNode("0%")
    }

    @Test
    fun 完成時點縮圖也全螢幕播放() {
        var played = 0
        show(task(EditTaskState.RENDERED), onPlay = { played++ })

        compose.onNodeWithTag("exportThumb").performClick()
        assertEquals(1, played)
    }

    @Test
    fun 存相簿後點縮圖也全螢幕播放() {
        var played = 0
        show(task(EditTaskState.RENDERED, galleryUri = "content://media/99"), onPlay = { played++ })

        compose.onNodeWithTag("exportThumb").performClick()
        assertEquals(1, played)
    }

    @Test
    fun 存相簿顯示已存到相簿與播放() {
        var played = false
        show(task(EditTaskState.RENDERED, galleryUri = "content://media/99"), onPlay = { played = true })

        compose.onNodeWithText("已存到相簿").assertIsDisplayed()
        compose.onNodeWithText("我的旅行・0:48").assertIsDisplayed()
        compose.onNodeWithText("播放").performClick()
        assertEquals(true, played)
        noNode("播放檢查")
        noNode("完成後自動上傳到 YouTube")
    }

    @Test
    fun 完成時有略過素材會說明() {
        show(task(EditTaskState.RENDERED, skipped = 2))

        compose.onNodeWithText("有 2 個素材已不存在，已略過").assertIsDisplayed()
    }

    @Test
    fun 失敗顯示原因與重試() {
        var retried = false
        // 重試之前殘留的略過個數與相簿位置，在失敗狀態不顯示
        show(
            task(EditTaskState.FAILED, failReason = "空間不足，還需要 1.2 GB", skipped = 3, galleryUri = "content://media/99"),
            onRetry = { retried = true },
        )

        compose.onNodeWithText("影片輸出失敗").assertIsDisplayed()
        compose.onNodeWithText("空間不足，還需要 1.2 GB").assertIsDisplayed()
        compose.onNodeWithText("重試").performClick()
        assertEquals(true, retried)
        compose.onNodeWithTag("exportThumb").assertHasNoClickAction()
        noNode("完成後自動上傳到 YouTube")
        noNode("取消導出")
        noNode("已存到相簿")
        noNode("有 3 個素材已不存在，已略過")
    }

    @Test
    fun 失敗沒有原因時寫輸出失敗() {
        show(task(EditTaskState.FAILED, failReason = null))

        compose.onNodeWithText("影片輸出失敗").assertIsDisplayed()
        compose.onNodeWithText("輸出失敗").assertIsDisplayed()
    }

    @Test
    fun 取消要先確認() {
        var cancelled = 0
        show(task(EditTaskState.RENDERING, progress = 10), onCancel = { cancelled++ })

        compose.onNodeWithText("取消導出").performClick()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()
        compose.onNodeWithText("已經輸出的部分會刪掉。").assertIsDisplayed()
        assertEquals(0, cancelled)

        // 選「繼續導出」：對話框關掉、不取消
        compose.onNodeWithText("繼續導出").performClick()
        noNode("取消導出？")
        assertEquals(0, cancelled)

        compose.onNodeWithText("取消導出").performClick()
        compose.onNode(hasText("取消導出") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, cancelled)
        noNode("取消導出？")
    }

    @Test
    fun 取消勾選回報false() {
        val changes = mutableListOf<Boolean>()
        show(task(EditTaskState.QUEUED, autoUpload = true), onAutoUploadChange = { changes += it })

        compose.onNodeWithText("完成後自動上傳到 YouTube").performClick()
        assertEquals(listOf(false), changes)
    }

    @Test
    fun 沒打勾時點一下回報true() {
        val changes = mutableListOf<Boolean>()
        show(task(EditTaskState.RENDERING, progress = 5, autoUpload = false), onAutoUploadChange = { changes += it })

        compose.onNodeWithText("完成後自動上傳到 YouTube").performClick()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun 縮圖取時間軸第一段的素材() {
        show(task(EditTaskState.RENDERING, progress = 5))
        compose.waitForIdle()

        assertEquals(listOf(7L), loaded.map { it.id })
    }

    @Test
    fun 返回鍵回報返回() {
        var back = false
        show(task(EditTaskState.QUEUED), onBack = { back = true })

        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(true, back)
    }
}
