package com.xenyaa.videoshot.ui.clip

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import com.xenyaa.videoshot.ui.theme.AppTheme
import com.xenyaa.videoshot.ui.theme.Palette
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
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
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.media.MediaThumbLoader
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class ClipHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        onNewClip: () -> Unit = {},
        draft: ClipDraftSummary? = null,
        onOpenDraft: () -> Unit = {},
        onDiscardDraft: () -> Unit = {},
        tasks: List<EditTask> = emptyList(),
        onOpenTask: (Long) -> Unit = {},
        onCancelTask: (Long) -> Unit = {},
        onDeleteTask: (Long) -> Unit = {},
        onRetryTask: (Long) -> Unit = {},
    ) {
        compose.setContent {
            VideoshotTheme {
                palette = AppTheme.colors
                ClipHomeScreen(
                    onNewClip = onNewClip, draft = draft, onOpenDraft = onOpenDraft, onDiscardDraft = onDiscardDraft,
                    tasks = tasks, thumbLoader = MediaThumbLoader(decode = { null }),
                    onOpenTask = onOpenTask, onCancelTask = onCancelTask, onDeleteTask = onDeleteTask, onRetryTask = onRetryTask,
                )
            }
        }
    }

    private fun task(
        id: Long,
        state: EditTaskState,
        title: String = "任務$id",
        progress: Int = 0,
        failReason: String? = null,
        galleryUri: String? = null,
        failedStep: EditTaskStep? = null,
        videoId: String? = null,
    ): EditTask {
        val source = ClipSource(7, "content://media/7", SourceKind.PHOTO, 0, null, 1080, 1920, 0)
        val timeline = Timeline(listOf(Segment(7, 0, 3_000)))
        return EditTask(
            id = id, state = state, progress = progress, title = title,
            draft = ClipDraft(ClipOrientation.PORTRAIT, listOf(source), EditHistory(timeline)),
            autoUpload = true, failReason = failReason, skippedSources = 0, galleryUri = galleryUri, createdAt = id,
            failedStep = failedStep, videoId = videoId,
        )
    }

    private lateinit var palette: Palette

    private val hasProgressBar = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun longPress(text: String) = compose.onNodeWithText(text).performTouchInput { longClick() }

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

    // ---- 導出任務清單（階段 20）----

    @Test fun 五種狀態列的文字() {
        show(
            tasks = listOf(
                task(5, EditTaskState.FAILED, failReason = "空間不足，還需要 1.2 GB"),
                task(4, EditTaskState.RENDERED, galleryUri = "content://media/external/video/9"),
                task(3, EditTaskState.RENDERED),
                task(2, EditTaskState.RENDERING, progress = 42),
                task(1, EditTaskState.QUEUED),
            ),
        )
        compose.onNodeWithText("等待中").assertIsDisplayed()
        compose.onNodeWithText("輸出影片中 42%").assertIsDisplayed()
        compose.onNodeWithText("輸出完成").assertIsDisplayed()
        compose.onNodeWithText("已存到相簿").assertIsDisplayed()
        compose.onNodeWithText("失敗：空間不足，還需要 1.2 GB").assertIsDisplayed()
        compose.onNodeWithText("任務1").assertIsDisplayed()
    }

    @Test fun 失敗沒有原因時寫輸出失敗() {
        show(tasks = listOf(task(1, EditTaskState.FAILED)))
        compose.onNodeWithText("失敗：輸出失敗").assertIsDisplayed()
    }

    @Test fun 縮圖讀不到時回到前景再讀一次() {
        var allowed = false
        var decodes = 0
        val loader = MediaThumbLoader(decode = { decodes++; if (allowed) androidx.compose.ui.graphics.ImageBitmap(1, 1) else null })
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                VideoshotTheme {
                    ClipHomeScreen(
                        onNewClip = {}, draft = null, onOpenDraft = {}, onDiscardDraft = {},
                        tasks = listOf(task(1, EditTaskState.RENDERED)), thumbLoader = loader,
                        onOpenTask = {}, onCancelTask = {}, onDeleteTask = {}, onRetryTask = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1, decodes) // 沒權限：讀不到

        // 去系統設定給回權限再回來
        allowed = true
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertEquals(2, decodes)

        // 讀到之後再回前景不會重讀
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertEquals(2, decodes)
    }

    @Test fun 重試後殘留的相簿位置不影響狀態列() {
        // 重試時任務變回等待中，但前一次留下的 galleryUri 還在；只有「輸出完成」才看它
        show(tasks = listOf(task(1, EditTaskState.QUEUED, galleryUri = "content://old")))
        compose.onNodeWithText("等待中").assertIsDisplayed()
        compose.onNodeWithText("已存到相簿").assertDoesNotExist()
    }

    @Test fun 失敗列有重試() {
        val retried = mutableListOf<Long>()
        show(tasks = listOf(task(3, EditTaskState.FAILED, failReason = "輸出失敗")), onRetryTask = { retried += it })
        compose.onNodeWithText("重試").performClick()
        assertEquals(listOf(3L), retried)
    }

    @Test fun 只有失敗列有重試() {
        show(tasks = listOf(task(1, EditTaskState.QUEUED), task(2, EditTaskState.RENDERED)))
        compose.onNodeWithText("重試").assertDoesNotExist()
    }

    @Test fun 點任務列通知呼叫端() {
        val opened = mutableListOf<Long>()
        show(tasks = listOf(task(8, EditTaskState.RENDERED, title = "旅行")), onOpenTask = { opened += it })
        compose.onNodeWithText("旅行").performClick()
        assertEquals(listOf(8L), opened)
    }

    @Test fun 長按等待中有取消_確認後才回報() {
        val cancelled = mutableListOf<Long>()
        show(tasks = listOf(task(4, EditTaskState.QUEUED, title = "等著的")), onCancelTask = { cancelled += it })
        longPress("等著的")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()
        compose.onNodeWithText("已經輸出的部分會刪掉。").assertIsDisplayed()
        assertEquals(emptyList<Long>(), cancelled)

        compose.onNodeWithText("取消導出").performClick()
        assertEquals(listOf(4L), cancelled)
    }

    @Test fun 長按輸出中取消後選繼續導出什麼都沒發生() {
        val cancelled = mutableListOf<Long>()
        show(tasks = listOf(task(4, EditTaskState.RENDERING, title = "跑著的", progress = 10)), onCancelTask = { cancelled += it })
        longPress("跑著的")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("繼續導出").performClick()
        assertEquals(emptyList<Long>(), cancelled)
        compose.onNodeWithText("取消導出？").assertDoesNotExist()
    }

    @Test fun 長按等待中沒有刪除任務() {
        show(tasks = listOf(task(4, EditTaskState.QUEUED, title = "等著的")))
        longPress("等著的")
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("刪除任務").assertDoesNotExist()
    }

    @Test fun 長按完成有刪除任務且要確認_沒存相簿的多一句() {
        val deleted = mutableListOf<Long>()
        show(tasks = listOf(task(6, EditTaskState.RENDERED, title = "完成的")), onDeleteTask = { deleted += it })
        longPress("完成的")
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("刪除這個任務？").assertIsDisplayed()
        compose.onNodeWithText("app 裡輸出的影片會一起刪掉。").assertIsDisplayed()
        assertEquals(emptyList<Long>(), deleted)

        compose.onNodeWithText("刪除").performClick()
        assertEquals(listOf(6L), deleted)
    }

    @Test fun 存進相簿的任務刪除確認沒有那一句() {
        show(tasks = listOf(task(6, EditTaskState.RENDERED, title = "存了的", galleryUri = "content://media/external/video/9")))
        longPress("存了的")
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("刪除這個任務？").assertIsDisplayed()
        compose.onNodeWithText("app 裡輸出的影片會一起刪掉。").assertDoesNotExist()
    }

    @Test fun 失敗的任務也能長按刪除_取消不刪() {
        val deleted = mutableListOf<Long>()
        show(tasks = listOf(task(2, EditTaskState.FAILED, title = "壞了的", failReason = "輸出失敗")), onDeleteTask = { deleted += it })
        longPress("壞了的")
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("刪除這個任務？").assertIsDisplayed()
        compose.onNodeWithText("app 裡輸出的影片會一起刪掉。").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        assertEquals(emptyList<Long>(), deleted)
        compose.onNodeWithText("刪除這個任務？").assertDoesNotExist()
    }

    @Test fun 有任務時不顯示空狀態() {
        show(tasks = listOf(task(1, EditTaskState.QUEUED)))
        compose.onNodeWithText("把手機裡的照片和影片剪成一支片").assertDoesNotExist()
        compose.onNodeWithText("新增剪輯").assertIsDisplayed()
    }

    @Test fun 順序是編輯中_任務_新增剪輯() {
        show(draft = summary, tasks = listOf(task(1, EditTaskState.QUEUED, title = "任務甲")))
        val draftTop = compose.onNodeWithText("編輯中").getUnclippedBoundsInRoot().top
        val taskTop = compose.onNodeWithText("任務甲").getUnclippedBoundsInRoot().top
        val newTop = compose.onNodeWithText("新增剪輯").getUnclippedBoundsInRoot().top
        assertTrue(draftTop < taskTop)
        assertTrue(taskTop < newTop)
    }

    @Test fun 任務順序照傳進來的順序() {
        show(tasks = listOf(task(2, EditTaskState.QUEUED, title = "新的"), task(1, EditTaskState.QUEUED, title = "舊的")))
        assertTrue(compose.onNodeWithText("新的").getUnclippedBoundsInRoot().top < compose.onNodeWithText("舊的").getUnclippedBoundsInRoot().top)
    }

    @Test fun 取消確認框開著時任務剛好完成_框消失且不會取消() {
        val cancelled = mutableListOf<Long>()
        val tasks = androidx.compose.runtime.mutableStateOf(listOf(task(4, EditTaskState.RENDERING, title = "跑著的", progress = 99)))
        compose.setContent {
            VideoshotTheme {
                ClipHomeScreen(
                    onNewClip = {}, tasks = tasks.value, thumbLoader = MediaThumbLoader(decode = { null }),
                    onCancelTask = { cancelled += it },
                )
            }
        }
        longPress("跑著的")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()

        tasks.value = listOf(task(4, EditTaskState.RENDERED, title = "跑著的"))
        compose.waitForIdle()

        compose.onNodeWithText("取消導出？").assertDoesNotExist()
        assertEquals(emptyList<Long>(), cancelled)
    }

    @Test fun 刪除確認框開著時任務變回進行中_框消失且不會刪除() {
        val deleted = mutableListOf<Long>()
        val tasks = androidx.compose.runtime.mutableStateOf(listOf(task(4, EditTaskState.FAILED, title = "壞了的", failReason = "輸出失敗")))
        compose.setContent {
            VideoshotTheme {
                ClipHomeScreen(
                    onNewClip = {}, tasks = tasks.value, thumbLoader = MediaThumbLoader(decode = { null }),
                    onDeleteTask = { deleted += it },
                )
            }
        }
        longPress("壞了的")
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("刪除這個任務？").assertIsDisplayed()

        tasks.value = listOf(task(4, EditTaskState.QUEUED, title = "壞了的"))
        compose.waitForIdle()

        compose.onNodeWithText("刪除這個任務？").assertDoesNotExist()
        assertEquals(emptyList<Long>(), deleted)
    }

    // ---- 上傳到 YouTube（階段 21）----

    @Test fun 上傳中顯示百分比與進度條() {
        show(tasks = listOf(task(1, EditTaskState.UPLOADING, progress = 63)))
        compose.onNodeWithText("上傳中 63%").assertIsDisplayed()
        compose.onAllNodes(hasProgressBar).assertCountEquals(1)
        assertEquals(palette.textDim, taskStatusColor(EditTaskState.UPLOADING, palette))
    }

    @Test fun 處理中只有文字沒有進度條() {
        show(tasks = listOf(task(1, EditTaskState.PROCESSING)))
        compose.onNodeWithText("YouTube 處理中").assertIsDisplayed()
        compose.onAllNodes(hasProgressBar).assertCountEquals(0)
    }

    @Test fun 已完成用主色字且右邊有主色圓點() {
        show(tasks = listOf(task(1, EditTaskState.DONE, videoId = "abc")))
        compose.onNodeWithText("已完成，點一下開始取圖").assertIsDisplayed()
        assertEquals(palette.accent, taskStatusColor(EditTaskState.DONE, palette))
        assertEquals(palette.textDim, taskStatusColor(EditTaskState.PROCESSING, palette))
        compose.onNodeWithTag("doneDot", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasProgressBar).assertCountEquals(0)
    }

    @Test fun 其他狀態沒有主色圓點() {
        show(tasks = listOf(task(1, EditTaskState.PROCESSING), task(2, EditTaskState.UPLOADING, progress = 5)))
        compose.onAllNodes(hasTestTag("doneDot"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun 失敗原因一行_上傳那一步失敗也有重試() {
        val retried = mutableListOf<Long>()
        show(
            tasks = listOf(task(3, EditTaskState.FAILED, failReason = "沒有網路連線", failedStep = EditTaskStep.UPLOAD)),
            onRetryTask = { retried += it },
        )
        compose.onNodeWithText("失敗：沒有網路連線").assertIsDisplayed()
        assertEquals(palette.danger, taskStatusColor(EditTaskState.FAILED, palette))
        compose.onNodeWithText("重試").performClick()
        assertEquals(listOf(3L), retried)
    }

    @Test fun 上傳中與處理中沒有重試也沒有勾選框() {
        show(tasks = listOf(task(1, EditTaskState.UPLOADING, progress = 5), task(2, EditTaskState.PROCESSING)))
        compose.onNodeWithText("重試").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test fun 長按上傳中有取消_確認框寫取消上傳() {
        val cancelled = mutableListOf<Long>()
        show(tasks = listOf(task(4, EditTaskState.UPLOADING, title = "傳著的", progress = 30)), onCancelTask = { cancelled += it })
        longPress("傳著的")
        compose.onNodeWithText("刪除任務").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("取消上傳？").assertIsDisplayed()
        compose.onNodeWithText("已經上傳的部分會作廢。").assertIsDisplayed()
        assertEquals(emptyList<Long>(), cancelled)

        compose.onNodeWithText("取消上傳").performClick()
        assertEquals(listOf(4L), cancelled)
    }

    @Test fun 取消上傳選繼續上傳什麼都沒發生() {
        val cancelled = mutableListOf<Long>()
        show(tasks = listOf(task(4, EditTaskState.UPLOADING, title = "傳著的", progress = 30)), onCancelTask = { cancelled += it })
        longPress("傳著的")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("繼續上傳").performClick()
        assertEquals(emptyList<Long>(), cancelled)
        compose.onNodeWithText("取消上傳？").assertDoesNotExist()
    }

    @Test fun 長按處理中有取消_確認框寫取消這個任務() {
        val cancelled = mutableListOf<Long>()
        show(tasks = listOf(task(5, EditTaskState.PROCESSING, title = "等著的", videoId = "abc")), onCancelTask = { cancelled += it })
        longPress("等著的")
        compose.onNodeWithText("刪除任務").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("取消這個任務？").assertIsDisplayed()
        compose.onNodeWithText("影片仍在你的 YouTube 頻道，可以到 YouTube 工作室刪除。").assertIsDisplayed()
        assertEquals(emptyList<Long>(), cancelled)

        compose.onNodeWithText("取消任務").performClick()
        assertEquals(listOf(5L), cancelled)
    }

    @Test fun 取消任務選繼續等待什麼都沒發生() {
        val cancelled = mutableListOf<Long>()
        show(tasks = listOf(task(5, EditTaskState.PROCESSING, title = "等著的")), onCancelTask = { cancelled += it })
        longPress("等著的")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("繼續等待").performClick()
        assertEquals(emptyList<Long>(), cancelled)
        compose.onNodeWithText("取消這個任務？").assertDoesNotExist()
    }

    @Test fun 長按已完成有刪除任務_有影片編號的多一行() {
        val deleted = mutableListOf<Long>()
        show(tasks = listOf(task(6, EditTaskState.DONE, title = "傳完的", videoId = "abc")), onDeleteTask = { deleted += it })
        longPress("傳完的")
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("刪除這個任務？").assertIsDisplayed()
        compose.onNodeWithText("影片仍在你的 YouTube 頻道，可以到 YouTube 工作室刪除。").assertIsDisplayed()
        compose.onNodeWithText("app 裡輸出的影片會一起刪掉。").assertDoesNotExist()
        compose.onNodeWithText("刪除").performClick()
        assertEquals(listOf(6L), deleted)
    }

    @Test fun 沒有影片編號的失敗任務刪除確認沒有YouTube那一行() {
        show(tasks = listOf(task(7, EditTaskState.FAILED, title = "壞了的", failReason = "沒有網路連線", failedStep = EditTaskStep.UPLOAD)))
        longPress("壞了的")
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("刪除這個任務？").assertIsDisplayed()
        compose.onNodeWithText("影片仍在你的 YouTube 頻道，可以到 YouTube 工作室刪除。").assertDoesNotExist()
    }

    @Test fun 處理失敗的任務有影片編號_刪除確認有YouTube那一行() {
        show(tasks = listOf(task(7, EditTaskState.FAILED, title = "壞了的", failReason = "YouTube 還沒產生縮圖", failedStep = EditTaskStep.STORYBOARD, videoId = "abc")))
        longPress("壞了的")
        compose.onNodeWithText("刪除任務").performClick()
        compose.onNodeWithText("影片仍在你的 YouTube 頻道，可以到 YouTube 工作室刪除。").assertIsDisplayed()
    }

    @Test fun 取消確認框開著時輸出轉成上傳_框消失且不會用輸出的說法取消() {
        val cancelled = mutableListOf<Long>()
        val tasks = androidx.compose.runtime.mutableStateOf(listOf(task(4, EditTaskState.RENDERING, title = "跑著的", progress = 99)))
        compose.setContent {
            VideoshotTheme {
                ClipHomeScreen(
                    onNewClip = {}, tasks = tasks.value, thumbLoader = MediaThumbLoader(decode = { null }),
                    onCancelTask = { cancelled += it },
                )
            }
        }
        longPress("跑著的")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()

        tasks.value = listOf(task(4, EditTaskState.UPLOADING, title = "跑著的", progress = 1))
        compose.waitForIdle()

        compose.onNodeWithText("取消導出？").assertDoesNotExist()
        compose.onNodeWithText("取消上傳？").assertDoesNotExist()
        assertEquals(emptyList<Long>(), cancelled)
    }

    @Test fun 取消確認框開著時等待轉成輸出_框留著() {
        val tasks = androidx.compose.runtime.mutableStateOf(listOf(task(4, EditTaskState.QUEUED, title = "等著的")))
        compose.setContent {
            VideoshotTheme {
                ClipHomeScreen(onNewClip = {}, tasks = tasks.value, thumbLoader = MediaThumbLoader(decode = { null }))
            }
        }
        longPress("等著的")
        compose.onNodeWithText("取消").performClick()
        tasks.value = listOf(task(4, EditTaskState.RENDERING, title = "等著的", progress = 1))
        compose.waitForIdle()
        compose.onNodeWithText("取消導出？").assertIsDisplayed()
    }
}
