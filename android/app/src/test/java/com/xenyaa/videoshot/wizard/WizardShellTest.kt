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

    /** 外殼測試不碰網路與資料庫。WizardData 只有三個方法，假實作三行就寫完。 */
    private class FakeData : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(FetchResult.OK, null, null)
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
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
    fun 進度指示帶文字而且是三段() {
        show()
        compose.onNodeWithText("1. 貼網址").assertIsDisplayed()
        compose.onNodeWithText("2. 挑畫面").assertIsDisplayed()
        compose.onNodeWithText("3. 填資料").assertIsDisplayed()
    }

    @Test
    fun 一進來在第一步() {
        val vm = newVm()
        show(vm)
        assertEquals(WizardStep.URL, vm.step.value)
    }

    @Test
    fun 已完成的步驟可以點回去() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        show(vm)
        compose.onNodeWithText("1. 貼網址").performClick()
        assertEquals(WizardStep.URL, vm.step.value)
    }

    @Test
    fun 還沒到的步驟點不動() {
        val vm = newVm()
        show(vm)
        compose.onNodeWithText("3. 填資料").performClick()
        assertEquals(WizardStep.URL, vm.step.value)
    }

    @Test
    fun 第一步按叉直接離開不問草稿() {
        show()
        compose.onNodeWithContentDescription("關閉").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun 第二步按叉會問要不要保留草稿() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        show(vm)
        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithText("保留草稿並離開").assertIsDisplayed()
        compose.onNodeWithText("捨棄草稿").assertIsDisplayed()
        assertEquals("還沒離開", 0, exits)
    }

    @Test
    fun 選保留草稿會保留() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        show(vm)
        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithText("保留草稿並離開").performClick()
        assertEquals(true, vm.hasDraft.value)
        assertEquals(1, exits)
    }

    @Test
    fun 選捨棄草稿會捨棄() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        show(vm)
        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithText("捨棄草稿").performClick()
        assertEquals(false, vm.hasDraft.value)
        assertEquals(1, exits)
    }

    @Test
    fun 離開確認的文案說得出草稿還在不在() {
        val vm = newVm()
        vm.goTo(WizardStep.PICK)
        show(vm)
        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithText("下次按【取圖】可以接著做", substring = true).assertIsDisplayed()
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
        compose.onNodeWithText("首頁").assertDoesNotExist()
        compose.onNodeWithText("分類").assertDoesNotExist()
    }
}
