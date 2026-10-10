package com.xenyaa.videoshot.ui.shell

import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.FacetUsage
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.AccountStats
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.settings.ShellSettings
import com.xenyaa.videoshot.query.FakeGeminiClient
import com.xenyaa.videoshot.query.QueryResolver
import com.xenyaa.videoshot.thumbs.ThumbKey
import com.xenyaa.videoshot.thumbs.ThumbSource
import com.xenyaa.videoshot.thumbs.Thumbs
import com.xenyaa.videoshot.ui.account.AccountDeps
import com.xenyaa.videoshot.ui.account.FakeAccountDeps
import com.xenyaa.videoshot.ui.theme.VideoshotTheme
import com.xenyaa.videoshot.ui.thumb.ThumbLoader
import com.xenyaa.videoshot.wizard.CropOutcome
import com.xenyaa.videoshot.wizard.FakeHaptics
import com.xenyaa.videoshot.wizard.LoadedVideo
import com.xenyaa.videoshot.wizard.WizardData
import com.xenyaa.videoshot.wizard.frames.FrameSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首次開啟的閘門本身（`AppRoot` 最外層那幾行）——先前沒有任何測試照到它，而最終審查
 * Important 2 正是活在這裡：`restoreDecisionMade` 是 false **不等於**「全新安裝」，
 * 既有安裝升級到這一版時 DataStore 裡本來就沒有這個 key，預設值也是 false。
 * 那台裝置本機明明有整個圖庫，卻會被「歡迎使用 videoshot」擋在門外。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootFirstRunGateTest {

    @get:Rule val compose = createComposeRule()

    private class SeededRepo(private val totalShots: Int) : FakeLibraryRepo() {
        override suspend fun accountStats(thisMonth: String) = AccountStats(totalShots, 0, 0)
    }

    private class FakeCacheRepo : CacheRepo {
        override suspend fun putThumbStates(states: List<ThumbStateEntity>) = Unit
        override suspend fun thumbState(videoId: String, sbLevel: Int, frameIndex: Int): ThumbStateEntity? = null
        override suspend fun thumbsDueForRetry(now: Long, limit: Int): List<ThumbStateEntity> = emptyList()
        override suspend fun forgetVideoThumbs(videoId: String) = Unit
        override suspend fun forgetThumb(videoId: String, sbLevel: Int, frameIndex: Int) = Unit
        override suspend fun countByState(state: String): Int = 0
        override suspend fun lostThumbs(): List<ThumbStateEntity> = emptyList()
        override suspend fun resetLostToMissing(now: Long) = Unit
        override suspend fun saveDraft(draft: DraftEntity) = Unit
        override suspend fun currentDraft(): DraftEntity? = null
        override suspend fun clearDraft() = Unit
        override suspend fun touchFacets(refs: Collection<com.xenyaa.videoshot.data.repo.model.FacetRef>, usedAt: Long) = Unit
        override suspend fun facetRecent(): Map<com.xenyaa.videoshot.data.repo.model.FacetRef, Long> = emptyMap()
        override suspend fun mergeFacetRecent(kind: Int, fromId: Long, toId: Long) = Unit
        override suspend fun forgetFacetRecent(kind: Int, id: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    private class FakeThumbs : Thumbs {
        override suspend fun thumbFor(shot: com.xenyaa.videoshot.data.repo.model.ShotRow) =
            ThumbSource.LocalFile(File("/fake/${shot.id}"))
        override fun fileOf(key: ThumbKey) = File("/unused")
        override fun exists(key: ThumbKey) = false
        override suspend fun delete(key: ThumbKey) = Unit
        override suspend fun deleteVideo(videoId: String) = Unit
    }

    private class NoopWizardData : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(FetchResult.OK, null, null)
        override suspend fun recentVideos(limit: Int) = emptyList<com.xenyaa.videoshot.data.repo.model.RecentVideo>()
        override suspend fun takenFrameIndexes(videoId: String, level: Int): Set<Int> = emptySet()
        override suspend fun distinctPlaces(): List<String> = emptyList()
        override suspend fun allTagNames(): List<String> = emptyList()
        override suspend fun cropThumbs(videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit) =
            CropOutcome(emptyList(), emptyList())
        override suspend fun commit(video: VideoEntity, picks: List<NewShot>): List<Long> = emptyList()
        override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
        override suspend fun saveDraft(json: String) = Unit
        override suspend fun currentDraft(): String? = null
        override suspend fun clearDraft(videoId: String) = Unit
    }

    /** `restoreDecisionMade` 是可變的：閘門判斷「本機有資料」之後會呼叫 `markRestoreDecisionMade`。 */
    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen = flowOf(true)
        override suspend fun markLightboxHintSeen() = Unit
        override val folderSort = flowOf(FolderSort.NAME_ASC)
        override suspend fun setFolderSort(value: FolderSort) = Unit

        val decisionFlow = MutableStateFlow(false)
        var markCalls = 0
        override val restoreDecisionMade = decisionFlow
        override suspend fun markRestoreDecisionMade() { markCalls++; decisionFlow.value = true }
    }

    private class Fixture(totalShots: Int) : AppRootDeps {
        private val repo = SeededRepo(totalShots)
        private val cache = FakeCacheRepo()
        private val fakeThumbs = FakeThumbs()
        val fakeSettings = FakeShellSettings()

        override val libraryRepo: LibraryRepo = repo
        override val thumbLoader = ThumbLoader(
            thumbs = fakeThumbs,
            decodeFile = { ImageBitmap(1, 1) },
            decodeBytes = { null },
            cover = { null },
        )
        override val facetUsage: FacetUsage by lazy { FacetUsage(libraryRepo, FakeCacheRepo()) { 0L } }
        override val shotDeleter = ShotDeleter(repo, fakeThumbs, cache, Dispatchers.Default)
        override val wizardData: WizardData = NoopWizardData()
        override val haptics = FakeHaptics()
        override val settings: ShellSettings = fakeSettings
        override val queryResolver: QueryResolver = QueryResolver(
            gemini = FakeGeminiClient(null),
            geminiKey = { null },
            vocabulary = { QueryVocabulary(emptyList(), emptyList()) },
        )
        override fun frameSourceFor(video: LoadedVideo): FrameSource = error("這組測試不碰精靈第二步")
        override fun manualImagesFor(videoId: String) = error("這組測試不碰手動圖")
        override fun captureFor(player: com.xenyaa.videoshot.player.Player) = null
        override val accountDeps: AccountDeps = FakeAccountDeps()
        override suspend fun listBackups() = error("這組測試不碰還原")
        override suspend fun restore(backup: com.xenyaa.videoshot.backup.RemoteBackup) = error("這組測試不碰還原")
    }

    private fun show(totalShots: Int): Fixture {
        val fixture = Fixture(totalShots)
        compose.setContent { VideoshotTheme { AppRoot(container = fixture, onExitApp = {}) } }
        compose.waitForIdle()
        return fixture
    }

    /** 真正的全新安裝（旗標 false ＋ 本機沒有任何收藏）：閘門要出現。 */
    @Test
    fun 全新安裝時顯示首次開啟的選擇畫面() {
        val fixture = show(totalShots = 0)
        compose.onNodeWithText("歡迎使用 videoshot").assertIsDisplayed()
        assertTrue("沒有回答之前不該擅自標記", fixture.fakeSettings.markCalls == 0)
    }

    /**
     * 最終審查 Important 2：旗標還是 false，但本機已經有收藏——這是既有安裝升級，不是全新安裝。
     * 閘門不該出現，而且旗標要被補寫回去，下一次啟動就能只看旗標短路。
     */
    @Test
    fun 既有安裝升級時不顯示閘門並補寫旗標() {
        val fixture = show(totalShots = 42)
        compose.onNodeWithText("歡迎使用 videoshot").assertDoesNotExist()
        compose.onNode(hasText("照片") and isSelectable()).assertIsDisplayed()
        assertTrue("要把旗標補寫回去", fixture.fakeSettings.markCalls == 1)
    }
}
