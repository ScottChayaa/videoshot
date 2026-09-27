package com.xenyaa.videoshot.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

private const val DAILY_BACKUP_WORK_NAME = "daily_backup"

/**
 * 每日自動備份（規格第十節「觸發」表的「自動」列）。約束（不計費網路、電量不低）在
 * [scheduleDailyBackup] 設定，這裡只管執行一次 `runIfDue(force = false)`。
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
    private val runIfDue: suspend (force: Boolean) -> Boolean,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        runIfDue(false)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

/** 把 [BackupManager] 接進 `BackupWorker` 的建構子——WorkManager 自己不知道怎麼生出 `BackupManager`。 */
class BackupWorkerFactory(private val runIfDue: suspend (force: Boolean) -> Boolean) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        BackupWorker::class.java.name -> BackupWorker(appContext, workerParameters, runIfDue)
        else -> null
    }
}

/** app 啟動時呼叫一次；`enqueueUniquePeriodicWork` 是 idempotent 的，重覆呼叫不會排出第二份工作。 */
fun scheduleDailyBackup(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()
    val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(DAILY_BACKUP_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
