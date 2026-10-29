package com.xenyaa.videoshot.publish

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.edit.KEY_TASK_ID
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private fun storyboardPollName(taskId: Long) = "clip-storyboard-$taskId"

/**
 * 查一次某個任務的 YouTube 縮圖產生了沒（[poll] 是 `StoryboardPoller.poll`）。
 * 普通的背景工作，不是前景服務；下一次由 [poll] 自己用 [enqueueStoryboardPoll] 排。
 */
class StoryboardPollWorker(
    context: Context,
    params: WorkerParameters,
    private val poll: suspend (Long) -> Unit,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.failure()
        poll(taskId)
        return Result.success()
    }
}

/**
 * [delayMs] 毫秒後（而且有網路時）查一次 [taskId] 的縮圖。每個任務一串（`clip-storyboard-{taskId}`），
 * 好幾個任務可以同時等。
 *
 * 用 `APPEND_OR_REPLACE`：輪詢工作在自己執行中排下一次時，下一次是接在自己後面（等這次成功才開始），
 * 不會把正在跑的自己取消。
 */
fun enqueueStoryboardPoll(context: Context, taskId: Long, delayMs: Long) {
    val request = OneTimeWorkRequestBuilder<StoryboardPollWorker>()
        .setInputData(workDataOf(KEY_TASK_ID to taskId))
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(storyboardPollName(taskId), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

/** 任務取消或刪除時收掉它的輪詢（排著的、正在跑的都取消）。 */
fun cancelStoryboardPoll(context: Context, taskId: Long) {
    WorkManager.getInstance(context).cancelUniqueWork(storyboardPollName(taskId))
}

/**
 * 處理中的任務沒有未結束的輪詢工作（app 被強制停止、手機重開、工作被系統丟掉）就補排一次，立刻查。
 * 已經排著或在跑的不動。
 */
suspend fun ensureStoryboardPolls(context: Context, repo: CacheRepo) {
    val manager = WorkManager.getInstance(context)
    for (id in repo.processingEditTaskIds()) {
        val scheduled = manager.getWorkInfosForUniqueWorkFlow(storyboardPollName(id)).first().any { !it.state.isFinished }
        if (!scheduled) enqueueStoryboardPoll(context, id, 0L)
    }
}
