package com.xenyaa.videoshot.debug

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xenyaa.videoshot.ui.common.TopBarIconButton
import com.xenyaa.videoshot.ui.theme.VideoshotTheme

/**
 * 元件總覽頁的入口，只在開發測試版存在（計畫 15A Task 8）。
 * 開啟：`adb shell am start -n com.xenyaa.videoshot/.debug.ComponentCatalogActivity`。
 * app 沒有深色模式，手機切到深色也一樣是淺色配色。
 */
class ComponentCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            // 右上角調色盤調的主色只活在這一頁，不存檔
            var tuning by remember { mutableStateOf(AccentTuning.DEFAULT) }
            var tunerOpen by remember { mutableStateOf(false) }
            VideoshotTheme(tuning.toSpec()) {
                ComponentCatalog(topBarActions = {
                    TopBarIconButton(PaletteIcon, "調整主色", onClick = { tunerOpen = true })
                })
                if (tunerOpen) AccentTunerSheet(tuning, onChange = { tuning = it }, onDismiss = { tunerOpen = false })
            }
        }
    }
}
