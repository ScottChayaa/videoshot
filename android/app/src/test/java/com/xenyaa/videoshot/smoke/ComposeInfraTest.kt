package com.xenyaa.videoshot.smoke

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 確認 Compose UI 測試在 JVM（Robolectric）上會動。
 *
 * 為什麼不用儀器測試：這台實機上**連 `Text("嗨")` 都會無限卡住**
 * （測試 Activity 從沒被帶到前景），而同樣走實機的非 UI 儀器測試 21 秒就跑完。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 模擬 Android 15（API 35）而不是 compileSdk 的 37：
// Android 16 起的 ApplicationSharedMemory 會讓 Robolectric 去戳 FileDescriptor 的內部欄位，
// 在現代 JDK 上被擋。我們的 minSdk 是 26，UI 邏輯在 35 上驗一樣有代表性。
@Config(sdk = [35])
class ComposeInfraTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun 最小的_compose_測試() {
        compose.setContent { Text("嗨") }
        compose.onNodeWithText("嗨").assertIsDisplayed()
    }
}
