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
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.player.FakePlayer
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import android.os.Looper
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

    private val meta = VideoMeta(
        videoId = "vid", title = "宜蘭兩天一夜", channelTitle = "某人",
        publishedAt = "2026-07-12T10:00:00Z", durationSec = 300,
        privacy = "public", playableInEmbed = true,
    )

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
}
