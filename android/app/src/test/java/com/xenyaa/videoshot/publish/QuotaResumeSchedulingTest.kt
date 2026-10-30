package com.xenyaa.videoshot.publish

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.xenyaa.videoshot.core.publish.nextQuotaResetMs
import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.edit.ClipExportWorker
import com.xenyaa.videoshot.edit.resumeClipExports
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 超過每日上傳上限後的等待與接回：用 work-testing 的 WorkManager，真的 [QuotaResumeWorker]；
 * `clip-export` 串上的工作換成一直跑著的替身（只記下收到的任務編號），才看得到「接回串上」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuotaResumeSchedulingTest {

    class HangingExport(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = inputData.getLong("task_id", -1L)
            exported += id
            running += id
            try {
                awaitCancellation()
            } finally {
                running -= id
            }
        }
    }

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var driver: TestDriver
    private val repo = FakeCacheRepo()
    private var nowMs = 0L

    /** 被收掉結果通知（「隔天會自動接著上傳」）的任務。 */
    private val dismissed = CopyOnWriteArrayList<Long>()

    @Before
    fun setUp() {
        exported.clear()
        context = ApplicationProvider.getApplicationContext()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                when (workerClassName) {
                    QuotaResumeWorker::class.java.name ->
                        QuotaResumeWorker(appContext, workerParameters, repo, { nowMs }, dismissResult = { dismissed += it })
                    ClipExportWorker::class.java.name -> HangingExport(appContext, workerParameters)
                    else -> null
                }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(factory).setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
        driver = WorkManagerTestInitHelper.getTestDriver(context)!!
    }

    /** 沒結束的工作全部取消並等它們真的停下，不然例外會漏到之後別的測試。 */
    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
        waitUntil { running.isEmpty() }
    }

    private fun quotaInfos(taskId: Long): List<WorkInfo> = workManager.getWorkInfosForUniqueWork("clip-quota-$taskId").get()
    private fun exportInfos(taskId: Long): List<WorkInfo> = workManager.getWorkInfosByTag("clip-export-$taskId").get()

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "等太久" }
            Thread.sleep(10)
        }
    }

    private fun draft() = ClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1_000L, null, 1080, 1920, 0)),
        EditHistory(Timeline(listOf(Segment(1, 0, 3000)))),
    )

    /** 建一個在 [hitAt] 撞到上限、停在等待中的任務。 */
    private suspend fun waitingTask(hitAt: Long): Long {
        val id = repo.startEditTask(draft(), "等明天的", true, 1_000L)
        repo.setEditTaskReadyToUpload(id, 0, 2_000L)
        repo.setEditTaskUploadUrl(id, "https://upload/session", 2_500L)
        repo.setEditTaskWaitingQuota(id, REASON_QUOTA, hitAt)
        return id
    }

    @Test
    fun 排程要求有網路並帶延遲_再排一次取代舊的() {
        enqueueQuotaResume(context, 5, 60_000L)
        val first = quotaInfos(5).single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertEquals(60_000L, first.initialDelayMillis)

        enqueueQuotaResume(context, 5, 30_000L)

        val alive = quotaInfos(5).filter { !it.state.isFinished }
        assertEquals(30_000L, alive.single().initialDelayMillis)
    }

    @Test
    fun 重置後接回輸出串() = runBlocking {
        val id = waitingTask(hitAt = 10_000L)
        enqueueQuotaResume(context, id, 60_000L)
        assertTrue(exported.isEmpty()) // 延遲還沒到

        nowMs = 70_000L
        val info = quotaInfos(id).single()
        driver.setInitialDelayMet(info.id)
        driver.setAllConstraintsMet(info.id)

        waitUntil { exported.toList() == listOf(id) }
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.UPLOADING, task.state)
        assertNull(task.failReason)
        assertEquals("https://upload/session", task.uploadUrl) // 從存著的網址接著傳
        assertTrue(exportInfos(id).any { !it.state.isFinished })
        // 接回時收掉「隔天會自動接著上傳」那則通知，不跟上傳中的說法矛盾
        assertEquals(listOf(id), dismissed.toList())
    }

    @Test
    fun 等待中被取消的任務重置後不會接回() = runBlocking {
        val id = waitingTask(hitAt = 10_000L)
        enqueueQuotaResume(context, id, 60_000L)
        repo.deleteEditTask(id)

        val info = quotaInfos(id).single()
        driver.setInitialDelayMet(info.id)
        driver.setAllConstraintsMet(info.id)

        waitUntil { quotaInfos(id).single().state == WorkInfo.State.SUCCEEDED }
        assertTrue(exportInfos(id).isEmpty())
        assertNull(repo.editTask(id).first())
        assertTrue(dismissed.isEmpty())
    }

    @Test
    fun 恢復時過了重置時間的接著傳() = runBlocking {
        val hitAt = 10_000L
        val id = waitingTask(hitAt)

        resumeClipExports(context, repo, nextQuotaResetMs(hitAt) + 1) { dismissed += it }

        assertEquals(EditTaskState.UPLOADING, repo.editTask(id).first()!!.state)
        assertEquals(listOf(id), dismissed.toList()) // 收掉「隔天會自動接著上傳」
        assertTrue(exportInfos(id).any { !it.state.isFinished })
        waitUntil { exported.toList() == listOf(id) }
    }

    @Test
    fun 恢復時還沒到的補排等待工作() = runBlocking {
        val hitAt = 10_000L
        val id = waitingTask(hitAt)
        val now = hitAt + 1_000L

        resumeClipExports(context, repo, now) { dismissed += it } // 例如重開機、強制停止後等待工作被清掉

        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.WAITING_QUOTA, task.state)
        assertEquals(hitAt, task.updatedAt) // 等待期間不寫 updated_at
        assertTrue(exportInfos(id).isEmpty()) // 不排上輸出串
        val info = quotaInfos(id).single()
        assertTrue(!info.state.isFinished)
        assertEquals(nextQuotaResetMs(hitAt) - now, info.initialDelayMillis)
        assertTrue(dismissed.isEmpty()) // 還在等，通知留著
    }

    @Test
    fun 恢復時已經排著的等待工作不動() = runBlocking {
        val id = waitingTask(hitAt = 10_000L)
        enqueueQuotaResume(context, id, 60_000L)
        val before = quotaInfos(id).single().id

        resumeClipExports(context, repo, 11_000L)

        assertEquals(before, quotaInfos(id).single().id)
    }

    companion object {
        val exported = CopyOnWriteArrayList<Long>()
        val running: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    }
}
