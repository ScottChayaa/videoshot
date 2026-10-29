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

/** 【重試】依失敗的步驟回到對的狀態：等待中、上傳中排工作，處理中排輪詢；上傳那一步失敗要先授權。 */
class RetryExportTaskTest {

    private val repo = FakeCacheRepo()
    private val enqueued = mutableListOf<Long>()
    private val polled = mutableListOf<Long>()
    private var authorizeCalls = 0
    private var denied = 0

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
        override fun enqueueStoryboardPoll(taskId: Long) { polled += taskId }
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

    private suspend fun retry(id: Long, granted: Boolean = true) =
        retryExportTask(deps, id, authorize = { authorizeCalls++; granted }, onAuthDenied = { denied++ })

    @Test fun 輸出失敗回到等待中並排工作() = runTest {
        val id = failedAt(EditTaskStep.RENDER)
        retry(id)
        assertEquals(EditTaskState.QUEUED, repo.editTask(id).first()!!.state)
        assertEquals(listOf(id), enqueued)
    }

    @Test fun 上傳失敗回到上傳中並排工作() = runTest {
        val id = failedAt(EditTaskStep.UPLOAD)
        retry(id)
        assertEquals(EditTaskState.UPLOADING, repo.editTask(id).first()!!.state)
        assertEquals(listOf(id), enqueued)
    }

    @Test fun storyboard步驟重試排輪詢不排串() = runTest {
        val id = failedAt(EditTaskStep.STORYBOARD)
        retry(id)
        assertEquals(EditTaskState.PROCESSING, repo.editTask(id).first()!!.state)
        assertEquals(emptyList<Long>(), enqueued)
        assertEquals(listOf(id), polled)
        assertEquals(0, authorizeCalls) // 只重新等縮圖，不重傳，不必授權
    }

    @Test fun 上傳步驟失敗重試前先授權() = runTest {
        val id = failedAt(EditTaskStep.UPLOAD)
        var stateWhenAsked: EditTaskState? = null
        retryExportTask(
            deps, id,
            authorize = { stateWhenAsked = repo.editTask(id).first()!!.state; true },
            onAuthDenied = { denied++ },
        )
        assertEquals(EditTaskState.FAILED, stateWhenAsked) // 授權通過之前不改狀態
        assertEquals(EditTaskState.UPLOADING, repo.editTask(id).first()!!.state)
        assertEquals(listOf(id), enqueued)
        assertEquals(0, denied)
    }

    @Test fun 上傳步驟授權被拒就維持失敗並提示() = runTest {
        val id = failedAt(EditTaskStep.UPLOAD)
        retry(id, granted = false)
        val task = repo.editTask(id).first()!!
        assertEquals(EditTaskState.FAILED, task.state)
        assertEquals(EditTaskStep.UPLOAD, task.failedStep)
        assertEquals(emptyList<Long>(), enqueued)
        assertEquals(1, denied)
    }

    @Test fun 輸出步驟失敗重試不授權() = runTest {
        retry(failedAt(EditTaskStep.RENDER))
        assertEquals(0, authorizeCalls)
    }

    @Test fun 不是失敗的任務什麼都不動也不排工作() = runTest {
        val id = failedAt(null) // 還是等待中
        retry(id)
        retry(999)
        assertEquals(EditTaskState.QUEUED, repo.editTask(id).first()!!.state)
        assertEquals(emptyList<Long>(), enqueued)
        assertEquals(emptyList<Long>(), polled)
        assertEquals(0, authorizeCalls)
    }
}
