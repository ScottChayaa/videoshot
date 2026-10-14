package com.xenyaa.videoshot.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 播放器全螢幕（規格第六節「全螢幕與畫面方向」）。整個 app 只有一份，由 `MainActivity` 發下去：
 * [FullscreenSystemEffect] 依它設方向與系統列，各畫面依它把播放器以外的東西藏起來。
 *
 * **全螢幕不是另開畫面**：播放器在組合裡的位置不能變，WebView 才不會被銷毀重載。
 */
class PlayerFullscreenState {
    var active by mutableStateOf(false)
        private set
    var landscape by mutableStateOf(true)
        private set

    fun enter(landscape: Boolean) {
        this.landscape = landscape
        active = true
    }

    fun exit() { active = false }
}

/** 預設值給預覽與測試用；正式畫面一律由 `MainActivity` 提供同一份。 */
val LocalPlayerFullscreen = staticCompositionLocalOf { PlayerFullscreenState() }

/**
 * 平常鎖直立；橫的影片全螢幕時轉橫向（兩個橫向都接受）。
 * Android 16 以上的大螢幕系統會忽略這個要求（規格第六節），不用另外判斷。
 */
internal fun orientationFor(active: Boolean, landscape: Boolean): Int =
    if (active && landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

/** 把 [state] 套到 Activity：方向＋沉浸模式（從邊緣滑一下暫時叫出系統列）。 */
@Composable
fun FullscreenSystemEffect(state: PlayerFullscreenState) {
    val activity = LocalContext.current.findActivity() ?: return
    LaunchedEffect(state.active, state.landscape) {
        activity.requestedOrientation = orientationFor(state.active, state.landscape)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (state.active) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Activity 結束時不留任何狀態（方向本來就跟著 Activity 走，這裡只是保險）
    DisposableEffect(Unit) { onDispose { state.exit() } }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
