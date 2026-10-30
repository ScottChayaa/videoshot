package com.xenyaa.videoshot.edit

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipExportWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = FakeCacheRepo()
    private val ran = mutableListOf<Long>()

    private fun draft() = ClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1_000L, null, 1080, 1920, 0)),
        EditHistory(Timeline(listOf(Segment(1, 0, 3000)))),
    )

    private fun foregroundInfo(@Suppress("UNUSED_PARAMETER") task: EditTask) = ForegroundInfo(NOTIFY_PROGRESS_ID, Notification())

    /** 開不了前景服務時被提醒「可以繼續上傳」的任務。 */
    private val resumable = mutableListOf<EditTask>()

    private fun worker(
        taskId: Long,
        updater: ForegroundUpdater = ForegroundUpdater { _, _, _ -> Futures.immediateFuture<Void?>(null) },
        runTask: suspend (Long) -> Unit = { ran += it },
    ) = TestListenableWorkerBuilder<ClipExportWorker>(context)
        .setInputData(workDataOf("task_id" to taskId))
        .setForegroundUpdater(updater)
        .setWorkerFactory(
            ClipExportWorkerFactory(repo, ::foregroundInfo, runTask, poll = {}, notifyCanResume = { resumable += it }) { 5_000L },
        )
        .build()

    @Test
    fun 升成前景後交給runner輸出() = runTest {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)

        assertEquals(ListenableWorker.Result.success(), worker(id).doWork())

        assertEquals(listOf(id), ran)
    }

    @Test
    fun 系統不讓開前景服務時改回等待中且不輸出() = runTest {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 30, 2_000L)
        val refuse = ForegroundUpdater { _, _, _ ->
            Futures.immediateFailedFuture<Void?>(IllegalStateException("ForegroundServiceStartNotAllowedException"))
        }

        assertEquals(ListenableWorker.Result.success(), worker(id, refuse).doWork())

        assertTrue(ran.isEmpty())
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.QUEUED, task.state)
        assertEquals(0, task.progress)
        assertTrue(resumable.isEmpty()) // 輸出中的不提醒（下次打開 app 從頭輸出）
    }

    @Test
    fun 上傳中的任務開不了前景服務時留在上傳中() = runTest {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        repo.setEditTaskReadyToUpload(id, 0, 2_000L)
        repo.setEditTaskProgress(id, EditTaskState.UPLOADING, 40, 3_000L)
        val refuse = ForegroundUpdater { _, _, _ ->
            Futures.immediateFailedFuture<Void?>(IllegalStateException("ForegroundServiceStartNotAllowedException"))
        }

        assertEquals(ListenableWorker.Result.success(), worker(id, refuse).doWork())

        assertTrue(ran.isEmpty())
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.UPLOADING, task.state) // 退回等待中會重新輸出
        assertEquals(40, task.progress)
        // 例如額度重置後 app 在背景：提醒使用者打開 app 接著傳
        assertEquals(listOf(id), resumable.map { it.id })
    }

    @Test
    fun 上傳中的任務會被執行() = runTest {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        repo.setEditTaskReadyToUpload(id, 0, 2_000L)
        var foregrounded = 0
        val counting = ForegroundUpdater { _, _, _ -> foregrounded++; Futures.immediateFuture<Void?>(null) }

        assertEquals(ListenableWorker.Result.success(), worker(id, counting).doWork())

        assertEquals(listOf(id), ran)
        assertEquals(1, foregrounded)
    }

    @Test
    fun 取消時不當成開不了前景服務() = runTest {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 30, 2_000L)

        try {
            worker(id) { throw CancellationException("取消") }.doWork()
            fail("取消應該往外丟")
        } catch (_: CancellationException) {
        }
        assertEquals(EditTaskState.RENDERING, repo.editTask(id).first()!!.state)
    }

    @Test
    fun 任務不存在或已經結束就什麼都不做() = runTest {
        val done = repo.startEditTask(draft(), "完成", true, 1_000L)
        repo.setEditTaskRendered(done, 0, null, 2_000L)
        var foregrounded = 0
        val counting = ForegroundUpdater { _, _, _ -> foregrounded++; Futures.immediateFuture<Void?>(null) }

        assertEquals(ListenableWorker.Result.success(), worker(99, counting).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(done, counting).doWork())

        assertTrue(ran.isEmpty())
        assertEquals(0, foregrounded)
    }

    // ---- 通知 ----

    private fun task(galleryUri: String? = null, skipped: Int = 0): EditTask {
        val base = kotlinx.coroutines.runBlocking {
            val id = repo.startEditTask(draft(), "videoshot 2026/10/20", true, 1_000L)
            repo.editTask(id).first()!!
        }
        return base.copy(galleryUri = galleryUri, skippedSources = skipped, state = EditTaskState.RENDERED)
    }

    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun 進度通知用固定編號且帶百分比與標題() {
        val notifier = AndroidClipExportNotifier(context)
        notifier.progress(task(), 42)

        val n = shadowOf(manager).getNotification(NOTIFY_PROGRESS_ID)
        assertEquals("輸出影片中 42%", n.title())
        assertEquals("videoshot 2026/10/20", n.text())
        assertEquals(CHANNEL_PROGRESS, n.channelId)
    }

    @Test
    fun 完成通知用自己的編號所以前景通知結束後還在() {
        val notifier = AndroidClipExportNotifier(context)
        val t = task(skipped = 2)
        notifier.finished(t)

        assertNull(shadowOf(manager).getNotification(NOTIFY_PROGRESS_ID))
        val n = shadowOf(manager).getNotification(resultNotificationId(t.id))
        assertNotNull(n)
        assertEquals(4400 + t.id.toInt(), resultNotificationId(t.id))
        assertEquals("影片輸出完成", n.title())
        assertEquals("videoshot 2026/10/20・點一下檢查成品・有 2 個素材已不存在，已略過", n.text())
        assertEquals(CHANNEL_RESULT, n.channelId)
    }

    @Test
    fun 存相簿與失敗的通知文字() {
        val notifier = AndroidClipExportNotifier(context)
        val saved = task(galleryUri = "content://media/external/video/9")
        notifier.finished(saved)
        val n = shadowOf(manager).getNotification(resultNotificationId(saved.id))
        assertEquals("影片已存到相簿", n.title())
        assertEquals("videoshot 2026/10/20", n.text())

        val failedTask = task()
        notifier.failed(failedTask, "空間不足，還需要 1.2 GB")
        val f = shadowOf(manager).getNotification(resultNotificationId(failedTask.id))
        assertEquals("影片輸出失敗", f.title())
        assertEquals("空間不足，還需要 1.2 GB", f.text())
    }

    @Test
    fun 正在看那一支的導出畫面時不跳結果通知_舊的也收掉() {
        val notifier = AndroidClipExportNotifier(context)
        val watched = task()
        val other = task()
        notifier.failed(watched, "輸出失敗") // 重試前留下的失敗通知

        notifier.viewingTaskId = watched.id
        assertNull(shadowOf(manager).getNotification(resultNotificationId(watched.id)))

        notifier.finished(watched)
        notifier.finished(other)
        assertNull(shadowOf(manager).getNotification(resultNotificationId(watched.id)))
        assertNotNull(shadowOf(manager).getNotification(resultNotificationId(other.id)))

        // 離開畫面後照常跳
        notifier.viewingTaskId = null
        notifier.failed(watched, "輸出失敗")
        assertNotNull(shadowOf(manager).getNotification(resultNotificationId(watched.id)))
    }

    @Test
    fun 收掉一個任務的結果通知() {
        val notifier = AndroidClipExportNotifier(context)
        val t = task()
        notifier.finished(t)

        notifier.dismissResult(t.id)

        assertNull(shadowOf(manager).getNotification(resultNotificationId(t.id)))
    }

    @Test
    fun 通知點下去開MainActivity帶剪輯extra() {
        val notifier = AndroidClipExportNotifier(context)
        notifier.progress(task(), 1)

        val pending = shadowOf(shadowOf(manager).getNotification(NOTIFY_PROGRESS_ID).contentIntent)
        assertTrue(pending.isActivityIntent)
        assertEquals("com.xenyaa.videoshot.MainActivity", pending.savedIntent.component?.className)
        assertTrue(pending.savedIntent.getBooleanExtra(EXTRA_OPEN_CLIP, false))
        assertEquals("com.xenyaa.videoshot.OPEN_CLIP", EXTRA_OPEN_CLIP)
    }

    @Test
    fun 兩個通知頻道的名稱與重要性() {
        AndroidClipExportNotifier(context)

        val progress = manager.getNotificationChannel(CHANNEL_PROGRESS)
        assertEquals("影片輸出進度", progress.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_LOW, progress.importance)
        val result = manager.getNotificationChannel(CHANNEL_RESULT)
        assertEquals("影片輸出結果", result.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, result.importance)
    }

    @Test
    fun 前景服務類型依版本選() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, foregroundServiceTypeFor(35, false))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, foregroundServiceTypeFor(37, false))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, foregroundServiceTypeFor(34, false))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, foregroundServiceTypeFor(29, false))
        assertNull(foregroundServiceTypeFor(28, false))

        val notifier = AndroidClipExportNotifier(context)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, notifier.foregroundInfo(task(), 33).foregroundServiceType)
        assertEquals(NOTIFY_PROGRESS_ID, notifier.foregroundInfo(task(), 28).notificationId)
    }
}
