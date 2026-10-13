package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 模擬 Android 15 而不是 compileSdk 的 37：Android 16 起的 ApplicationSharedMemory
// 會讓 Robolectric 去戳 FileDescriptor 的內部欄位，在現代 JDK 上被擋。minSdk 是 26，35 有代表性。
@Config(sdk = [35])
class WizardShellTest {

    @get:Rule val compose = createComposeRule()

    private var exits = 0

    /** 外殼測試不碰網路與資料庫、也不碰第三步，補最短的版本就好。 */
    private class FakeData : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(FetchResult.OK, null, null)
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
        override suspend fun distinctPlaces() = emptyList<String>()
        override suspend fun allTagNames() = emptyList<String>()
        override suspend fun cropThumbs(
            videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit,
        ) = CropOutcome(frameIndexes, emptyList())
        override suspend fun commit(
            video: com.xenyaa.videoshot.data.library.entity.VideoEntity,
            picks: List<com.xenyaa.videoshot.data.repo.model.NewShot>,
        ) = picks.indices.map { it.toLong() }
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) = Unit
        override suspend fun currentDraft(): String? = null
        override suspend fun clearDraft(videoId: String) = Unit
    }

    private fun newVm() = WizardViewModel(
        data = FakeData(),
        frameSourceFactory = { com.xenyaa.videoshot.wizard.frames.FakeFrameSource.of(frameCount = 0) },
        strength = kotlinx.coroutines.flow.flowOf(com.xenyaa.videoshot.core.similarity.FilterStrength.MEDIUM),
        hintSeen = kotlinx.coroutines.flow.flowOf(true),
        onHintSeen = {},
        manualImages = { com.xenyaa.videoshot.capture.ManualImageStore(java.io.File("/tmp/unused")) },
        captureFor = { null },
    )

    private fun show(vm: WizardViewModel = newVm()) {
        compose.setContent { WizardScreen(vm = vm, haptics = FakeHaptics(), onExit = { exits++ }) }
    }

    @Test
    fun 一進來在第一步() {
        val vm = newVm()
        show(vm)
        assertEquals(WizardStep.URL, vm.step.value)
    }

    /** 頂欄左上角是返回鍵（不是 ✕）。 */
    @Test
    fun 頂欄左上角是返回鍵() {
        show()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        compose.onNodeWithContentDescription("關閉").assertDoesNotExist()
    }

    @Test
    fun 第一步按返回直接離開() {
        show()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun 第二步按返回回到第一步不離開() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        show(vm)
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(WizardStep.URL, vm.step.value)
        assertEquals("還沒離開", 0, exits)
        // 沒有離開確認框了
        compose.onNodeWithText("保留草稿並離開").assertDoesNotExist()
    }

    @Test
    fun 第三步按返回回到第二步() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        vm.goTo(WizardStep.DETAILS)
        show(vm)
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(WizardStep.PICK, vm.step.value)
        assertEquals(0, exits)
    }

    /** Android 返回走同一套：第二步回第一步，第一步才離開。 */
    @Test
    fun 系統返回跟頂欄返回鍵一樣() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        compose.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            WizardScreen(vm = vm, haptics = FakeHaptics(), onExit = { exits++ })
        }
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        assertEquals(WizardStep.URL, vm.step.value)
        assertEquals(0, exits)
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        assertEquals(1, exits)
    }

    @Test
    fun back_從第二步回到第一步而不是離開() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        assertEquals(true, vm.back())
        assertEquals(WizardStep.URL, vm.step.value)
    }

    @Test
    fun back_在第一步回傳false讓系統處理() {
        assertEquals(false, newVm().back())
    }

    @Test
    fun 沒有底部導覽列() {
        show()
        compose.onNodeWithText("照片").assertDoesNotExist()
        compose.onNodeWithText("相簿").assertDoesNotExist()
    }

    /** 原型 .wz-top：第一步的頂欄標題是「取圖」。 */
    @Test
    fun 第一步頂欄顯示取圖() {
        show()
        compose.onNodeWithText("取圖").assertIsDisplayed()
    }

    /** 影片標題還沒載入（第二步但 loaded 為空）時，頂欄退回「取圖」。 */
    @Test
    fun 第二步取不到影片標題時頂欄退回取圖() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        show(vm)
        compose.onNodeWithText("取圖").assertIsDisplayed()
    }
}
