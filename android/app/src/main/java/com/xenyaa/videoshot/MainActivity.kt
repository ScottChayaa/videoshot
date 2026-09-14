package com.xenyaa.videoshot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.wizard.WizardScreen
import com.xenyaa.videoshot.wizard.WizardViewModel

/**
 * ⚠️ 臨時入口：直接落在取圖精靈。
 *
 * 真正的外殼（底部五格導覽、首頁）是**階段 7**，屆時這裡會再換一次。
 * 階段 2 的資料層冒煙畫面已經完成它的任務（在實機上證明資料層活著），內容留在 git 歷史。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VideoshotApp
        setContent {
            VideoshotTheme {
                val factory = remember {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            WizardViewModel(
                                data = app.container.wizardData,
                                frameSourceFactory = { app.container.frameSourceFor(it) },
                                strength = app.container.settings.filterStrength,
                                hintSeen = app.container.settings.gridHintSeen,
                                onHintSeen = { app.container.settings.markGridHintSeen() },
                            ) as T
                    }
                }
                WizardScreen(
                    vm = viewModel(factory = factory),
                    haptics = app.container.haptics,
                    // 階段 7 之前沒有別的地方可去，關掉精靈就是關掉 app
                    onExit = { finish() },
                )
            }
        }
    }
}
