package com.xenyaa.videoshot

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenyaa.videoshot.ui.shell.AppRoot
import com.xenyaa.videoshot.ui.shell.FrameStallGuard
import com.xenyaa.videoshot.ui.theme.Palettes
import com.xenyaa.videoshot.ui.theme.VideoshotTheme

class MainActivity : ComponentActivity() {
    /** 測試會換掉它——見 `MainActivityFrameStallTest`。 */
    internal var frameStallGuard = FrameStallGuard()

    /** MIUI 13 會吞掉 surface 建出來之前的 Compose 幀請求，取得焦點時補要一幀（見 [FrameStallGuard]）。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        frameStallGuard.onWindowFocusChanged(hasFocus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 系統列圖示固定深色：app 沒有深色模式，手機切到深色時也不能換成白色圖示（疊在淺底上看不見）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val app = application as VideoshotApp
        setContent {
            // 色系存在 DataStore，這裡是唯一的套用點；沒有深色模式，不看系統的淺色／深色設定。
            // 讀不到（第一幀、或使用者沒選過）就是預設色系，不阻塞畫面。
            val themeId by app.container.settings.themeId.collectAsStateWithLifecycle(initialValue = null)

            VideoshotTheme(spec = Palettes.byId(themeId)) {
                AppRoot(container = app.container, onExitApp = { finish() })
            }
        }
    }
}
