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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
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
        failedStep: EditTaskStep? = null,
        videoId: String? = null,
    ): EditTask {
        val timeline = Timeline(listOf(Segment(7, 0, 30_000), Segment(8, 0, 18_000)))
        return EditTask(
            id = 1, state = state, progress = progress, title = "我的旅行",
            draft = ClipDraft(ClipOrientation.PORTRAIT, listOf(source(7), source(8)), EditHistory(timeline)),
            autoUpload = autoUpload, failReason = failReason, skippedSources = skipped,
            galleryUri = galleryUri, createdAt = 0, failedStep = failedStep, videoId = videoId,
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

    // ── 上傳到 YouTube（階段 21）──

    @Test
    fun 上傳中標題與取消上傳() {
        var cancelled = 0
        show(task(EditTaskState.UPLOADING, progress = 63), onCancel = { cancelled++ })

        compose.onNodeWithText("上傳中…").assertIsDisplayed()
        // 縮圖上大字百分比（同輸出中的畫法），縮圖不能點
        compose.onNode(hasText("63%") and hasAnyAncestor(hasTestTag("exportThumb"))).assertIsDisplayed()
        compose.onNodeWithTag("exportThumb").assertRangeInfoEquals(ProgressBarRangeInfo(0.63f, 0f..1f))
        compose.onNodeWithTag("exportThumb").assertHasNoClickAction()
        // 勾選框只在等待中、輸出中出現
        noNode("完成後自動上傳到 YouTube")
        noNode("取消導出")

        compose.onNodeWithText("取消上傳").performClick()
        compose.onNodeWithText("取消上傳？").assertIsDisplayed()
        compose.onNodeWithText("已經上傳的部分會作廢。").assertIsDisplayed()
        assertEquals(0, cancelled)

        compose.onNodeWithText("繼續上傳").performClick()
        noNode("取消上傳？")
        assertEquals(0, cancelled)

        compose.onNodeWithText("取消上傳").performClick()
        compose.onNode(hasText("取消上傳") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun 等待明天上傳的導出中畫面() {
        var cancelled = 0
        show(
            task(EditTaskState.WAITING_QUOTA, failReason = "系統目前上傳已超過每日配額，請隔天再繼續上傳"),
            onCancel = { cancelled++ },
        )

        compose.onNodeWithText("等待明天上傳").assertIsDisplayed()
        compose.onNodeWithText("系統目前上傳已超過每日配額，請隔天再繼續上傳").assertIsDisplayed()
        compose.onNodeWithTag("exportThumb").assertHasNoClickAction()
        noNode("重試")
        noNode("完成後自動上傳到 YouTube")
        noNode("取消導出")

        compose.onNodeWithText("取消上傳").performClick()
        compose.onNodeWithText("取消上傳？").assertIsDisplayed()
        compose.onNodeWithText("已經上傳的部分會作廢。").assertIsDisplayed()
        compose.onNode(hasText("取消上傳") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun 頻道上限的等待原因() {
        show(task(EditTaskState.WAITING_QUOTA, failReason = "這個頻道今天的上傳次數已達上限，請隔天再繼續上傳"))
        compose.onNodeWithText("等待明天上傳").assertIsDisplayed()
        compose.onNodeWithText("這個頻道今天的上傳次數已達上限，請隔天再繼續上傳").assertIsDisplayed()
    }

    @Test
    fun 處理中的小字與取消任務() {
        var cancelled = 0
        show(task(EditTaskState.PROCESSING, videoId = "abc"), onCancel = { cancelled++ })

        compose.onNodeWithText("YouTube 處理中…").assertIsDisplayed()
        compose.onNodeWithText("等 YouTube 產生縮圖，通常幾分鐘內完成").assertIsDisplayed()
        compose.onNodeWithTag("exportThumb").assertHasNoClickAction()
        noNode("完成後自動上傳到 YouTube")
        noNode("取消導出")
        noNode("取消上傳")

        compose.onNodeWithText("取消任務").performClick()
        compose.onNodeWithText("取消這個任務？").assertIsDisplayed()
        compose.onNodeWithText("影片仍在你的 YouTube 頻道，可以到 YouTube 工作室刪除。").assertIsDisplayed()
        assertEquals(0, cancelled)

        compose.onNodeWithText("繼續等待").performClick()
        noNode("取消這個任務？")
        assertEquals(0, cancelled)

        compose.onNodeWithText("取消任務").performClick()
        compose.onNode(hasText("取消任務") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun 已完成的文字() {
        show(task(EditTaskState.DONE, videoId = "abc"))

        compose.onNodeWithText("已上傳到 YouTube（非公開）").assertIsDisplayed()
        compose.onNodeWithText("我的旅行・0:48").assertIsDisplayed()
        compose.onNodeWithText("開始取圖在下一個階段加入").assertIsDisplayed()
        compose.onNodeWithTag("exportThumb").assertHasNoClickAction()
        noNode("完成後自動上傳到 YouTube")
        noNode("取消任務")
        noNode("取消上傳")
        noNode("播放檢查")
    }

    private fun failedShows(step: EditTaskStep?, title: String) {
        show(task(EditTaskState.FAILED, failReason = "某個原因", failedStep = step))
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("某個原因").assertIsDisplayed()
        compose.onNodeWithText("重試").assertIsDisplayed()
    }

    @Test fun 失敗標題依步驟_輸出() = failedShows(EditTaskStep.RENDER, "影片輸出失敗")

    @Test fun 失敗標題依步驟_上傳() = failedShows(EditTaskStep.UPLOAD, "影片上傳失敗")

    @Test fun 失敗標題依步驟_處理() = failedShows(EditTaskStep.STORYBOARD, "YouTube 處理失敗")

    @Test fun 舊資料沒記步驟的失敗當成輸出() = failedShows(null, "影片輸出失敗")

    @Test
    fun 取消確認框開著時輸出轉成上傳_框消失() {
        val shown = androidx.compose.runtime.mutableStateOf(task(EditTaskState.RENDERING, progress = 99))
        var cancelled = 0
        compose.setContent { VideoshotTheme { ClipExportScreen(shown.value, loader, {}, {}, { cancelled++ }, {}, {}) } }
        compose.onNodeWithText("取消導出").performClick()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()

        shown.value = task(EditTaskState.UPLOADING, progress = 1)
        compose.waitForIdle()

        noNode("取消導出？")
        noNode("取消上傳？")
        assertEquals(0, cancelled)
    }

    // ── 導覽入口（ClipExportRoute）：勾選框打勾前先授權 ──

    private val repo = FakeCacheRepo()
    private val routeDeps = object : ClipDeps {
        override val mediaLibrary = object : MediaLibrary {
            override suspend fun list(filter: MediaFilter): List<MediaItem> = emptyList()
        }
        override val mediaThumbLoader = loader
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = emptySet<String>()
        override val cacheRepo: CacheRepo = repo
        override fun now() = 500L
    }

    private fun showRoute(autoUpload: Boolean, granted: UploadAuthResult, authorizeCalls: MutableList<Unit>, notices: MutableList<String>): Long {
        val id = runBlocking {
            repo.startEditTask(newClipDraft(ClipOrientation.PORTRAIT, listOf(source(7))), "我的旅行", autoUpload, 100)
        }
        compose.setContent {
            VideoshotTheme {
                ClipExportRoute(
                    taskId = id, deps = routeDeps, scope = rememberCoroutineScope(),
                    onBack = {}, onGone = {}, onPlay = {},
                    authorize = { authorizeCalls += Unit; granted },
                    onNotice = { notices += it },
                )
            }
        }
        return id
    }

    private fun autoUploadOf(id: Long) = runBlocking { repo.editTask(id).first()!!.autoUpload }

    @Test
    fun 授權被拒時勾選框維持沒勾並提示() {
        val calls = mutableListOf<Unit>()
        val notices = mutableListOf<String>()
        val id = showRoute(autoUpload = false, granted = UploadAuthResult.DECLINED, authorizeCalls = calls, notices = notices)

        compose.onNodeWithText("完成後自動上傳到 YouTube").performClick()
        compose.waitForIdle()

        assertEquals(1, calls.size)
        assertEquals(false, autoUploadOf(id))
        compose.onNode(isToggleable()).assertIsOff()
        assertEquals(listOf("沒有取得 YouTube 授權，這支不會上傳"), notices)
    }

    @Test
    fun 授權通過才打勾() {
        val calls = mutableListOf<Unit>()
        val notices = mutableListOf<String>()
        val id = showRoute(autoUpload = false, granted = UploadAuthResult.GRANTED, authorizeCalls = calls, notices = notices)

        compose.onNodeWithText("完成後自動上傳到 YouTube").performClick()
        compose.waitForIdle()

        assertEquals(1, calls.size)
        assertEquals(true, autoUploadOf(id))
        assertEquals(emptyList<String>(), notices)
    }

    @Test
    fun 授權服務不可用時照樣打勾不提示() {
        val calls = mutableListOf<Unit>()
        val notices = mutableListOf<String>()
        val id = showRoute(autoUpload = false, granted = UploadAuthResult.UNAVAILABLE, authorizeCalls = calls, notices = notices)

        compose.onNodeWithText("完成後自動上傳到 YouTube").performClick()
        compose.waitForIdle()

        assertEquals(1, calls.size)
        assertEquals(true, autoUploadOf(id))
        assertEquals(emptyList<String>(), notices)
    }

    @Test
    fun 取消打勾不用授權() {
        val calls = mutableListOf<Unit>()
        val notices = mutableListOf<String>()
        val id = showRoute(autoUpload = true, granted = UploadAuthResult.DECLINED, authorizeCalls = calls, notices = notices)

        compose.onNodeWithText("完成後自動上傳到 YouTube").performClick()
        compose.waitForIdle()

        assertEquals(0, calls.size)
        assertEquals(false, autoUploadOf(id))
    }
}
