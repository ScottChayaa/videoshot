package com.xenyaa.videoshot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenyaa.videoshot.data.settings.NightMode
import com.xenyaa.videoshot.data.settings.isDark
import com.xenyaa.videoshot.ui.shell.AppRoot
import com.xenyaa.videoshot.ui.theme.Palettes
import com.xenyaa.videoshot.ui.theme.VideoshotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VideoshotApp
        setContent {
            // 主題（配色）與淺／深模式都存在 DataStore，這裡是唯一的套用點。
            // 讀不到（第一幀、或使用者沒選過）就是預設色系 ＋ 跟隨系統，不阻塞畫面。
            val themeId by app.container.settings.themeId.collectAsStateWithLifecycle(initialValue = null)
            val nightMode by app.container.settings.nightMode
                .collectAsStateWithLifecycle(initialValue = NightMode.SYSTEM)

            VideoshotTheme(
                spec = Palettes.byId(themeId),
                darkTheme = nightMode.isDark(systemDark = isSystemInDarkTheme()),
            ) {
                AppRoot(container = app.container, onExitApp = { finish() })
            }
        }
    }
}
