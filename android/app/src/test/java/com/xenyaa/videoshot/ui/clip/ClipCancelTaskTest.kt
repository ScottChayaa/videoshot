package com.xenyaa.videoshot.ui.clip

import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 取消與刪除任務的呼叫順序（導出中畫面與剪輯頁的任務列共用 [cancelExportTask]、[deleteExportTask]）。
 * 重點：`resumeExports` 一定在刪資料列之後——它最後會把還在「處理中」的任務補排輪詢，順序反了會把剛收掉的輪詢排回去。
 */
class ClipCancelTaskTest {
    private val repo = FakeCacheRepo()
    private val calls = mutableListOf<String>()
    private var taskId = 0L

    /** 每個呼叫都記下當下這個任務還在不在資料庫裡，順序與時機一起驗。 */
    private val deps = object : ClipDeps {
        override val mediaLibrary = object : MediaLibrary {
            override suspend fun list(filter: MediaFilter): List<MediaItem> = emptyList()
        }
        override val mediaThumbLoader = MediaThumbLoader(decode = { null })
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = emptySet<String>()
        override val cacheRepo: CacheRepo = repo
        override fun now() = 500L

        private suspend fun log(what: String) {
            calls += "$what:${if (repo.editTask(taskId).first() != null) "任務在" else "任務已刪"}"
        }

        override fun cancelStoryboardPoll(taskId: Long) { calls += "cancelPoll" }
        override fun cancelQuotaResume(taskId: Long) { calls += "cancelQuota" }
        override suspend fun cancelExport(taskId: Long) = log("cancelExport")
        override suspend fun resumeExports() = log("resume")
        override suspend fun deleteExportFiles(taskId: Long) = log("deleteFiles")
        override fun dismissExportResult(taskId: Long) { calls += "dismiss" }
    }

    private suspend fun processingTask(): Long {
        val draft = newClipDraft(
            ClipOrientation.PORTRAIT,
            listOf(ClipSource(7, "content://media/7", SourceKind.PHOTO, 0, null, 1080, 1920, 0)),
        )
        val id = repo.startEditTask(draft, "我的旅行", true, 100)
        repo.setEditTaskUploaded(id, "vid", 200)
        taskId = id
        return id
    }

    @Test fun 取消處理中的任務_先收輪詢和刪資料列_最後才補排() = runTest {
        val id = processingTask()
        cancelExportTask(deps, id)

        assertNull(repo.editTask(id).first())
        assertEquals(
            listOf("cancelPoll", "cancelQuota", "cancelExport:任務在", "cancelExport:任務已刪", "deleteFiles:任務已刪", "dismiss", "resume:任務已刪"),
            calls,
        )
    }

    @Test fun 取消等待明天上傳的任務_先收等待工作和刪資料列_最後才補排() = runTest {
        val id = processingTask()
        repo.deleteEditTask(id)
        val draft = newClipDraft(
            ClipOrientation.PORTRAIT,
            listOf(ClipSource(7, "content://media/7", SourceKind.PHOTO, 0, null, 1080, 1920, 0)),
        )
        val waiting = repo.startEditTask(draft, "等明天的", true, 100)
        repo.setEditTaskReadyToUpload(waiting, 0, 150)
        repo.setEditTaskWaitingQuota(waiting, "系統目前上傳已超過每日配額，請隔天再繼續上傳", 200)
        taskId = waiting

        cancelExportTask(deps, waiting)

        assertNull(repo.editTask(waiting).first())
        // resumeExports 在刪之前跑的話會把等待工作補排回去
        assertEquals(
            listOf("cancelPoll", "cancelQuota", "cancelExport:任務在", "cancelExport:任務已刪", "deleteFiles:任務已刪", "dismiss", "resume:任務已刪"),
            calls,
        )
    }

    @Test fun 刪除任務也收輪詢_補排在刪之後() = runTest {
        val id = processingTask()
        deleteExportTask(deps, id)

        assertNull(repo.editTask(id).first())
        assertEquals(listOf("cancelPoll", "deleteFiles:任務已刪", "dismiss", "resume:任務已刪"), calls)
    }
}
