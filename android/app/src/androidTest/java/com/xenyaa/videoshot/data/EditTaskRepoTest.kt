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
        val id = repo.startEditTask(draft, "標題", now = 100)

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
        val id = repo.startEditTask(edited, "標題", now = 100)

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
            repo.startEditTask(draft, "標題", now = 100)
            fail("插入被擋，應該丟例外")
        } catch (_: Exception) {
            // 預期
        }
        assertEquals(draft, repo.currentClipDraft())
        assertEquals(0L, rowCount("edit_task"))
    }

    @Test fun 清單新的在前() = runTest {
        val a = repo.startEditTask(draft, "A", now = 100)
        val b = repo.startEditTask(draft, "B", now = 200)
        val c = repo.startEditTask(draft, "C", now = 300)
        assertEquals(listOf(c, b, a), repo.editTasks().first().map { it.id })
    }

    @Test fun 進度與完成與失敗寫得進去() = runTest {
        val id = repo.startEditTask(draft, "標題", now = 100)

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

        repo.setEditTaskFailed(id, "輸出失敗", now = 130)
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.FAILED, it.state)
            assertEquals("輸出失敗", it.failReason)
        }

        repo.requeueEditTask(id, now = 140)
        repo.editTask(id).first()!!.let {
            assertEquals(EditTaskState.QUEUED, it.state)
            assertEquals(0, it.progress)
            assertNull(it.failReason)
        }
    }

    @Test fun 完成時存相簿的uri可以是null() = runTest {
        val id = repo.startEditTask(draft, "標題", now = 100)
        repo.setEditTaskRendered(id, skipped = 0, galleryUri = null, now = 120)
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.RENDERED, task.state)
        assertNull(task.galleryUri)
    }

    @Test fun 刪掉後再寫進度不會復活() = runTest {
        val id = repo.startEditTask(draft, "標題", now = 100)
        repo.deleteEditTask(id)

        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 50, now = 110)
        repo.setEditTaskRendered(id, 0, null, now = 120)
        repo.setEditTaskFailed(id, "輸出失敗", now = 130)
        repo.setEditTaskAutoUpload(id, false, now = 140)
        repo.requeueEditTask(id, now = 150)

        assertNull(repo.editTask(id).first())
        assertTrue(repo.editTasks().first().isEmpty())
        assertEquals(0L, rowCount("edit_task"))
    }

    @Test fun 待處理只列等待與輸出中_舊的在前() = runTest {
        val a = repo.startEditTask(draft, "A", now = 100)
        val b = repo.startEditTask(draft, "B", now = 200)
        val c = repo.startEditTask(draft, "C", now = 300)
        val d = repo.startEditTask(draft, "D", now = 400)
        repo.setEditTaskProgress(b, EditTaskState.RENDERING, 10, now = 410)
        repo.setEditTaskRendered(c, 0, null, now = 420)
        repo.setEditTaskFailed(d, "輸出失敗", now = 430)

        assertEquals(listOf(a, b), repo.pendingEditTaskIds())
    }

    @Test fun 清單會隨寫入更新() = runTest {
        val id = repo.startEditTask(draft, "標題", now = 100)
        val before = repo.editTasks().first()
        repo.setEditTaskProgress(id, EditTaskState.RENDERING, 7, now = 110)
        val after = repo.editTasks().first()
        assertEquals(0, before.single().progress)
        assertEquals(7, after.single().progress)
    }

    @Test fun payload存的是編碼後的草稿() = runTest {
        val id = repo.startEditTask(draft, "標題", now = 100)
        val payload = db.useWriterConnection { c ->
            c.usePrepared("SELECT payload FROM edit_task WHERE id = $id") { s -> s.step(); s.getText(0) }
        }
        assertEquals(draft, ClipDraftCodec.decode(payload))
    }
}
