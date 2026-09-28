package com.xenyaa.videoshot

import android.app.Application
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.xenyaa.videoshot.backup.BackupWorkerFactory
import com.xenyaa.videoshot.backup.scheduleDailyBackup
import com.xenyaa.videoshot.backfill.BackfillWorkerFactory
import com.xenyaa.videoshot.backfill.scheduleBackfill
import com.xenyaa.videoshot.di.AppContainer

class VideoshotApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() {
            val factory = DelegatingWorkerFactory().apply {
                addFactory(BackupWorkerFactory(runIfDue = { force -> container.backupManager.runIfDue(force) }))
                addFactory(BackfillWorkerFactory(runBatch = { container.backfillManager.runBatch() }))
            }
            return Configuration.Builder().setWorkerFactory(factory).build()
        }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleDailyBackup(this)
        scheduleBackfill(this)
    }
}
