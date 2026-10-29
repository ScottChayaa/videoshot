package com.xenyaa.videoshot.ui.clip

import com.xenyaa.videoshot.core.timeline.ClipOrientation
import com.xenyaa.videoshot.core.timeline.ClipSource
import com.xenyaa.videoshot.core.timeline.SourceKind
import com.xenyaa.videoshot.core.timeline.newClipDraft
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.data.repo.model.EditTaskStep
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 【重試】依失敗的步驟回到對的狀態，只有要跑工作的（等待中、上傳中）才排工作。 */
class RetryExportTaskTest {

    private val repo = FakeCacheRepo()
    private val enqueued = mutableListOf<Long>()

    private val deps = object : ClipDeps {
        override val mediaLibrary = object : MediaLibrary {
            override suspend fun list(filter: MediaFilter): List<MediaItem> = emptyList()
        }
        override val mediaThumbLoader = MediaThumbLoader(decode = { null })
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = emptySet<String>()
        override val cacheRepo: CacheRepo = repo
        override fun now() = 500L
        override fun enqueueExport(taskId: Long) { enqueued += taskId }
    }

    private val draft = newClipDraft(
        ClipOrientation.PORTRAIT,
        listOf(ClipSource(1, "content://media/1", SourceKind.PHOTO, 1000, null, 1080, 1920, 0)),
    )

    private suspend fun failedAt(step: EditTaskStep?): Long {
        val id = repo.startEditTask(draft, "標題", true, 100)
        if (step != null) repo.setEditTaskFailed(id, "壞了", step, 110)
        return id
    }

    @Test fun 輸出失敗回到等待中並排工作() = runTest {
        val id = failedAt(EditTaskStep.RENDER)
        retryExportTask(deps, id)
        assertEquals(EditTaskState.QUEUED, repo.editTask(id).first()!!.state)
        assertEquals(listOf(id), enqueued)
    }

    @Test fun 上傳失敗回到上傳中並排工作() = runTest {
        val id = failedAt(EditTaskStep.UPLOAD)
        retryExportTask(deps, id)
        assertEquals(EditTaskState.UPLOADING, repo.editTask(id).first()!!.state)
        assertEquals(listOf(id), enqueued)
    }

    @Test fun 縮圖等待失敗回到處理中且先不排工作() = runTest {
        val id = failedAt(EditTaskStep.STORYBOARD)
        retryExportTask(deps, id)
        assertEquals(EditTaskState.PROCESSING, repo.editTask(id).first()!!.state)
        assertEquals(emptyList<Long>(), enqueued)
    }

    @Test fun 不是失敗的任務什麼都不動也不排工作() = runTest {
        val id = failedAt(null) // 還是等待中
        retryExportTask(deps, id)
        retryExportTask(deps, 999)
        assertEquals(EditTaskState.QUEUED, repo.editTask(id).first()!!.state)
        assertEquals(emptyList<Long>(), enqueued)
    }
}
