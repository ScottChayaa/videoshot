package com.xenyaa.videoshot.publish

import com.xenyaa.videoshot.core.timeline.ClipDraft
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.Segment
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.Timeline
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.edit.ClipExportNotifier
import com.xenyaa.videoshot.youtube.Youtube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) // 例外收斂時會呼叫 android.util.Log
@Config(sdk = [35])
class StoryboardPollerTest {

    private class FakeYoutube(var page: suspend () -> WatchPage) : Youtube {
        val asked = mutableListOf<String>()
        override suspend fun watchPage(videoId: String): WatchPage {
            asked += videoId
            return page()
        }
        override suspend fun sheet(url: String): ByteArray = throw AssertionError("不該下載 sheet")
    }

    private class FakeNotifier : ClipExportNotifier {
        val done = mutableListOf<EditTask>()
        val failed = mutableListOf<Pair<EditTask, String>>()
        override fun progress(task: EditTask, percent: Int) = throw AssertionError("不該通知進度")
        override fun finished(task: EditTask) = throw AssertionError("不該呼叫 finished")
        override fun done(task: EditTask) { done += task }
        override fun failed(task: EditTask, reason: String) { failed += task to reason }
    }

    private val repo = FakeCacheRepo()
    private val notifier = FakeNotifier()
    private val scheduled = mutableListOf<Pair<Long, Long>>()
    private var nowMs = 0L

    private fun page(result: FetchResult, spec: String? = null) = WatchPage(result, null, spec)

    private fun poller(youtube: Youtube, repo: FakeCacheRepo = this.repo) =
        StoryboardPoller(repo, youtube, notifier, { id, delay -> scheduled += id to delay }, { nowMs })

    private fun draft() = ClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1_000L, null, 1080, 1920, 0)),
        EditHistory(Timeline(listOf(Segment(1, 0, 3000)))),
    )

    /** 建一個在 [since] 進入處理中的任務（`updated_at` 就是進入那一刻）。 */
    private suspend fun processingTask(since: Long = 1_000L, on: FakeCacheRepo = repo): Long {
        val id = on.startEditTask(draft(), "標題", true, 0L)
        on.setEditTaskUploaded(id, "vid123", since)
        return id
    }

    @Test
    fun 有storyboard就完成並通知() = runTest {
        val id = processingTask()
        nowMs = 61_000L
        val youtube = FakeYoutube { page(FetchResult.OK, "spec") }

        poller(youtube).poll(id)

        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.DONE, task.state)
        assertEquals(listOf("vid123"), youtube.asked)
        assertEquals(listOf(task), notifier.done) // 通知的是寫完之後重讀的任務
        assertTrue(scheduled.isEmpty())
        assertTrue(notifier.failed.isEmpty())
    }

    @Test
    fun 還沒有就依經過時間排下一次() = runTest {
        val id = processingTask(since = 1_000L)
        val youtube = FakeYoutube { page(FetchResult.NO_STORYBOARD) }

        nowMs = 1_000L + 4 * 60_000L // 進入處理中 4 分鐘
        poller(youtube).poll(id)
        nowMs = 1_000L + 6 * 60_000L // 6 分鐘
        poller(youtube).poll(id)

        assertEquals(listOf(id to 30_000L, id to 120_000L), scheduled)
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.PROCESSING, task.state)
        assertEquals(1_000L, task.updatedAt) // 輪詢不改 updated_at，計時才不會重新起算
        assertTrue(notifier.done.isEmpty())
        assertTrue(notifier.failed.isEmpty())
    }

    @Test
    fun 超過60分鐘失敗於storyboard步驟() = runTest {
        val id = processingTask(since = 1_000L)
        nowMs = 1_000L + 60 * 60_000L
        val youtube = FakeYoutube { page(FetchResult.NO_STORYBOARD) }

        poller(youtube).poll(id)

        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.FAILED, task.state)
        assertEquals(EditTaskStep.STORYBOARD, task.failedStep)
        assertEquals("YouTube 還沒產生縮圖", task.failReason)
        assertEquals(listOf(task to "YouTube 還沒產生縮圖"), notifier.failed)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun 抓不到頁面也照樣排下一次() = runTest {
        for (result in listOf(FetchResult.FETCH_FAILED, FetchResult.VIDEO_UNAVAILABLE, FetchResult.PARSE_FAILED, FetchResult.RATE_LIMITED)) {
            scheduled.clear()
            val id = processingTask(since = 0L)
            nowMs = 10_000L

            poller(FakeYoutube { page(result) }).poll(id)

            assertEquals("$result", listOf(id to 30_000L), scheduled)
            assertEquals(EditTaskState.PROCESSING, repo.editTask(id).first()!!.state)
        }
    }

    @Test
    fun OK但沒有spec也當作還沒有() = runTest {
        val id = processingTask(since = 0L)
        nowMs = 10_000L

        poller(FakeYoutube { page(FetchResult.OK, null) }).poll(id)

        assertEquals(listOf(id to 30_000L), scheduled)
        assertEquals(EditTaskState.PROCESSING, repo.editTask(id).first()!!.state)
    }

    @Test
    fun 不是處理中就不動() = runTest {
        val queued = repo.startEditTask(draft(), "等待中", true, 0L)
        val failed = repo.startEditTask(draft(), "失敗", true, 0L)
        repo.setEditTaskFailed(failed, "輸出失敗", EditTaskStep.RENDER, 1_000L)
        val youtube = FakeYoutube { throw AssertionError("不該抓頁面") }

        poller(youtube).poll(queued)
        poller(youtube).poll(failed)
        poller(youtube).poll(999L) // 任務已被刪掉

        assertTrue(youtube.asked.isEmpty())
        assertTrue(scheduled.isEmpty())
        assertTrue(notifier.done.isEmpty() && notifier.failed.isEmpty())
        assertEquals(EditTaskState.QUEUED, repo.editTask(queued).first()!!.state)
    }

    @Test
    fun 等的時候任務被刪掉就不通知() = runTest {
        val id = processingTask()
        val youtube = FakeYoutube {
            repo.deleteEditTask(id) // 抓頁面的途中使用者取消了任務
            page(FetchResult.OK, "spec")
        }

        poller(youtube).poll(id)

        assertNull(repo.editTask(id).first())
        assertTrue(notifier.done.isEmpty())
    }

    @Test
    fun 例外不讓輪詢斷掉_照間隔再排下一次() = runTest {
        val id = processingTask(since = 0L)
        nowMs = 10_000L

        poller(FakeYoutube { throw IllegalStateException("壞掉了") }).poll(id)

        assertEquals(listOf(id to 30_000L), scheduled)
        assertEquals(EditTaskState.PROCESSING, repo.editTask(id).first()!!.state)
    }

    @Test
    fun 寫資料庫丟例外也照間隔再排下一次() = runTest {
        val throwing = object : FakeCacheRepo() {
            override suspend fun setEditTaskDone(id: Long, now: Long) = throw IllegalStateException("資料庫壞了")
        }
        val id = processingTask(since = 0L, on = throwing)
        nowMs = 400_000L // 6 分多鐘：間隔 2 分鐘

        poller(FakeYoutube { page(FetchResult.OK, "spec") }, throwing).poll(id)

        assertEquals(listOf(id to 120_000L), scheduled)
        assertTrue(notifier.done.isEmpty())
    }

    @Test
    fun 取消要繼續丟出去而不是當成例外() = runTest {
        val id = processingTask()

        var thrown: Throwable? = null
        try {
            poller(FakeYoutube { throw CancellationException("被取消") }).poll(id)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null)
        assertTrue(scheduled.isEmpty())
    }
}
