package com.xenyaa.videoshot.data.repo

import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `FakeCacheRepo` 的上傳相關行為要跟 Room 版一致（後面任務的 JVM 測試都靠它）。
 * 這裡的案例和 androidTest 的 `EditTaskRepoTest` 一一對應。
 */
class FakeCacheRepoEditTaskTest {

    private val repo = FakeCacheRepo()
    private val draft = newClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1000, null, 1080, 1920, 0)),
    )

    private suspend fun start(title: String = "標題", autoUpload: Boolean = true) =
        repo.startEditTask(draft, title, autoUpload, now = 100)

    private suspend fun task(id: Long) = repo.editTask(id).first()!!

    @Test fun 重試依失敗步驟回到對的狀態() = runTest {
        val render = start("R")
        repo.setEditTaskFailed(render, "輸出失敗", EditTaskStep.RENDER, now = 110)
        val upload = start("U")
        repo.setEditTaskReadyToUpload(upload, skipped = 0, now = 105)
        repo.setEditTaskProgress(upload, EditTaskState.UPLOADING, 63, now = 106)
        repo.setEditTaskUploadUrl(upload, "https://upload/u", now = 107)
        repo.setEditTaskFailed(upload, "沒有網路連線", EditTaskStep.UPLOAD, now = 110)
        val storyboard = start("S")
        repo.setEditTaskUploaded(storyboard, "vid", now = 105)
        repo.setEditTaskFailed(storyboard, "YouTube 還沒產生縮圖", EditTaskStep.STORYBOARD, now = 110)

        assertEquals(EditTaskState.QUEUED, repo.retryEditTask(render, now = 200))
        assertEquals(EditTaskState.UPLOADING, repo.retryEditTask(upload, now = 200))
        assertEquals(EditTaskState.PROCESSING, repo.retryEditTask(storyboard, now = 200))

        task(render).let {
            assertEquals(EditTaskState.QUEUED, it.state)
            assertEquals(0, it.progress)
            assertNull(it.failReason)
            assertNull(it.failedStep)
        }
        task(upload).let {
            assertEquals(EditTaskState.UPLOADING, it.state)
            assertEquals(63, it.progress) // 接著傳，進度不退回 0
            assertNull(it.failedStep)
            assertEquals("https://upload/u", it.uploadUrl)
        }
        task(storyboard).let {
            assertEquals(EditTaskState.PROCESSING, it.state)
            assertEquals("vid", it.videoId)
            assertNull(it.failReason)
            assertEquals(200L, it.updatedAt)
        }
    }

    @Test fun 重試對不是失敗或不存在的任務回null且不動它() = runTest {
        val id = start()
        assertNull(repo.retryEditTask(id, now = 200))
        assertNull(repo.retryEditTask(999, now = 200))
        assertEquals(EditTaskState.QUEUED, task(id).state)
        assertEquals(100L, task(id).updatedAt)
    }

    @Test fun 中斷時只有輸出中改回等待中() = runTest {
        val rendering = start("R")
        repo.setEditTaskProgress(rendering, EditTaskState.RENDERING, 40, now = 110)
        val uploading = start("U")
        repo.setEditTaskReadyToUpload(uploading, skipped = 0, now = 110)
        repo.setEditTaskUploadUrl(uploading, "https://upload/u", now = 120)
        repo.setEditTaskProgress(uploading, EditTaskState.UPLOADING, 55, now = 130)
        val processing = start("P")
        repo.setEditTaskUploaded(processing, "vid", now = 110)

        repo.requeueEditTask(rendering, now = 200)
        repo.requeueEditTask(uploading, now = 200)
        repo.requeueEditTask(processing, now = 200)

        assertEquals(EditTaskState.QUEUED, task(rendering).state)
        assertEquals(0, task(rendering).progress)
        task(uploading).let {
            assertEquals(EditTaskState.UPLOADING, it.state)
            assertEquals(55, it.progress)
            assertEquals("https://upload/u", it.uploadUrl)
        }
        assertEquals(EditTaskState.PROCESSING, task(processing).state)
    }

    @Test fun 上傳完清掉網址與失敗原因() = runTest {
        val id = start()
        repo.setEditTaskUploadUrl(id, "https://upload/1", now = 110)
        repo.setEditTaskFailed(id, "上傳失敗", EditTaskStep.UPLOAD, now = 120)

        repo.setEditTaskUploaded(id, "abcDEF12345", now = 130)

        task(id).let {
            assertEquals(EditTaskState.PROCESSING, it.state)
            assertEquals(100, it.progress)
            assertEquals("abcDEF12345", it.videoId)
            assertNull(it.uploadUrl)
            assertNull(it.failReason)
            assertNull(it.failedStep)
            assertEquals(130L, it.updatedAt)
        }
    }

    @Test fun 待處理與處理中與已完成數() = runTest {
        val a = start("A")
        val b = start("B")
        val c = start("C")
        val d = start("D")
        val e = start("E")
        repo.setEditTaskProgress(b, EditTaskState.RENDERING, 10, now = 110)
        repo.setEditTaskRendered(c, 0, null, now = 110)
        repo.setEditTaskReadyToUpload(d, skipped = 0, now = 110)
        repo.setEditTaskUploaded(e, "ve", now = 110)

        assertEquals(listOf(a, b, d), repo.pendingEditTaskIds())
        assertEquals(listOf(e), repo.processingEditTaskIds())
        assertEquals(0, repo.doneEditTaskCount().first())
        repo.setEditTaskDone(e, now = 120)
        assertEquals(1, repo.doneEditTaskCount().first())
        assertEquals(emptyList<Long>(), repo.processingEditTaskIds())
    }

    @Test fun 對已刪的任務寫上傳欄位不會復活() = runTest {
        val id = start()
        repo.deleteEditTask(id)

        repo.setEditTaskUploadUrl(id, "https://upload/1", now = 110)
        repo.setEditTaskReadyToUpload(id, skipped = 0, now = 120)
        repo.setEditTaskUploaded(id, "vid", now = 130)
        repo.setEditTaskDone(id, now = 140)

        assertNull(repo.editTask(id).first())
        assertEquals(emptyList<Long>(), repo.pendingEditTaskIds())
    }

    @Test fun 自動上傳只在等待中與輸出中改得動() = runTest {
        val id = start(autoUpload = true)
        repo.setEditTaskUploaded(id, "vid", now = 130) // 處理中

        repo.setEditTaskAutoUpload(id, false, now = 200)

        task(id).let {
            assertEquals(true, it.autoUpload)
            assertEquals(130L, it.updatedAt) // 處理中的計時靠 updated_at，不能被動到
        }
    }

    @Test fun 開始任務可以指定不自動上傳() = runTest {
        assertFalse(task(start(autoUpload = false)).autoUpload)
    }
    @Test fun 只有上傳中能進等待() = runTest {
        val uploading = start("U")
        repo.setEditTaskReadyToUpload(uploading, skipped = 0, now = 105)
        repo.setEditTaskProgress(uploading, EditTaskState.UPLOADING, 0, now = 106)
        repo.setEditTaskUploadUrl(uploading, "https://upload/u", now = 107)
        val queued = start("Q")
        val processing = start("P")
        repo.setEditTaskUploaded(processing, "vid", now = 105)
        val failed = start("F")
        repo.setEditTaskFailed(failed, "沒有網路連線", EditTaskStep.UPLOAD, now = 105)

        for (id in listOf(uploading, queued, processing, failed)) repo.setEditTaskWaitingQuota(id, "額度", now = 200)

        task(uploading).let {
            assertEquals(EditTaskState.WAITING_QUOTA, it.state)
            assertEquals("額度", it.failReason)
            assertNull(it.failedStep)
            assertEquals("https://upload/u", it.uploadUrl) // 保留，重置後接著傳
            assertEquals(200L, it.updatedAt) // 撞到上限的時間，重置時間從它算
        }
        assertEquals(EditTaskState.QUEUED, task(queued).state)
        assertEquals(EditTaskState.PROCESSING, task(processing).state)
        task(failed).let {
            assertEquals(EditTaskState.FAILED, it.state)
            assertEquals("沒有網路連線", it.failReason)
        }
        // 等待中不算待處理（沒有工作在跑）、不算已完成
        assertEquals(listOf(queued), repo.pendingEditTaskIds())
        assertEquals(listOf(uploading), repo.waitingQuotaEditTaskIds())
        assertEquals(0, repo.doneEditTaskCount().first())
    }

    @Test fun 只有等待中能回上傳中() = runTest {
        val waiting = start("W")
        repo.setEditTaskReadyToUpload(waiting, skipped = 0, now = 105)
        repo.setEditTaskUploadUrl(waiting, "https://upload/w", now = 106)
        repo.setEditTaskWaitingQuota(waiting, "額度", now = 200)
        val uploading = start("U")
        repo.setEditTaskReadyToUpload(uploading, skipped = 0, now = 105)

        // 等待期間別的寫入不動 updated_at
        repo.requeueEditTask(waiting, now = 250)
        repo.setEditTaskAutoUpload(waiting, false, now = 260)
        assertEquals(200L, task(waiting).updatedAt)

        assertEquals(true, repo.resumeEditTaskFromQuota(waiting, now = 300))
        assertEquals(false, repo.resumeEditTaskFromQuota(waiting, now = 310)) // 已經回上傳中了
        assertEquals(false, repo.resumeEditTaskFromQuota(uploading, now = 300))
        assertEquals(false, repo.resumeEditTaskFromQuota(999, now = 300))

        task(waiting).let {
            assertEquals(EditTaskState.UPLOADING, it.state)
            assertNull(it.failReason)
            assertEquals("https://upload/w", it.uploadUrl)
            assertEquals(300L, it.updatedAt)
        }
        assertEquals(105L, task(uploading).updatedAt)
        assertEquals(emptyList<Long>(), repo.waitingQuotaEditTaskIds())
    }
}
