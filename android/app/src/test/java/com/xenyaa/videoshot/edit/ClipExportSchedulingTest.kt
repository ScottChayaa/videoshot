package com.xenyaa.videoshot.edit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** 排程、取消、恢復：用 work-testing 的 WorkManager，工作本身換成永遠不結束的替身，才看得到「排著」的狀態。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipExportSchedulingTest {

    /** 替身：記下收到的任務編號，然後一直跑著（不結束）。 */
    class HangingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            started += inputData.getLong("task_id", -1L)
            awaitCancellation()
        }
    }

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private val repo = FakeCacheRepo()

    @Before
    fun setUp() {
        started.clear()
        context = ApplicationProvider.getApplicationContext()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == ClipExportWorker::class.java.name) HangingWorker(appContext, workerParameters) else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(factory).setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun infos(taskId: Long): List<WorkInfo> = workManager.getWorkInfosByTag("clip-export-$taskId").get()

    private fun draft() = ClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1_000L, null, 1080, 1920, 0)),
        EditHistory(Timeline(listOf(Segment(1, 0, 3000)))),
    )

    @Test
    fun 排程帶任務標籤() {
        enqueueClipExport(context, 7)

        val info = infos(7).single()
        assertTrue("clip-export-7" in info.tags)
        assertTrue(!info.state.isFinished)
        assertEquals(listOf(7L), started.toList()) // input 的 task_id 有帶到工作裡
    }

    @Test
    fun 取消只取消那一支() {
        enqueueClipExport(context, 1)
        enqueueClipExport(context, 2) // 排在 1 後面（同一串）

        cancelClipExport(context, 2)

        assertEquals(WorkInfo.State.CANCELLED, infos(2).single().state)
        assertTrue(!infos(1).single().state.isFinished)
    }

    @Test
    fun 恢復時輸出中的改回等待並重排() = runBlocking {
        val id = repo.startEditTask(draft(), "標題", 1_000L)
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 40, 2_000L) // app 被殺時停在輸出中

        resumeClipExports(context, repo, 3_000L)

        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.QUEUED, task.state)
        assertEquals(0, task.progress)
        assertTrue(!infos(id).single().state.isFinished)
    }

    @Test
    fun 已經排著的不重複排() = runBlocking {
        val id = repo.startEditTask(draft(), "標題", 1_000L)
        enqueueClipExport(context, id)
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 40, 2_000L) // 工作真的在跑

        resumeClipExports(context, repo, 3_000L)

        assertEquals(1, infos(id).size)
        assertEquals(EditTaskState.RENDERING, repo.editTask(id).first()!!.state) // 也不動它的狀態
    }

    @Test
    fun 輸出完成或失敗的任務不會被排() = runBlocking {
        val done = repo.startEditTask(draft(), "完成", 1_000L)
        repo.setEditTaskRendered(done, 0, null, 2_000L)
        val failed = repo.startEditTask(draft(), "失敗", 1_000L)
        repo.setEditTaskFailed(failed, "輸出失敗", 2_000L)

        resumeClipExports(context, repo, 3_000L)

        assertTrue(infos(done).isEmpty())
        assertTrue(infos(failed).isEmpty())
    }

    companion object {
        val started = CopyOnWriteArrayList<Long>()
    }
}
