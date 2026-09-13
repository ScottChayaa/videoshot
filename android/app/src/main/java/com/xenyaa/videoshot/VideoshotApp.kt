package com.xenyaa.videoshot

import android.app.Application
import com.xenyaa.videoshot.di.AppContainer

class VideoshotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
