package com.xenyaa.videoshot.edit

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.model.EditTask
import com.xenyaa.videoshot.data.repo.model.EditTaskState
import com.xenyaa.videoshot.publish.QuotaResumeWorker
import com.xenyaa.videoshot.publish.StoryboardPollWorker
import com.xenyaa.videoshot.publish.ensureQuotaResumes
import com.xenyaa.videoshot.publish.ensureStoryboardPolls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val WORK_NAME = "clip-export"
internal const val KEY_TASK_ID = "task_id"

internal fun clipExportTag(taskId: Long) = "clip-export-$taskId"

/**
 * 在背景跑一個任務：先升成前景服務（通知欄有進度，app 縮小、鎖屏都照跑），再交給 [runTask]
 * （`AppContainer.runClipTask`：輸出，有勾自動上傳的接著上傳）。等待中、輸出中、上傳中的任務都會跑。
 *
 * 系統不讓這時候開前景服務（Android 12 起，app 在背景時）：輸出中的改回「等待中」、上傳中的原樣留著
 * （下次從存著的上傳網址接著傳，不重新輸出）並 [notifyCanResume]（例如每日上限重置後 app 在背景），
 * 回成功，留給下次打開 app 時 [resumeClipExports] 重排。
 * 任務不存在或已經做完（處理中以後）就什麼都不做。
 */
class ClipExportWorker(
    context: Context,
    params: WorkerParameters,
    private val repo: CacheRepo,
    private val foregroundInfoFor: (EditTask) -> ForegroundInfo,
    private val runTask: suspend (Long) -> Unit,
    private val now: () -> Long,
    /** 上傳中的任務開不了前景服務：提醒使用者打開 app 接著傳。 */
    private val notifyCanResume: (EditTask) -> Unit = {},
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.failure()
        val task = repo.editTask(taskId).first() ?: return Result.success()
        if (task.state !in RUNNABLE) return Result.success()

        try {
            setForeground(foregroundInfoFor(task))
        } catch (e: CancellationException) {
            throw e // CancellationException 本身就是 IllegalStateException，不能被下面吃掉
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException 也是 IllegalStateException；只把輸出中的改回等待中
            repo.requeueEditTask(taskId, now())
            if (task.state == EditTaskState.UPLOADING) notifyCanResume(task)
            return Result.success()
        }

        runTask(taskId)
        return Result.success()
    }

    private companion object {
        val RUNNABLE = setOf(EditTaskState.QUEUED, EditTaskState.RENDERING, EditTaskState.UPLOADING)
    }
}

/**
 * 把剪輯任務的依賴接進 [ClipExportWorker]、[StoryboardPollWorker] 與 [QuotaResumeWorker] 的建構子——WorkManager 自己不知道怎麼生出它們。
 * [poll] 是 `StoryboardPoller.poll`；[notifyCanResume] 見 [ClipExportWorker]。
 */
class ClipExportWorkerFactory(
    private val repo: CacheRepo,
    private val foregroundInfoFor: (EditTask) -> ForegroundInfo,
    private val runTask: suspend (Long) -> Unit,
    private val poll: suspend (Long) -> Unit,
    private val notifyCanResume: (EditTask) -> Unit = {},
    private val now: () -> Long,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        ClipExportWorker::class.java.name ->
            ClipExportWorker(appContext, workerParameters, repo, foregroundInfoFor, runTask, now, notifyCanResume)
        StoryboardPollWorker::class.java.name -> StoryboardPollWorker(appContext, workerParameters, poll)
        QuotaResumeWorker::class.java.name -> QuotaResumeWorker(appContext, workerParameters, repo, now)
        else -> null
    }
}

/**
 * 排一個輸出工作。所有輸出共用一串（`clip-export`），一次只跑一支、其餘排隊。
 * 標籤 `clip-export-{taskId}` 用來取消與查有沒有排著。
 */
fun enqueueClipExport(context: Context, taskId: Long) {
    val request = OneTimeWorkRequestBuilder<ClipExportWorker>()
        .setInputData(workDataOf(KEY_TASK_ID to taskId))
        .addTag(clipExportTag(taskId))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

/**
 * 這個任務的輸出工作正在跑才取消它（中斷輸出），等到取消真的套用才回來；排隊中的工作不動。
 *
 * 排隊中的不能取消：串的末端一旦是「已取消」，下一次 [enqueueClipExport] 的 `APPEND_OR_REPLACE`
 * 會把同名的整串工作刪掉（包括正在跑的那支），新工作立刻開始，變成兩支同時輸出。
 * 排隊中的任務由呼叫端刪掉資料列就好：工作輪到時讀不到任務，直接結束（[ClipExportWorker]）。
 *
 * 取消正在跑的那支（串頭）時，排在後面的工作會被連帶取消，但那些任務在資料庫還是「等待中」：
 * 呼叫端取消完要再呼叫 [resumeClipExports] 把它們補排（這時整串都結束了，換掉整串是安全的）。
 * 不等取消套用就補排，可能讀到還沒被取消的狀態而跳過它們。
 */
suspend fun cancelClipExport(context: Context, taskId: Long) {
    val manager = WorkManager.getInstance(context)
    val tag = clipExportTag(taskId)
    val running = manager.getWorkInfosByTagFlow(tag).first().any { it.state == WorkInfo.State.RUNNING }
    if (running) manager.cancelAllWorkByTag(tag).await()
}

/**
 * 把沒有工作的未完成任務（app 被殺、手機重開、之前系統不讓開前景服務）排回 `clip-export` 串上：
 * 輸出中的改回等待中（從頭輸出）、上傳中的原樣排回去（從存著的上傳網址接著傳）。
 * 先處理等每日上限重置的任務（[ensureQuotaResumes]：已過重置時間的改回上傳中、跟著排回串上，還沒到的補排等待工作），
 * 最後把處理中、沒有輪詢工作的任務補排等縮圖（[ensureStoryboardPolls]）。
 * 在 app 第一次回到前景時呼叫——這時才開得了前景服務。已經排著或在跑的不動。
 *
 * 不會把正在跑的串換掉：[cancelClipExport] 只取消正在跑的串頭（整串跟著結束），
 * 串的末端是「已取消」或「失敗」時整串都已經結束了。
 */
suspend fun resumeClipExports(context: Context, repo: CacheRepo, now: Long) {
    val manager = WorkManager.getInstance(context)
    ensureQuotaResumes(context, repo, now)
    for (id in repo.pendingEditTaskIds()) {
        val scheduled = manager.getWorkInfosByTagFlow(clipExportTag(id)).first().any { !it.state.isFinished }
        if (scheduled) continue
        repo.requeueEditTask(id, now) // 只動輸出中的
        enqueueClipExport(context, id)
    }
    ensureStoryboardPolls(context, repo)
}
