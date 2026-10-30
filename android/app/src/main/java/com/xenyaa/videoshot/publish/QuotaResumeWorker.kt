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
import com.xenyaa.videoshot.core.publish.nextQuotaResetMs
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.edit.KEY_TASK_ID
import com.xenyaa.videoshot.edit.enqueueClipExport
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private fun quotaResumeName(taskId: Long) = "clip-quota-$taskId"

/**
 * 每日上傳上限重置了：把等待中的任務改回上傳中、接回 `clip-export` 串（從存著的上傳網址接著傳）。
 * 接回時收掉「隔天會自動接著上傳」那則通知（[dismissResult]），不然上傳中會同時有兩則矛盾的說法。
 * 普通的背景工作，不是前景服務。任務已被取消（刪掉）或不在等待中就什麼都不做。
 *
 * 接回去的那個工作在 app 背景時（Android 12 起）不一定開得了前景服務：開不了時任務留在上傳中、
 * 跳一則「影片可以繼續上傳了」，下次打開 app 由 `resumeClipExports` 接著傳（見 `ClipExportWorker`）。
 */
class QuotaResumeWorker(
    context: Context,
    params: WorkerParameters,
    private val repo: CacheRepo,
    private val now: () -> Long,
    private val dismissResult: (Long) -> Unit = {},
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.failure()
        if (repo.resumeEditTaskFromQuota(taskId, now())) {
            dismissResult(taskId)
            enqueueClipExport(applicationContext, taskId)
        }
        return Result.success()
    }
}

/**
 * [delayMs] 毫秒後（而且有網路時）讓 [taskId] 接著上傳。每個任務一個（`clip-quota-{taskId}`），
 * 再排一次就取代舊的（`REPLACE`）。
 */
fun enqueueQuotaResume(context: Context, taskId: Long, delayMs: Long) {
    val request = OneTimeWorkRequestBuilder<QuotaResumeWorker>()
        .setInputData(workDataOf(KEY_TASK_ID to taskId))
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(quotaResumeName(taskId), ExistingWorkPolicy.REPLACE, request)
}

/** 等待中的任務被取消時收掉它的等待工作。 */
fun cancelQuotaResume(context: Context, taskId: Long) {
    WorkManager.getInstance(context).cancelUniqueWork(quotaResumeName(taskId))
}

/**
 * 等待中的任務：已過重置時間（從撞到上限的 `updated_at` 算）的改回上傳中（由呼叫端排回串上），
 * 還沒到的確認有等待工作，沒有就補排（重開機、強制停止後工作被清掉）。已經排著的不動。
 * 改回上傳中的任務交給 [onResumed]（收掉「隔天會自動接著上傳」那則通知）。
 */
suspend fun ensureQuotaResumes(context: Context, repo: CacheRepo, now: Long, onResumed: (Long) -> Unit = {}) {
    val manager = WorkManager.getInstance(context)
    for (id in repo.waitingQuotaEditTaskIds()) {
        val hitAt = repo.editTask(id).first()?.updatedAt ?: continue
        val resetAt = nextQuotaResetMs(hitAt)
        if (now >= resetAt) {
            if (repo.resumeEditTaskFromQuota(id, now)) onResumed(id)
            continue
        }
        val scheduled = manager.getWorkInfosForUniqueWorkFlow(quotaResumeName(id)).first().any { !it.state.isFinished }
        if (!scheduled) enqueueQuotaResume(context, id, resetAt - now)
    }
}
