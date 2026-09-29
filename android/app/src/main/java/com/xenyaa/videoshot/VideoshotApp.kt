package com.xenyaa.videoshot

import android.app.Application
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.xenyaa.videoshot.backup.BackupWorkerFactory
import com.xenyaa.videoshot.backup.scheduleDailyBackup
import com.xenyaa.videoshot.backfill.BackfillScanWorkerFactory
import com.xenyaa.videoshot.backfill.BackfillWorkerFactory
import com.xenyaa.videoshot.backfill.scheduleBackfill
import com.xenyaa.videoshot.backfill.scheduleBackfillScan
import com.xenyaa.videoshot.di.AppContainer

class VideoshotApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() {
            val factory = DelegatingWorkerFactory().apply {
                addFactory(BackupWorkerFactory(runIfDue = { force -> container.backupManager.runIfDue(force) }))
                addFactory(BackfillWorkerFactory(runBatch = { container.backfillManager.runBatch() }))
                addFactory(BackfillScanWorkerFactory(scanForMissing = { container.backfillManager.scanForMissing() }))
            }
            return Configuration.Builder().setWorkerFactory(factory).build()
        }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleDailyBackup(this)
        // 掃描不受網路約束，永遠先排；回填本身（真的抓 sheet）預設仍然只在 Wi-Fi 下執行
        // （見 BackfillScanWorker／scheduleBackfill 的 KDoc）。
        scheduleBackfillScan(this)
        scheduleBackfill(this)
    }
}
