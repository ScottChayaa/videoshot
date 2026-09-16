package com.xenyaa.videoshot.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xenyaa.videoshot.di.AppContainer
import com.xenyaa.videoshot.ui.common.ComingSoonScreen
import com.xenyaa.videoshot.wizard.WizardScreen
import com.xenyaa.videoshot.wizard.WizardViewModel
import java.time.LocalDate

private val NavSaver = Saver<NavState, String>(
    save = { NavCodec.encode(it) },
    restore = { NavCodec.decode(it) },
)

/**
 * 組裝點：導覽狀態、各分頁的內容、系統返回鍵。
 *
 * @param onExitApp 已經在最外層還按返回 —— 交還給系統（結束 Activity）
 */
@Composable
fun AppRoot(container: AppContainer, onExitApp: () -> Unit) {
    var nav by rememberSaveable(stateSaver = NavSaver) { mutableStateOf(NavState()) }

    BackHandler { nav.pop()?.let { nav = it } ?: onExitApp() }

    AppShell(nav = nav, onSelectTab = { nav = nav.select(it) }) { tab ->
        when (tab) {
            // Task 5 會把這一行換成真正的 HomeScreen
            Tab.HOME -> ComingSoonScreen("首頁", "縮圖牆正在接上（Task 5）")
            Tab.SEARCH -> ComingSoonScreen("查詢", "標籤與地點的查詢會在階段 10 做好")
            Tab.FOLDERS -> ComingSoonScreen("分類", "資料夾會在階段 8 做好")
            Tab.ACCOUNT -> ComingSoonScreen("帳號", "備份、設定與標籤管理會在階段 11～12 做好")
            Tab.CAPTURE -> CaptureTab(container, onExit = { nav = nav.select(nav.returnTo) })
        }
    }
}

@Composable
private fun CaptureTab(container: AppContainer, onExit: () -> Unit) {
    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WizardViewModel(
                    data = container.wizardData,
                    frameSourceFactory = { container.frameSourceFor(it) },
                    strength = container.settings.filterStrength,
                    hintSeen = container.settings.gridHintSeen,
                    onHintSeen = { container.settings.markGridHintSeen() },
                    manualImages = { container.manualImagesFor(it) },
                    captureFor = { container.captureFor(it) },
                    today = { LocalDate.now().toString() },
                ) as T
        }
    }
    val vm: WizardViewModel = viewModel(factory = factory, key = "wizard")
    WizardScreen(vm = vm, haptics = container.haptics, onExit = onExit)
}
