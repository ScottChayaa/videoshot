package com.xenyaa.videoshot.player

import androidx.compose.runtime.compositionLocalOf

/**
 * 取圖第二步與原片播放的【聲音】（規格第六節「播放器」）：設定值 `player_muted`，
 * 跟 Lightbox 片段的 `clip_muted` 分開記。`AppRoot` 從設定讀出來發下去，[PlayerSurface] 自己取用，
 * 兩個畫面不必一層層傳。
 */
class PlayerSound(val muted: Boolean, val setMuted: (Boolean) -> Unit)

/** 預設值給預覽與測試用（有聲音、切換沒作用）；正式畫面一律由 `AppRoot` 提供。 */
val LocalPlayerSound = compositionLocalOf { PlayerSound(muted = false) {} }
