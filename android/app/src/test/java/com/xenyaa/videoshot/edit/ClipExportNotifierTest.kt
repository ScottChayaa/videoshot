package com.xenyaa.videoshot.edit

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 上傳相關的通知文字與前景服務類型（輸出的部分在 [ClipExportWorkerTest]）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipExportNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private var nextId = 1L

    private fun task(
        state: EditTaskState = EditTaskState.UPLOADING,
        autoUpload: Boolean = true,
        skipped: Int = 0,
        failedStep: EditTaskStep? = null,
        progress: Int = 0,
    ) = EditTask(
        id = nextId++,
        state = state,
        progress = progress,
        title = "videoshot 2026/10/29",
        draft = ClipDraft(
            ClipOrientation.PORTRAIT,
            listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1_000L, null, 1080, 1920, 0)),
            EditHistory(Timeline(listOf(Segment(1, 0, 3000)))),
        ),
        autoUpload = autoUpload,
        failReason = null,
        skippedSources = skipped,
        galleryUri = null,
        createdAt = 1_000L,
        failedStep = failedStep,
    )

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun 上傳中的進度文字() {
        val notifier = AndroidClipExportNotifier(context)

        notifier.progress(task(state = EditTaskState.UPLOADING), 63)
        val n = shadowOf(manager).getNotification(NOTIFY_PROGRESS_ID)
        assertEquals("上傳中 63%", n.title())
        assertEquals("videoshot 2026/10/29", n.text())

        // 輸出中照舊
        notifier.progress(task(state = EditTaskState.RENDERING), 12)
        assertEquals("輸出影片中 12%", shadowOf(manager).getNotification(NOTIFY_PROGRESS_ID).title())

        // 重開後直接從上傳中開始的前景通知
        val info = notifier.foregroundInfo(task(state = EditTaskState.UPLOADING, progress = 40), 35)
        assertEquals("上傳中 40%", info.notification.title())
    }

    @Test
    fun 完成橫幅標題與略過內文() {
        val notifier = AndroidClipExportNotifier(context)
        val t = task(state = EditTaskState.DONE, skipped = 2)
        notifier.done(t)

        val n = shadowOf(manager).getNotification(resultNotificationId(t.id))
        assertEquals("已完成，點一下開始取圖", n.title())
        assertEquals("videoshot 2026/10/29・有 2 個素材已不存在，已略過", n.text())
        assertEquals(CHANNEL_RESULT, n.channelId)

        val plain = task(state = EditTaskState.DONE)
        notifier.done(plain)
        assertEquals("videoshot 2026/10/29", shadowOf(manager).getNotification(resultNotificationId(plain.id)).text())

        // 停在那個任務的導出畫面時不跳
        val watched = task(state = EditTaskState.DONE)
        notifier.viewingTaskId = watched.id
        notifier.done(watched)
        assertNull(shadowOf(manager).getNotification(resultNotificationId(watched.id)))
    }

    @Test
    fun 失敗標題依步驟() {
        val notifier = AndroidClipExportNotifier(context)
        val cases = listOf(
            EditTaskStep.RENDER to "影片輸出失敗",
            null to "影片輸出失敗", // 舊資料沒記步驟＝輸出
            EditTaskStep.UPLOAD to "影片上傳失敗",
            EditTaskStep.STORYBOARD to "YouTube 處理失敗",
        )
        for ((step, title) in cases) {
            val t = task(state = EditTaskState.FAILED, failedStep = step)
            notifier.failed(t, "沒有網路連線")

            val n = shadowOf(manager).getNotification(resultNotificationId(t.id))
            assertEquals("步驟 $step", title, n.title())
            assertEquals("沒有網路連線", n.text())
        }
    }

    @Test
    fun 前景類型API35有上傳時含dataSync() {
        val both = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        assertEquals(both, foregroundServiceTypeFor(35, true))
        assertEquals(both, foregroundServiceTypeFor(37, true))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, foregroundServiceTypeFor(35, false))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, foregroundServiceTypeFor(34, true))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, foregroundServiceTypeFor(29, true))
        assertNull(foregroundServiceTypeFor(28, true))

        val notifier = AndroidClipExportNotifier(context)
        assertEquals(both, notifier.foregroundInfo(task(state = EditTaskState.QUEUED, autoUpload = true), 35).foregroundServiceType)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            notifier.foregroundInfo(task(state = EditTaskState.QUEUED, autoUpload = false), 35).foregroundServiceType,
        )
    }
}
