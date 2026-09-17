package com.xenyaa.videoshot.data.settings

import com.xenyaa.videoshot.core.similarity.FilterStrength
import kotlinx.coroutines.flow.Flow

/**
 * `AppRoot` 用得到的設定子集 —— 精靈的過濾強度，跟 Lightbox／第二步各自的一次性提示。
 *
 * 窄介面的理由跟 `WizardData`／`LibraryRepo` 一樣（見它們的 KDoc）：讓 `AppRoot` 不必依賴
 * 整個 [AppSettings]（背後是 DataStore／`Context`），測試才能不牽動真的 DataStore 檔案
 * 就把它接起來——一個 `flowOf(...)` 就是一份可控的假實作。
 *
 * 階段 8～11 還會替帳號頁（分類）、備份、Gemini key 加更多設定值；那些不是 `AppRoot`
 * 直接用到的，不進這個介面，免得它又跟著 [AppSettings] 一起長成什麼都放的抽屜。
 */
interface ShellSettings {
    val filterStrength: Flow<FilterStrength>
    val gridHintSeen: Flow<Boolean>
    suspend fun markGridHintSeen()
    val lightboxHintSeen: Flow<Boolean>
    suspend fun markLightboxHintSeen()
}
