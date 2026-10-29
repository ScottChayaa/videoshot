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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** 排程、取消、恢復：用 work-testing 的 WorkManager，工作本身換成永遠不結束的替身，才看得到「排著」的狀態。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipExportSchedulingTest {

    /**
     * 替身：記下收到的任務編號，然後一直跑著（不結束）。
     * 有設 [tasks] 時跟真的工作一樣先查任務、不在就直接結束；有 [gates] 的編號等它放行才結束。
     */
    class HangingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = inputData.getLong("task_id", -1L)
            val repo = tasks
            if (repo != null && repo.editTask(id).first() == null) return Result.success()
            started += id
            running += id
            try {
                gates[id]?.await() ?: awaitCancellation()
            } finally {
                running -= id
            }
            return Result.success()
        }
    }

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private val repo = FakeCacheRepo()

    @Before
    fun setUp() {
        started.clear()
        gates.clear()
        tasks = null
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

    /**
     * 替身工作不會自己結束：測試結束前全部取消並等它們真的停下，不然它們會在 WorkManager 的測試資料庫
     * 關掉之後才被取消，例外漏到之後別的測試（`UncaughtExceptionsBeforeTest`）。
     */
    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
        waitUntil { running.isEmpty() }
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

    /** 等 [condition] 成立（工作在別的執行緒跑），最多 5 秒。 */
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "等太久" }
            Thread.sleep(10)
        }
    }

    @Test
    fun 取消排隊中的不動工作只取消正在跑的() = runBlocking {
        enqueueClipExport(context, 1)
        enqueueClipExport(context, 2) // 排在 1 後面（同一串）
        waitUntil { infos(1).single().state == WorkInfo.State.RUNNING }

        cancelClipExport(context, 2) // 排隊中：工作留著，輪到時讀不到任務自己結束

        assertTrue(!infos(2).single().state.isFinished)
        assertEquals(WorkInfo.State.RUNNING, infos(1).single().state)

        cancelClipExport(context, 1) // 正在跑：取消
        assertEquals(WorkInfo.State.CANCELLED, infos(1).single().state)
    }

    @Test
    fun 取消排隊的任務後再排一支_正在跑的不受影響_新的排在後面() = runBlocking {
        tasks = repo
        val a = repo.startEditTask(draft(), "A", true, 1_000L)
        val b = repo.startEditTask(draft(), "B", true, 1_000L)
        gates[a] = CompletableDeferred()
        enqueueClipExport(context, a)
        enqueueClipExport(context, b) // b 是串的末端
        waitUntil { started.toList() == listOf(a) }

        // 剪輯頁取消 b：取消工作（排隊中的不動）→ 刪任務 → 補排
        cancelClipExport(context, b)
        repo.deleteEditTask(b)
        resumeClipExports(context, repo, 3_000L)
        // 之後再導出一支 c
        val c = repo.startEditTask(draft(), "C", true, 4_000L)
        gates[c] = CompletableDeferred()
        enqueueClipExport(context, c)

        // a 還在跑、c 排在後面還沒開始：一次只輸出一支
        assertEquals(WorkInfo.State.RUNNING, infos(a).single().state)
        assertEquals(WorkInfo.State.BLOCKED, infos(c).single().state)
        assertEquals(listOf(a), started.toList())

        // a 做完：b 的工作讀不到任務直接結束，接著才輪到 c
        gates.getValue(a).complete(Unit)
        waitUntil { started.size == 2 }
        assertEquals(listOf(a, c), started.toList())
        assertEquals(WorkInfo.State.SUCCEEDED, infos(a).single().state)
        assertEquals(WorkInfo.State.SUCCEEDED, infos(b).single().state)
        waitUntil { infos(c).single().state == WorkInfo.State.RUNNING }
    }

    @Test
    fun 取消串頭之後被連帶取消的任務能補排() = runBlocking {
        val a = repo.startEditTask(draft(), "A", true, 1_000L)
        val b = repo.startEditTask(draft(), "B", true, 1_000L)
        val c = repo.startEditTask(draft(), "C", true, 1_000L)
        enqueueClipExport(context, a)
        enqueueClipExport(context, b) // 排在 a 後面
        enqueueClipExport(context, c)
        waitUntil { infos(a).single().state == WorkInfo.State.RUNNING }

        cancelClipExport(context, a) // 取消正在跑的串頭：b、c 的工作會被連帶取消
        repo.deleteEditTask(a) // 畫面端取消導出會刪掉任務
        resumeClipExports(context, repo, 3_000L)

        // 資料庫裡 b、c 還是等待中，補排後各自有一個還活著的工作
        assertEquals(EditTaskState.QUEUED, repo.editTask(b).first()!!.state)
        assertEquals(EditTaskState.QUEUED, repo.editTask(c).first()!!.state)
        assertTrue(infos(b).any { !it.state.isFinished })
        assertTrue(infos(c).any { !it.state.isFinished })
        assertTrue(infos(a).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun 恢復時輸出中的改回等待並重排() = runBlocking {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 40, 2_000L) // app 被殺時停在輸出中

        resumeClipExports(context, repo, 3_000L)

        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.QUEUED, task.state)
        assertEquals(0, task.progress)
        assertTrue(!infos(id).single().state.isFinished)
    }

    @Test
    fun 恢復時上傳中的照樣排回串上且不改狀態() = runBlocking {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        repo.setEditTaskReadyToUpload(id, 0, 2_000L)
        repo.setEditTaskUploadUrl(id, "https://upload.example/session", 2_500L)
        repo.setEditTaskProgress(id, EditTaskState.UPLOADING, 40, 3_000L) // app 被殺時停在上傳中

        resumeClipExports(context, repo, 4_000L)

        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.UPLOADING, task.state) // 改回等待中會重新輸出、從 0 重傳
        assertEquals(40, task.progress)
        assertEquals("https://upload.example/session", task.uploadUrl)
        assertTrue(!infos(id).single().state.isFinished)
    }

    @Test
    fun 恢復時補排等storyboard() = runBlocking {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        repo.setEditTaskReadyToUpload(id, 0, 2_000L)
        repo.setEditTaskUploaded(id, "abc123def45", 3_000L) // 處理中，但輪詢工作被強制停止清掉了

        resumeClipExports(context, repo, 4_000L)

        assertTrue(infos(id).isEmpty()) // 不排上 clip-export 串
        val polls = workManager.getWorkInfosForUniqueWork("clip-storyboard-$id").get()
        assertEquals(1, polls.size)
        assertTrue(!polls.single().state.isFinished)
        assertEquals(EditTaskState.PROCESSING, repo.editTask(id).first()!!.state)
    }

    @Test
    fun 已經排著的不重複排() = runBlocking {
        val id = repo.startEditTask(draft(), "標題", true, 1_000L)
        enqueueClipExport(context, id)
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 40, 2_000L) // 工作真的在跑

        resumeClipExports(context, repo, 3_000L)

        assertEquals(1, infos(id).size)
        assertEquals(EditTaskState.RENDERING, repo.editTask(id).first()!!.state) // 也不動它的狀態
    }

    @Test
    fun 輸出完成或失敗的任務不會被排() = runBlocking {
        val done = repo.startEditTask(draft(), "完成", true, 1_000L)
        repo.setEditTaskRendered(done, 0, null, 2_000L)
        val failed = repo.startEditTask(draft(), "失敗", true, 1_000L)
        repo.setEditTaskFailed(failed, "輸出失敗", com.xenyaa.videoshot.data.repo.model.EditTaskStep.RENDER, 2_000L)

        resumeClipExports(context, repo, 3_000L)

        assertTrue(infos(done).isEmpty())
        assertTrue(infos(failed).isEmpty())
    }

    companion object {
        val started = CopyOnWriteArrayList<Long>()
        val running: MutableSet<Long> = ConcurrentHashMap.newKeySet()
        val gates = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

        @Volatile
        var tasks: FakeCacheRepo? = null
    }
}
