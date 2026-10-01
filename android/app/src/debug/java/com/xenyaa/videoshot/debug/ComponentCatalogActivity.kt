package com.xenyaa.videoshot.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xenyaa.videoshot.ui.theme.VideoshotTheme

/**
 * 元件總覽頁的入口，只在開發測試版存在（計畫 15A Task 8）。
 * 開啟：`adb shell am start -n com.xenyaa.videoshot/.debug.ComponentCatalogActivity`；
 * 深淺色跟系統（驗深色用 `adb shell cmd uimode night yes`）。
 */
class ComponentCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VideoshotTheme { ComponentCatalog() } }
    }
}
