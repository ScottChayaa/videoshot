package com.xenyaa.videoshot

import android.app.Application
import androidx.work.Configuration
import com.xenyaa.videoshot.backup.BackupWorkerFactory
import com.xenyaa.videoshot.backup.scheduleDailyBackup
import com.xenyaa.videoshot.di.AppContainer

class VideoshotApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(BackupWorkerFactory(runIfDue = { force -> container.backupManager.runIfDue(force) }))
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleDailyBackup(this)
    }
}
