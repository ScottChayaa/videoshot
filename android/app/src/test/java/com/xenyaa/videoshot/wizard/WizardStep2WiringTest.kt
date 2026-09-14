package com.xenyaa.videoshot.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.player.FakePlayer
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import com.xenyaa.videoshot.wizard.frames.FramePlan
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **不要用 `runTest`**：Robolectric 底下 `Dispatchers.Main` 是 Android 的主 looper，
 * `runTest` 並沒有接管它，`viewModelScope` 的工作不會被 `advanceUntilIdle` 推進。
 * 正確的等待方式是 `compose.waitForIdle()`，沒有 `setContent` 的測試則用
 * `shadowOf(Looper.getMainLooper()).idle()`。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardStep2WiringTest {

    @get:Rule val compose = createComposeRule()

    private class FakeData(private val taken: Set<Int>) : WizardData {
        override suspend fun watchPage(videoId: String) =
            WatchPage(FetchResult.OK, null, "spec")
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String): Set<Int> = taken
    }

    private fun newVm(
        taken: Set<Int> = emptySet(),
        source: FakeFrameSource = FakeFrameSource.of(frameCount = 3, intervalSec = 10.0),
    ) = WizardViewModel(
        data = FakeData(taken),
        frameSourceFactory = { source },
        strength = flowOf(FilterStrength.MEDIUM),
        hintSeen = flowOf(true),
        onHintSeen = {},
    )

    private fun show(vm: WizardViewModel) {
        compose.setContent {
            WizardScreen(vm = vm, haptics = FakeHaptics(), onExit = {})
        }
    }

    /**
     * `WizardViewModel` 的 `openStep2` 把 `compute` 定死成 `Dispatchers.Default`（規格第三節設計
     * 原則第 5 條要求重運算不在主執行緒）—— 那是**真的背景執行緒**，`shadowOf(...).idle()` 只推得動
     * 主 looper 的訊息佇列，推不動背景執行緒本身做完了沒。檢查 `kept`／`hiddenCount` 這類要等收斂算完
     * 才穩定的欄位前，得輪詢等背景執行緒把結果貼回主執行緒，不能只 `idle()` 一次就假設穩定。
     */
    private fun awaitMain(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun 進第二步就建立了縮圖牆的狀態機() {
        val vm = newVm()
        vm.openRecent("vid")
        // 這一條沒有 setContent，所以自己把主 looper 推到閒置
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(vm.step2.value)
    }

    @Test
    fun 第二步不再是佔位文字() {
        val vm = newVm()
        vm.openRecent("vid")
        show(vm)
        compose.waitForIdle()
        compose.onNodeWithText("第二步（挑畫面）在階段 4b 實作").assertDoesNotExist()
        compose.onNodeWithText("全部選取").assertIsDisplayed()
    }

    @Test
    fun 已收藏的格號從第一步帶進第二步() {
        val vm = newVm(taken = setOf(1))
        vm.openRecent("vid")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf(1), vm.step2.value!!.state.value.taken)
    }

    @Test
    fun 長按會叫播放器跳到那一格的秒數() {
        val vm = newVm()
        val player = FakePlayer()
        vm.attachPlayer(player)
        vm.openRecent("vid")
        show(vm)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("第 3 格 00:20").performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals(listOf(20.0), player.seeks)
        assertEquals(true, player.playing)
        assertEquals(2, vm.step2.value!!.state.value.playingFrame)
    }

    @Test
    fun 點一格會勾選() {
        val vm = newVm()
        vm.openRecent("vid")
        show(vm)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("第 2 格 00:10").performClick()
        compose.waitForIdle()
        assertEquals(setOf(1), vm.step2.value!!.state.value.selected)
    }

    @Test
    fun 回第一步再進第二步會換掉舊的狀態機() {
        val first = FakeFrameSource.of(frameCount = 3)
        val vm = newVm(source = first)
        vm.openRecent("vid")
        shadowOf(Looper.getMainLooper()).idle()
        val before = vm.step2.value

        vm.back()
        vm.openRecent("vid")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("舊的要關掉，磁碟快取才放得掉", true, first.closed)
        assert(before !== vm.step2.value)
    }

    @Test
    fun 點已收藏的格子會出現提示點知道了會關掉() {
        val vm = newVm(taken = setOf(0))
        vm.openRecent("vid")
        show(vm)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("第 1 格 00:00").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("這一格已經收藏過了").assertIsDisplayed()

        compose.onNodeWithText("知道了").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("這一格已經收藏過了").assertDoesNotExist()
    }

    /**
     * 「回第一步再進第二步」不只要換掉 `step2.value`，還要把舊 store 綁的 `strength`／`hintSeen`
     * 收集器一起取消 —— 否則它們會一路累積，而且舊 store 明明已經 close 了卻還在被更新。
     *
     * 驗法：兩格的指紋漢明距離設成 8，MEDIUM(6) 不收斂、HIGH(10) 會收斂成 1 張。
     * 進第二步兩次後往 `strength` 推一個新值，**只有新的 store** 該有反應。
     */
    @Test
    fun 回第一步再進第二步後舊的strength收集器已經停止收集() {
        val strengthFlow = MutableStateFlow(FilterStrength.MEDIUM)
        val plan = FramePlan("vid", level = 3, atSec = listOf(0.0, 1.0), lowQuality = false)
        // 0x00 與 0xFF 的漢明距離是 8：MEDIUM(閾值 6) 不收斂、HIGH(閾值 10) 會收斂成 1 張。
        val firstSource = FakeFrameSource(plan = plan, perSheet = 2, hashes = listOf(0x00L, 0xFFL))
        val secondSource = FakeFrameSource(plan = plan, perSheet = 2, hashes = listOf(0x00L, 0xFFL))
        var calls = 0
        val vm = WizardViewModel(
            data = FakeData(emptySet()),
            frameSourceFactory = { if (calls++ == 0) firstSource else secondSource },
            strength = strengthFlow,
            hintSeen = flowOf(true),
            onHintSeen = {},
        )

        vm.openRecent("vid")
        shadowOf(Looper.getMainLooper()).idle()
        val oldStore = vm.step2.value!!
        awaitMain { !oldStore.state.value.converging }
        assertEquals(listOf(0, 1), oldStore.state.value.kept)

        vm.back()
        vm.openRecent("vid")
        shadowOf(Looper.getMainLooper()).idle()
        val newStore = vm.step2.value!!
        awaitMain { !newStore.state.value.converging }
        assert(oldStore !== newStore)

        strengthFlow.value = FilterStrength.HIGH
        awaitMain { newStore.state.value.kept.size == 1 }

        assertEquals("新 store 該用新強度重新收斂", listOf(0), newStore.state.value.kept)
        assertEquals("舊 store 已經 close，不該再被更新", listOf(0, 1), oldStore.state.value.kept)
    }
}
