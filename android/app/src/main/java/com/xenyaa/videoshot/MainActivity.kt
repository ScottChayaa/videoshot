package com.xenyaa.videoshot

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
                                manualImages = { app.container.manualImagesFor(it) },
                                captureFor = { app.container.captureFor(it) },
                                today = { java.time.LocalDate.now().toString() },
                            ) as T
                    }
                }
                val context = LocalContext.current
                val vm: WizardViewModel = viewModel(factory = factory)
                LaunchedEffect(vm) {
                    vm.finished.collect { done ->
                        // 階段 7 的首頁做好之前，終點只能是一句話。
                        // **不要順手做一個暫時的首頁** —— 那會變成兩份要維護的東西
                        Toast.makeText(context, "已新增 ${done.count} 張", Toast.LENGTH_LONG).show()
                    }
                }
                WizardScreen(
                    vm = vm,
                    haptics = app.container.haptics,
                    // 階段 7 之前沒有別的地方可去，關掉精靈就是關掉 app
                    onExit = { finish() },
                )
            }
        }
    }
}
