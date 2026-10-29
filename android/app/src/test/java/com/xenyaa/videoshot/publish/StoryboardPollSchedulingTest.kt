package com.xenyaa.videoshot.publish

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestDriver
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
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** 等 storyboard 的排程：用 work-testing 的 WorkManager，真的 [StoryboardPollWorker] 搭一個可以擋住的 `poll`。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryboardPollSchedulingTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var driver: TestDriver
    private val repo = FakeCacheRepo()

    /** 每次 `poll` 收到的任務編號。 */
    private val polled = CopyOnWriteArrayList<Long>()

    /** 有放閘門的編號：`poll` 等閘門放行才結束；沒放的永遠不結束。 */
    private val gates = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
    private val running: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == StoryboardPollWorker::class.java.name) {
                    StoryboardPollWorker(appContext, workerParameters) { id ->
                        polled += id
                        running += id
                        try {
                            gates[id]?.await() ?: awaitCancellation()
                        } finally {
                            running -= id
                        }
                    }
                } else {
                    null
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

    private fun infos(taskId: Long): List<WorkInfo> = workManager.getWorkInfosForUniqueWork("clip-storyboard-$taskId").get()

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "等太久" }
            Thread.sleep(10)
        }
    }

    /** 讓這個任務最早一個還沒開始的工作通過延遲與網路條件（測試模式不會自己等時間）。 */
    private fun release(info: WorkInfo) {
        driver.setInitialDelayMet(info.id)
        driver.setAllConstraintsMet(info.id)
    }

    private fun draft() = ClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1_000L, null, 1080, 1920, 0)),
        EditHistory(Timeline(listOf(Segment(1, 0, 3000)))),
    )

    @Test
    fun 排程要求有網路並帶延遲與任務編號() {
        enqueueStoryboardPoll(context, 5, 30_000L)

        val info = infos(5).single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(30_000L, info.initialDelayMillis)
        assertTrue(polled.isEmpty()) // 延遲還沒到不會跑

        release(info)
        waitUntil { polled.toList() == listOf(5L) } // input 的 task_id 有帶到工作裡
    }

    @Test
    fun 接在自己後面而不是取代() = runBlocking {
        gates[1] = CompletableDeferred()
        enqueueStoryboardPoll(context, 1, 0L)
        val first = infos(1).single()
        release(first)
        waitUntil { infos(1).single().state == WorkInfo.State.RUNNING }

        // 前一次還在跑時排下一次（真實流程：輪詢工作自己在 poll 裡排）
        enqueueStoryboardPoll(context, 1, 30_000L)

        // 前一次沒有被取消、仍在跑；下一次是接在它後面等著
        val all = infos(1)
        assertEquals(2, all.size)
        assertEquals(WorkInfo.State.RUNNING, all.single { it.id == first.id }.state)
        assertEquals(WorkInfo.State.BLOCKED, all.single { it.id != first.id }.state)
        assertEquals(listOf(1L), polled.toList())

        // 前一次成功結束，下一次才解除擋住、進入等延遲與網路
        gates.getValue(1).complete(Unit)
        waitUntil { infos(1).single { it.id == first.id }.state == WorkInfo.State.SUCCEEDED }
        val second = infos(1).single { it.id != first.id }
        waitUntil { infos(1).single { it.id != first.id }.state == WorkInfo.State.ENQUEUED }
        assertEquals(listOf(1L), polled.toList()) // 延遲還沒到，沒跑

        release(second)
        waitUntil { polled.toList() == listOf(1L, 1L) }
    }

    @Test
    fun 取消會收掉等待中的輪詢() {
        enqueueStoryboardPoll(context, 3, 30_000L)
        enqueueStoryboardPoll(context, 3, 30_000L) // 接在後面

        cancelStoryboardPoll(context, 3)

        assertTrue(infos(3).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun 補排只排沒有工作的() = runBlocking {
        val waiting = repo.startEditTask(draft(), "已排著", true, 0L)
        repo.setEditTaskUploaded(waiting, "v1", 1_000L)
        val orphan = repo.startEditTask(draft(), "沒有工作", true, 0L)
        repo.setEditTaskUploaded(orphan, "v2", 1_000L)
        val uploading = repo.startEditTask(draft(), "上傳中", true, 0L)
        repo.setEditTaskProgress(uploading, EditTaskState.UPLOADING, 50, 1_000L)
        val failed = repo.startEditTask(draft(), "失敗", true, 0L)
        repo.setEditTaskFailed(failed, "YouTube 還沒產生縮圖", EditTaskStep.STORYBOARD, 1_000L)
        enqueueStoryboardPoll(context, waiting, 30_000L)

        ensureStoryboardPolls(context, repo)

        assertEquals(1, infos(waiting).size) // 已經排著的不重複排
        val info = infos(orphan).single()
        assertTrue(!info.state.isFinished)
        assertEquals(0L, info.initialDelayMillis) // 補排的立刻查一次
        assertTrue(infos(uploading).isEmpty()) // 不是處理中的不排
        assertTrue(infos(failed).isEmpty())
    }

    @Test
    fun 補排時上一輪已經結束的算沒有工作() = runBlocking {
        val id = repo.startEditTask(draft(), "標題", true, 0L)
        repo.setEditTaskUploaded(id, "v1", 1_000L)
        enqueueStoryboardPoll(context, id, 30_000L)
        workManager.cancelUniqueWork("clip-storyboard-$id").result.get() // 例如被強制停止後系統丟掉

        ensureStoryboardPolls(context, repo)

        assertTrue(infos(id).any { !it.state.isFinished })
    }
}
