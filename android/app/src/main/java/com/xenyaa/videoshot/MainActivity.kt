package com.xenyaa.videoshot

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.xenyaa.videoshot.edit.EXTRA_OPEN_CLIP
import com.xenyaa.videoshot.player.FullscreenSystemEffect
import com.xenyaa.videoshot.player.LocalPlayerFullscreen
import com.xenyaa.videoshot.player.LocalPlayerSound
import com.xenyaa.videoshot.player.PlayerFullscreenState
import com.xenyaa.videoshot.player.PlayerSound
import com.xenyaa.videoshot.ui.shell.AppRoot
import com.xenyaa.videoshot.ui.shell.FrameStallGuard
import com.xenyaa.videoshot.ui.theme.Palettes
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 播放器全螢幕的唯一一份狀態（規格第六節）。放在 Activity 上，轉向時不重建，所以不會丟。 */
    private val playerFullscreen = PlayerFullscreenState()

    /** 測試會換掉它——見 `MainActivityFrameStallTest`。 */
    internal var frameStallGuard = FrameStallGuard()

    /**
     * 「請切到剪輯頁」的訊號：點輸出通知（[EXTRA_OPEN_CLIP]）時加一，[AppRoot] 看到新的值就回到剪輯分頁。
     * 通知的 PendingIntent 帶 `SINGLE_TOP｜CLEAR_TOP`，app 已開著時系統把它交給 [onNewIntent]
     * （manifest 沒設 launchMode 也一樣），所以不必改成 singleTop。
     */
    internal var openClipSignal by mutableIntStateOf(0)
        private set

    private fun noteOpenClip(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CLIP, false) == true) openClipSignal++
    }

    /** MIUI 13 會吞掉 surface 建出來之前的 Compose 幀請求，取得焦點時補要一幀（見 [FrameStallGuard]）。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        frameStallGuard.onWindowFocusChanged(hasFocus)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteOpenClip(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 系統列圖示固定深色：app 沒有深色模式，手機切到深色時也不能換成白色圖示（疊在淺底上看不見）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 只看第一次建立：系統因為程序被殺而重建時，getIntent() 還是當初那個，不能再切一次剪輯頁
        if (savedInstanceState == null) noteOpenClip(intent)
        val app = application as VideoshotApp
        setContent {
            // 色系存在 DataStore，這裡是唯一的套用點；沒有深色模式，不看系統的淺色／深色設定。
            // 讀不到（第一幀、或使用者沒選過）就是預設色系，不阻塞畫面。
            val themeId by app.container.settings.themeId.collectAsStateWithLifecycle(initialValue = null)
            // 取圖第二步與原片播放的【聲音】：跟全螢幕一樣整個 app 一份，播放器自己取用
            val playerMuted by app.container.settings.playerMuted.collectAsStateWithLifecycle(initialValue = false)
            val playerSound = remember(playerMuted) {
                PlayerSound(playerMuted) { value -> lifecycleScope.launch { app.container.settings.setPlayerMuted(value) } }
            }

            VideoshotTheme(spec = Palettes.byId(themeId)) {
                CompositionLocalProvider(
                    LocalPlayerFullscreen provides playerFullscreen,
                    LocalPlayerSound provides playerSound,
                ) {
                    FullscreenSystemEffect(playerFullscreen)
                    AppRoot(container = app.container, onExitApp = { finish() }, openClipSignal = openClipSignal)
                }
            }
        }
    }
}
