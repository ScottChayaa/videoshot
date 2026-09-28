package com.xenyaa.videoshot.backfill

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

private const val BACKFILL_WORK_NAME = "thumbnail_backfill"
private const val KEY_ALLOW_MOBILE_DATA = "allow_mobile_data"

/**
 * 縮圖回填的一次性作業（規格第十一節「執行方式」）。**一次執行只處理一批**
 * （[BackfillManager.runBatch] 內部依 `batchLimit` 與到期時間自然限制範圍），
 * 還有工作沒做完的話自己重新排一個接續的 work（見 [doWork] 的 `Continued.remaining`
 * 分支）——這樣每一次執行都停在 WorkManager 給的執行時間窗口內，不會因為一支影片
 * 很多、單次執行時間過長被系統中止。
 */
class BackfillWorker(
    context: Context,
    params: WorkerParameters,
    private val runBatch: suspend () -> BackfillRunResult,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        when (val result = runBatch()) {
            BackfillRunResult.Idle -> Result.success()
            // 429／解析失敗：回 retry 交給 WorkManager 的退避（setBackoffCriteria），
            // 不在這裡自己算退避時間——避免跟 thumb_state 的每格退避搞成兩套時間表
            BackfillRunResult.Paused -> Result.retry()
            is BackfillRunResult.Continued -> {
                if (result.remaining) {
                    val allowMobileData = inputData.getBoolean(KEY_ALLOW_MOBILE_DATA, false)
                    scheduleBackfill(applicationContext, allowMobileData = allowMobileData, replace = true)
                }
                Result.success()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

/** 把 [BackfillManager] 接進 `BackfillWorker` 的建構子——WorkManager 自己不知道怎麼生出 `BackfillManager`。 */
class BackfillWorkerFactory(private val runBatch: suspend () -> BackfillRunResult) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        BackfillWorker::class.java.name -> BackfillWorker(appContext, workerParameters, runBatch)
        else -> null
    }
}

/**
 * 排一次回填作業。**唯一的排程入口**——app 開機（預設 Wi-Fi）、Worker 自我接續
 * （沿用同一次授權）、使用者按【用行動網路繼續】（[allowMobileData] = true、[replace] = true）
 * 都呼叫這裡。
 *
 * @param allowMobileData false（預設）＝只在不計費網路（Wi-Fi）下執行；true＝這次允許用行動
 *        網路，**不持久化**（規格第四節：回填是否允許行動網路「不存,每次詢問」）——呼叫端
 *        （帳號頁）每次都要明確傳 true，不會自己記住上次的選擇。
 * @param replace true 用 [ExistingWorkPolicy.REPLACE]（使用者主動要求「這次用行動網路繼續」，
 *        要蓋掉目前卡在等 Wi-Fi 的排程；Worker 自我接續時同理，蓋掉自己剛執行完的那一份）；
 *        false（開機時）用 [ExistingWorkPolicy.KEEP]——已經有工作在跑或在排隊就不要打斷它，
 *        沒有的話才插入新的（例如上一輪已經跑完、這次開機發現有新的 missing 又要再跑一次）。
 */
fun scheduleBackfill(context: Context, allowMobileData: Boolean = false, replace: Boolean = false) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
        .build()
    val request = OneTimeWorkRequestBuilder<BackfillWorker>()
        .setConstraints(constraints)
        .setInputData(workDataOf(KEY_ALLOW_MOBILE_DATA to allowMobileData))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
    val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    WorkManager.getInstance(context).enqueueUniqueWork(BACKFILL_WORK_NAME, policy, request)
}
