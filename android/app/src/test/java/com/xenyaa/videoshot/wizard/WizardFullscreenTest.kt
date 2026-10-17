package com.xenyaa.videoshot.wizard

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.player.LocalPlayerFullscreen
import com.xenyaa.videoshot.player.PlayerFullscreenState
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 第二步全螢幕：藏起頂欄與縮圖牆，播放器留在原地（規格第六節「播放器」）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardFullscreenTest {

    @get:Rule val compose = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private class FakeData : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(
            FetchResult.OK,
            VideoMeta(videoId, "宜蘭兩天一夜", "ch", "2025-07-12T10:00:00-07:00", 600, "public", true),
            "spec",
        )
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
        override suspend fun distinctPlaces() = emptyList<String>()
        override suspend fun tagSuggestions() = emptyList<com.xenyaa.videoshot.data.library.dao.TagNameKind>()
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

    /** 已經在第二步、loaded 有 meta 的 ViewModel。 */
    private fun step2Vm(frameCount: Int = 3): WizardViewModel {
        val vm = WizardViewModel(
            data = FakeData(),
            frameSourceFactory = { FakeFrameSource.of(frameCount = frameCount, intervalSec = 10.0) },
            strength = flowOf(FilterStrength.MEDIUM),
            hintSeen = flowOf(true),
            onHintSeen = {},
            manualImages = { com.xenyaa.videoshot.capture.ManualImageStore(java.io.File(tmp.root, "manual")) },
            captureFor = { null },
        )
        vm.openRecent("vid")
        shadowOf(Looper.getMainLooper()).idle()
        return vm
    }

    @Test
    fun 全螢幕時藏起頂欄與縮圖牆() {
        val fs = PlayerFullscreenState()
        val vm = step2Vm()
        compose.setContent {
            CompositionLocalProvider(LocalPlayerFullscreen provides fs) {
                WizardScreen(vm, FakeHaptics(), onExit = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("宜蘭兩天一夜").assertIsDisplayed()
        compose.onNodeWithText("全選").assertIsDisplayed()
        compose.runOnUiThread { fs.enter(landscape = true) }
        compose.waitForIdle()
        compose.onNodeWithText("宜蘭兩天一夜").assertDoesNotExist()
        compose.onNodeWithText("全選").assertDoesNotExist()
        compose.onNodeWithText("下一步", substring = true).assertDoesNotExist()
        compose.runOnUiThread { fs.exit() }
        compose.waitForIdle()
        compose.onNodeWithText("宜蘭兩天一夜").assertIsDisplayed()
        compose.onNodeWithText("全選").assertIsDisplayed()
    }

    /** 捲到後面幾列、進全螢幕再離開，縮圖牆要停在原來的位置，不跳回頂端。 */
    @Test
    fun 離開全螢幕後縮圖牆停在原來捲到的位置() {
        val fs = PlayerFullscreenState()
        val vm = step2Vm(frameCount = 80)
        compose.setContent {
            CompositionLocalProvider(LocalPlayerFullscreen provides fs) {
                WizardScreen(vm, FakeHaptics(), onExit = {})
            }
        }
        compose.waitForIdle()
        // 捲動前，第 1 格在畫面上、第 60 格不在
        compose.onNodeWithContentDescription("第 1 格 00:00").assertIsDisplayed()
        compose.onNodeWithContentDescription("第 60 格 09:50").assertDoesNotExist()

        compose.onNode(hasScrollAction()).performScrollToIndex(59)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("第 60 格 09:50").assertIsDisplayed()

        compose.runOnUiThread { fs.enter(landscape = true) }
        compose.waitForIdle()
        compose.runOnUiThread { fs.exit() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("第 60 格 09:50").assertIsDisplayed()
        compose.onNodeWithContentDescription("第 1 格 00:00").assertDoesNotExist()
    }
}
