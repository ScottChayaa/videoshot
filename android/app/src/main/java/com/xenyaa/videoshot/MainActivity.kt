package com.xenyaa.videoshot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xenyaa.videoshot.ui.shell.AppRoot
import com.xenyaa.videoshot.ui.theme.VideoshotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VideoshotApp
        setContent {
            VideoshotTheme {
                AppRoot(container = app.container, onExitApp = { finish() })
            }
        }
    }
}
