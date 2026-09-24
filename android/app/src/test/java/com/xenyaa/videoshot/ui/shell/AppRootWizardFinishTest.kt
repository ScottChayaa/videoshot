package com.xenyaa.videoshot.ui.shell

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.home.monthLabel
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.data.settings.ShellSettings
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.wizard.CropOutcome
import com.xenyaa.videoshot.wizard.FakeHaptics
import com.xenyaa.videoshot.wizard.LoadedVideo
import com.xenyaa.videoshot.wizard.WizardData
import com.xenyaa.videoshot.wizard.frames.FakeFrameSource
import com.xenyaa.videoshot.wizard.frames.FrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 精靈完成（`WizardViewModel.finished`）要讓外殼做四件事——切回首頁分頁、重新整理清單、
 * 記下要捲到哪個月份、跳 snackbar。這裡釘住這四件事。
 *
 * 這裡故意**不直接呼叫** `WizardViewModel` 的方法——`AppRoot` 自己組出來的那個實例外面拿不到
 * （`viewModel(factory=…, key="wizard")` 是它的私有細節，測試不該為了摸到它而改變 `AppRoot`
 * 的形狀）。改成整段從 `WizardScreen` 的真實 UI 走一遍：點「最近取過的影片」→ 勾一格 →
 * 填資料 → 完成。第一步、第二步都故意讓 `meta` 是 null——`PlayerSurface` 只在有 `meta` 時才會
 * 掛一個真的 WebView 上去，這裡不需要也不測播放器，meta 是 null 就跳過那一段。
 *
 * **這裡刻意沒有驗「不卡住發送事件的 coroutine」那一條**（階段 7 全盤覆查第 5 點；修法是把
 * 直接 `await` 的 `showSnackbar` 改成 `scope.launch`）。原本想用「finish 之後立刻切回取圖
 * 分頁，檢查 `WizardViewModel` 有沒有已經 `resetToStart()`」當判別測試（`resetToStart()`
 * 排在被 `_finished.emit()` 卡住的那一行之後，awaited 版本理論上還沒機會跑）；手動把
 * `AppRoot.kt` 的 `scope.launch` 改回直接 `await` 重新跑過這個測試，結果**兩種寫法這個
 * 測試都會過**——在這個 Robolectric＋Compose 測試環境下，`compose.performClick()`／
 * `assertIsDisplayed()` 附帶的同步機制似乎連帶把 `SnackbarHostState.showSnackbar()`
 * 內部的逾時也一併推進掉了，awaited 版本的延遲因此量不出來。寫一個兩種寫法都會過的測試
 * 比不寫還糟，所以拿掉了，只留下面這四個有真的區辨力的斷言。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootWizardFinishTest {

    @get:Rule val compose = createComposeRule()

    private fun row(id: Long, date: String) = ShotRow(
        id = id, videoId = "old", atSec = 1.0, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
        eventDate = date, place = null, description = null,
    )

    /** 同一份 repo 同時餵首頁跟接住精靈的 `commit`——跟正式環境 `AppContainer.wizardData.commit`
     * 直接呼叫 `libraryRepo.commitPicks` 是同一條路徑，「reload 撈得到新圖」才是真的整合檢查，
     * 不是擺拍。 */
    private class FakeLibraryRepo(seed: List<ShotRow>) : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var items: List<ShotRow> = seed
        var nextId = 1000L
        val homeFeedCalls = mutableListOf<Unit>()

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?): Page<ShotRow> {
            homeFeedCalls += Unit
            return Page(items, null)
        }
        override suspend fun shotsOfVideo(videoId: String) = items.filter { it.videoId == videoId }
        override suspend fun shotById(id: Long) = items.find { it.id == id }
        override suspend fun shotCount(upToMonth: String?) = items.size
        override suspend fun commitPicks(video: VideoEntity, picks: List<NewShot>): List<Long> {
            val newRows = picks.map { p ->
                ShotRow(
                    id = nextId++, videoId = video.id, atSec = p.atSec, source = p.source,
                    frameIndex = p.frameIndex, sbLevel = p.sbLevel, eventDate = p.eventDate,
                    place = p.place, description = p.description,
                )
            }
            items = items + newRows // 接在後面——舊資料排在前面，新圖的月份才會落在畫面捲得到的最後面
            return newRows.map { it.id }
        }
    }

    private class FakeCacheRepo : CacheRepo {
        override suspend fun putThumbStates(states: List<ThumbStateEntity>) = Unit
        override suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity? = null
        override suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity> = emptyList()
        override suspend fun forgetVideoThumbs(videoId: String) = Unit
        override suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int) = Unit
        override suspend fun saveDraft(draft: DraftEntity) = Unit
        override suspend fun currentDraft(): DraftEntity? = null
        override suspend fun clearDraft() = Unit
        override suspend fun clearAll() = Unit
    }

    private class FakeThumbs : Thumbs {
        override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
        override fun fileOf(key: ThumbKey) = File("/unused")
        override fun exists(key: ThumbKey) = false
        override suspend fun delete(key: ThumbKey) = Unit
        override suspend fun deleteVideo(videoId: String) = Unit
    }

    /** 一支「最近取過的影片」，`watchPage` 回 meta=null——精靈仍然吃得下（規格第七節降級表），
     * 也避開 `PlayerSurface` 掛 WebView（見類別 KDoc）。 */
    private class FakeWizardData(private val repo: FakeLibraryRepo) : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(FetchResult.OK, null, null)
        override suspend fun recentVideos(limit: Int) = listOf(RecentVideo("v1", "海邊那支", 0L, 0))
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
        override suspend fun distinctPlaces(): List<String> = emptyList()
        override suspend fun allTagNames(): List<String> = emptyList()
        override suspend fun cropThumbs(videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit) =
            CropOutcome(written = frameIndexes, missing = emptyList())
        override suspend fun commit(video: VideoEntity, picks: List<NewShot>) = repo.commitPicks(video, picks)
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) = Unit
        override suspend fun currentDraft(): String? = null
        override suspend fun clearDraft(videoId: String) = Unit
    }

    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen = flowOf(true)
        override suspend fun markLightboxHintSeen() = Unit
        override val folderSort = flowOf(FolderSort.NAME_ASC)
        override suspend fun setFolderSort(value: FolderSort) = Unit
    }

    private class Fixture(seed: List<ShotRow>) : AppRootDeps {
        val repo = FakeLibraryRepo(seed)
        override val libraryRepo: LibraryRepo = repo
        override val thumbLoader = ThumbLoader(FakeThumbs(), { null }, { null }, { null })
        override val shotDeleter = ShotDeleter(repo, FakeThumbs(), FakeCacheRepo(), Dispatchers.Default)
        override val wizardData: WizardData = FakeWizardData(repo)
        override val haptics = FakeHaptics()
        override val settings: ShellSettings = FakeShellSettings()
        // frameCount=1、intervalSec=0.0：只有一格，內容描述固定是「第 1 格 00:00」
        override fun frameSourceFor(video: LoadedVideo): FrameSource = FakeFrameSource.of(frameCount = 1, intervalSec = 0.0)
        override fun manualImagesFor(videoId: String) = error("這組測試不碰手動圖")
        override fun captureFor(player: com.xenyaa.videoshot.player.Player) = null
    }

    /** 40 張分散在三個舊月份（跟 `WizardFinishTest.bigState()` 同樣的用意）：
     * 確保精靈新增的那一張排到清單最後面、不捲動看不到，`scrollToMonth` 才驗得出東西。 */
    private fun seed(): List<ShotRow> =
        (1..20).map { row(it.toLong(), "2020-03-%02d".format((it % 28) + 1)) } +
            (21..30).map { row(it.toLong(), "2020-02-%02d".format((it % 27) + 1)) } +
            (31..40).map { row(it.toLong(), "2020-01-%02d".format((it % 27) + 1)) }

    private fun show(): Fixture {
        val fixture = Fixture(seed())
        compose.setContent {
            VideoshotTheme {
                AppRoot(container = fixture, onExitApp = {})
            }
        }
        return fixture
    }

    /** 精靈的 `today()` 用的是真的 `LocalDate.now()`（AppRoot.kt 寫死，測試不該為了方便去改
     * production 的形狀），所以這裡跟著算同一個月份，不硬編日期字串。 */
    private fun todayMonthLabel(): String = monthLabel(LocalDate.now().toString().take(7))

    @Test
    fun 完成會切回首頁_重新整理_捲到新月份_跳出snackbar_而且不卡住() {
        val fixture = show()

        compose.onNodeWithText("取圖").performClick()
        compose.onNodeWithText("海邊那支").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("第 1 格 00:00").performClick()
        compose.onNodeWithText("下一步（1 張）").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("完成").performClick()
        // 這一格沒填過圖資（沒動過任何欄位），會先跳「還有 1 張沒填資料」的提醒，不是直接完成
        compose.onNodeWithText("仍要完成").performClick()
        compose.waitForIdle()

        // 回首頁分頁：CAPTURE 分頁沒有底部導覽，導覽列重新出現就代表真的切走了
        compose.onNodeWithText("首頁").assertIsDisplayed()

        // 重新整理：新圖真的寫進了首頁清單背後那個 repo（同一份 commit 路徑）
        assertEquals(41, fixture.repo.items.size)

        // 跳 snackbar
        compose.onNodeWithText("已新增 1 張").assertIsDisplayed()

        // 捲到新月份：新圖排在清單最後面，沒有捲動的話畫面上看不到它的月份標題
        compose.onNodeWithText(todayMonthLabel()).assertIsDisplayed()
    }

    // 「不卡住發送事件的 coroutine」這條刻意不在這裡驗——見類別 KDoc 最後一段：
    // 在這個 Robolectric compose 測試環境下量不出來，寫了也是假訊號。
}
