package com.xenyaa.videoshot.data

import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenyaa.videoshot.core.timeline.ClipDraftCodec
import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.EditHistory
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.data.cache.CacheDatabase
import com.xenyaa.videoshot.data.repo.RoomCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 剪輯輸出任務（`cache.db` 的 `edit_task`）。一律用 in-memory 的 DB，不碰手機上真正的 cache.db。 */
@RunWith(AndroidJUnit4::class)
class EditTaskRepoTest {

    private lateinit var db: CacheDatabase
    private lateinit var repo: RoomCacheRepo

    @Before fun setUp() {
        db = inMemoryCacheDb()
        repo = RoomCacheRepo(db, Dispatchers.IO)
    }

    @After fun tearDown() { db.close() }

    private val draft = newClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(
            ClipSource(1, "content://media/1", SourceKind.PHOTO, 1000, null, 1080, 1920, 0),
            ClipSource(2, "content://media/2", SourceKind.VIDEO, 2000, 5000, 1920, 1080, 90),
        ),
    )

    private suspend fun rowCount(table: String): Long =
        db.useWriterConnection { c -> c.usePrepared("SELECT COUNT(*) FROM $table") { s -> s.step(); s.getLong(0) } }

    @Test fun 開始任務同時清掉草稿() = runTest {
        repo.saveClipDraft(draft, now = 1)
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)

        assertNull(repo.currentClipDraft())
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.QUEUED, task.state)
        assertEquals(0, task.progress)
        assertEquals("標題", task.title)
        assertEquals(draft, task.draft)
        assertEquals(draft.timeline.durationMs, task.durationMs)
        assertTrue(task.autoUpload)
        assertNull(task.failReason)
        assertEquals(0, task.skippedSources)
        assertNull(task.galleryUri)
        assertEquals(100L, task.createdAt)
    }

    @Test fun 任務裡的草稿只留目前的時間軸() = runTest {
        val edited = draft.copy(history = draft.history.apply(draft.timeline.copy(segments = draft.timeline.segments.drop(1))))
        assertTrue(edited.history.canUndo)
        val id = repo.startEditTask(edited, "標題", autoUpload = true, now = 100)

        val saved = repo.editTask(id).first()!!.draft
        assertFalse(saved.history.canUndo)
        assertEquals(EditHistory(edited.timeline), saved.history)
    }

    @Test fun 任務寫入失敗時草稿還在() = runTest {
        repo.saveClipDraft(draft, now = 1)
        // 讓 edit_task 的插入一定失敗；交易整個倒回，草稿不能被清掉
        db.useWriterConnection { c ->
            c.usePrepared("CREATE TRIGGER no_insert BEFORE INSERT ON edit_task BEGIN SELECT RAISE(ABORT, '寫不進去'); END") { it.step() }
        }
        try {
            repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
            fail("插入被擋，應該丟例外")
        } catch (_: Exception) {
            // 預期
        }
        assertEquals(draft, repo.currentClipDraft())
        assertEquals(0L, rowCount("edit_task"))
    }

    @Test fun 清單新的在前() = runTest {
        val a = repo.startEditTask(draft, "A", autoUpload = true, now = 100)
        val b = repo.startEditTask(draft, "B", autoUpload = true, now = 200)
        val c = repo.startEditTask(draft, "C", autoUpload = true, now = 300)
        assertEquals(listOf(c, b, a), repo.editTasks().first().map { it.id })
    }

    @Test fun 進度與完成與失敗寫得進去() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)

        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 42, now = 110)
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.RENDERING, it.state)
            assertEquals(42, it.progress)
        }

        repo.setEditTaskAutoUpload(id, false, now = 115)
        assertFalse(repo.editTask(id).first()!!.autoUpload)

        repo.setEditTaskRendered(id, skipped = 2, galleryUri = "content://media/9", now = 120)
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.RENDERED, it.state)
            assertEquals(100, it.progress)
            assertEquals(2, it.skippedSources)
            assertEquals("content://media/9", it.galleryUri)
        }

        repo.setEditTaskFailed(id, "輸出失敗", EditTaskStep.RENDER, now = 130)
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.FAILED, it.state)
            assertEquals("輸出失敗", it.failReason)
        }

        assertEquals(EditTaskStep.RENDER, repo.editTask(id).first()!!.failedStep)

        assertEquals(EditTaskState.QUEUED, repo.retryEditTask(id, now = 140))
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.QUEUED, it.state)
            assertEquals(0, it.progress)
            assertNull(it.failReason)
            assertNull(it.failedStep)
            assertEquals(140L, it.updatedAt)
        }
    }

    @Test fun 完成時存相簿的uri可以是null() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        repo.setEditTaskRendered(id, skipped = 0, galleryUri = null, now = 120)
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.RENDERED, task.state)
        assertNull(task.galleryUri)
    }

    @Test fun 刪掉後再寫進度不會復活() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        repo.deleteEditTask(id)

        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 50, now = 110)
        repo.setEditTaskRendered(id, 0, null, now = 120)
        repo.setEditTaskFailed(id, "輸出失敗", EditTaskStep.RENDER, now = 130)
        repo.setEditTaskAutoUpload(id, false, now = 140)
        repo.requeueEditTask(id, now = 150)
        repo.setEditTaskReadyToUpload(id, skipped = 1, now = 160)
        repo.setEditTaskUploadUrl(id, "https://upload/1", now = 170)
        repo.setEditTaskUploaded(id, "vid", now = 180)
        repo.setEditTaskDone(id, now = 190)
        assertNull(repo.retryEditTask(id, now = 200))

        assertNull(repo.editTask(id).first())
        assertTrue(repo.editTasks().first().isEmpty())
        assertEquals(0L, rowCount("edit_task"))
    }

    @Test fun 待處理只列等待與輸出中與上傳中_舊的在前() = runTest {
        val a = repo.startEditTask(draft, "A", autoUpload = true, now = 100)
        val b = repo.startEditTask(draft, "B", autoUpload = true, now = 200)
        val c = repo.startEditTask(draft, "C", autoUpload = true, now = 300)
        val d = repo.startEditTask(draft, "D", autoUpload = true, now = 400)
        repo.setEditTaskProgress(b, EditTaskState.RENDERING, 10, now = 410)
        repo.setEditTaskRendered(c, 0, null, now = 420)
        repo.setEditTaskFailed(d, "輸出失敗", EditTaskStep.RENDER, now = 430)
        val e = repo.startEditTask(draft, "E", autoUpload = true, now = 500)
        repo.setEditTaskRendered(e, 0, null, now = 510)
        repo.setEditTaskReadyToUpload(e, skipped = 0, now = 520)
        val f = repo.startEditTask(draft, "F", autoUpload = true, now = 600)
        repo.setEditTaskUploaded(f, "vid", now = 610)

        assertEquals(listOf(a, b, e), repo.pendingEditTaskIds())
        assertEquals(listOf(f), repo.processingEditTaskIds())
    }

    @Test fun 清單會隨寫入更新() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        val before = repo.editTasks().first()
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 7, now = 110)
        val after = repo.editTasks().first()
        assertEquals(0, before.single().progress)
        assertEquals(7, after.single().progress)
    }

    @Test fun 重試依失敗步驟回到對的狀態() = runTest {
        val render = repo.startEditTask(draft, "R", autoUpload = true, now = 100)
        repo.setEditTaskFailed(render, "輸出失敗", EditTaskStep.RENDER, now = 110)
        val upload = repo.startEditTask(draft, "U", autoUpload = true, now = 100)
        repo.setEditTaskReadyToUpload(upload, skipped = 0, now = 105)
        repo.setEditTaskProgress(upload, EditTaskState.UPLOADING, 63, now = 106)
        repo.setEditTaskUploadUrl(upload, "https://upload/u", now = 107)
        repo.setEditTaskFailed(upload, "沒有網路連線", EditTaskStep.UPLOAD, now = 110)
        val storyboard = repo.startEditTask(draft, "S", autoUpload = true, now = 100)
        repo.setEditTaskUploaded(storyboard, "vid", now = 105)
        repo.setEditTaskFailed(storyboard, "YouTube 還沒產生縮圖", EditTaskStep.STORYBOARD, now = 110)

        assertEquals(EditTaskState.QUEUED, repo.retryEditTask(render, now = 200))
        assertEquals(EditTaskState.UPLOADING, repo.retryEditTask(upload, now = 200))
        assertEquals(EditTaskState.PROCESSING, repo.retryEditTask(storyboard, now = 200))

        repo.editTask(render).first()!!.let {
            assertEquals(EditTaskState.QUEUED, it.state)
            assertEquals(0, it.progress)
            assertNull(it.failReason)
            assertNull(it.failedStep)
        }
        repo.editTask(upload).first()!!.let {
            assertEquals(EditTaskState.UPLOADING, it.state)
            assertEquals(63, it.progress) // 接著傳，進度不退回 0
            assertNull(it.failReason)
            assertNull(it.failedStep)
            assertEquals("https://upload/u", it.uploadUrl) // 從斷掉的地方接著傳
        }
        repo.editTask(storyboard).first()!!.let {
            assertEquals(EditTaskState.PROCESSING, it.state)
            assertEquals("vid", it.videoId)
            assertNull(it.failReason)
            assertNull(it.failedStep)
            assertEquals(200L, it.updatedAt) // 重新計時
        }
    }

    @Test fun 重試對不是失敗或不存在的任務回null且不動它() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        assertNull(repo.retryEditTask(id, now = 200))
        assertNull(repo.retryEditTask(999, now = 200))
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.QUEUED, it.state)
            assertEquals(100L, it.updatedAt)
        }
    }

    @Test fun 失敗步驟是null或不認識時重試當成輸出() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        db.useWriterConnection { c ->
            c.usePrepared("UPDATE edit_task SET state = 'failed', fail_reason = 'x', failed_step = NULL WHERE id = $id") { it.step() }
        }
        assertNull(repo.editTask(id).first()!!.failedStep)
        assertEquals(EditTaskState.QUEUED, repo.retryEditTask(id, now = 200))

        db.useWriterConnection { c ->
            c.usePrepared("UPDATE edit_task SET state = 'failed', fail_reason = 'x', failed_step = 'zzz' WHERE id = $id") { it.step() }
        }
        assertEquals(EditTaskStep.RENDER, repo.editTask(id).first()!!.failedStep)
        assertEquals(EditTaskState.QUEUED, repo.retryEditTask(id, now = 300))
    }

    @Test fun 中斷時只有輸出中改回等待中() = runTest {
        val rendering = repo.startEditTask(draft, "R", autoUpload = true, now = 100)
        repo.setEditTaskProgress(rendering, EditTaskState.RENDERING, 40, now = 110)
        val uploading = repo.startEditTask(draft, "U", autoUpload = true, now = 100)
        repo.setEditTaskReadyToUpload(uploading, skipped = 0, now = 110)
        repo.setEditTaskUploadUrl(uploading, "https://upload/u", now = 120)
        repo.setEditTaskProgress(uploading, EditTaskState.UPLOADING, 55, now = 130)
        val processing = repo.startEditTask(draft, "P", autoUpload = true, now = 100)
        repo.setEditTaskUploaded(processing, "vid", now = 110)

        repo.requeueEditTask(rendering, now = 200)
        repo.requeueEditTask(uploading, now = 200)
        repo.requeueEditTask(processing, now = 200)

        repo.editTask(rendering).first()!!.let {
            assertEquals(EditTaskState.QUEUED, it.state)
            assertEquals(0, it.progress)
        }
        repo.editTask(uploading).first()!!.let {
            assertEquals(EditTaskState.UPLOADING, it.state)
            assertEquals(55, it.progress)
            assertEquals("https://upload/u", it.uploadUrl)
        }
        assertEquals(EditTaskState.PROCESSING, repo.editTask(processing).first()!!.state)
    }

    @Test fun 輸出完成後準備上傳會歸零進度並記略過數() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        repo.setEditTaskRendered(id, skipped = 0, galleryUri = "content://media/9", now = 110)
        repo.setEditTaskReadyToUpload(id, skipped = 2, now = 120)
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.UPLOADING, it.state)
            assertEquals(0, it.progress)
            assertEquals(2, it.skippedSources)
            assertEquals(120L, it.updatedAt)
        }
    }

    @Test fun 上傳網址可以寫入與清掉() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        repo.setEditTaskUploadUrl(id, "https://upload/1", now = 110)
        assertEquals("https://upload/1", repo.editTask(id).first()!!.uploadUrl)
        repo.setEditTaskUploadUrl(id, null, now = 120)
        assertNull(repo.editTask(id).first()!!.uploadUrl)
    }

    @Test fun 上傳完清掉網址與失敗原因() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        repo.setEditTaskUploadUrl(id, "https://upload/1", now = 110)
        repo.setEditTaskFailed(id, "上傳失敗", EditTaskStep.UPLOAD, now = 120)

        repo.setEditTaskUploaded(id, "abcDEF12345", now = 130)

        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.PROCESSING, it.state)
            assertEquals(100, it.progress)
            assertEquals("abcDEF12345", it.videoId)
            assertNull(it.uploadUrl)
            assertNull(it.failReason)
            assertNull(it.failedStep)
            assertEquals(130L, it.updatedAt)
        }
    }

    @Test fun 已完成數只算done() = runTest {
        assertEquals(0, repo.doneEditTaskCount().first())
        val a = repo.startEditTask(draft, "A", autoUpload = true, now = 100)
        val b = repo.startEditTask(draft, "B", autoUpload = true, now = 100)
        val c = repo.startEditTask(draft, "C", autoUpload = true, now = 100)
        val d = repo.startEditTask(draft, "D", autoUpload = true, now = 100)
        repo.setEditTaskUploaded(a, "va", now = 110)
        repo.setEditTaskDone(a, now = 120)
        repo.setEditTaskUploaded(b, "vb", now = 110) // 還在處理中
        repo.setEditTaskFailed(c, "上傳失敗", EditTaskStep.UPLOAD, now = 110)
        repo.setEditTaskRendered(d, 0, null, now = 110)

        assertEquals(1, repo.doneEditTaskCount().first())
        repo.setEditTaskDone(b, now = 130)
        assertEquals(2, repo.doneEditTaskCount().first())
    }

    @Test fun 對已刪的任務寫上傳網址不會復活() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        repo.deleteEditTask(id)

        repo.setEditTaskUploadUrl(id, "https://upload/1", now = 110)
        repo.setEditTaskReadyToUpload(id, skipped = 0, now = 120)
        repo.setEditTaskUploaded(id, "vid", now = 130)
        repo.setEditTaskDone(id, now = 140)

        assertNull(repo.editTask(id).first())
        assertEquals(0L, rowCount("edit_task"))
    }

    @Test fun 自動上傳只在等待中與輸出中改得動() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        repo.setEditTaskUploaded(id, "vid", now = 130) // 處理中

        repo.setEditTaskAutoUpload(id, false, now = 200)

        repo.editTask(id).first()!!.let {
            assertTrue(it.autoUpload)
            assertEquals(130L, it.updatedAt) // 處理中的計時靠 updated_at，不能被動到
        }
    }

    @Test fun 開始任務可以指定不自動上傳() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = false, now = 100)
        assertFalse(repo.editTask(id).first()!!.autoUpload)
    }

    @Test fun payload存的是編碼後的草稿() = runTest {
        val id = repo.startEditTask(draft, "標題", autoUpload = true, now = 100)
        val payload = db.useWriterConnection { c ->
            c.usePrepared("SELECT payload FROM edit_task WHERE id = $id") { s -> s.step(); s.getText(0) }
        }
        assertEquals(draft, ClipDraftCodec.decode(payload))
    }
}
