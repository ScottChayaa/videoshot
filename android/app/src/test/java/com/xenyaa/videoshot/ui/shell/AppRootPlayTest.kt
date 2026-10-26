package com.xenyaa.videoshot.ui.shell

import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.FacetUsage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.paging.FolderCursor
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.core.youtube.FetchResult
import com.xenyaa.videoshot.core.youtube.VideoMeta
import com.xenyaa.videoshot.core.youtube.WatchPage
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.DraftEntity
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.FolderPage
import com.xenyaa.videoshot.data.repo.model.NewShot
import com.xenyaa.videoshot.data.repo.model.Page
import com.xenyaa.videoshot.data.repo.model.RecentVideo
import com.xenyaa.videoshot.data.repo.model.ShotRow
import com.xenyaa.videoshot.data.settings.ShellSettings
import com.xenyaa.videoshot.query.FakeGeminiClient
import com.xenyaa.videoshot.ui.account.AccountDeps
import com.xenyaa.videoshot.ui.account.FakeAccountDeps
import com.xenyaa.videoshot.query.QueryResolver
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
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun row(id: Long, atSec: Double) = ShotRow(
    id = id, videoId = "v1", atSec = atSec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
    eventDate = "2026-03-05", place = null, description = null,
)

/**
 * 原片播放的接線：Lightbox【原片播放】→ 全螢幕播放 → 返回回到 Lightbox；`⋯`【重新取圖】刪掉舊收藏、開精靈。
 * fixture 手法同 [AppRootFoldersTest]，這裡只在意這一條路徑用得到的假資料。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootPlayTest {

    @get:Rule val compose = createComposeRule()

    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var homeItems = listOf(row(1L, 30.0))
        var videoShots = listOf(row(1L, 30.0), row(2L, 90.0))
        var video: VideoEntity? = VideoEntity("v1", "旅行影片", "c", "2026-03-05T00:00:00Z", 600, "public", null, 1L)
        var deletedVideoIds = mutableListOf<String>()

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(homeItems, null)
        override suspend fun monthCounts() = emptyList<com.xenyaa.videoshot.data.repo.model.MonthCount>()
        override suspend fun shotsOfVideo(videoId: String) = videoShots
        override suspend fun videoById(videoId: String) = video
        override suspend fun deleteVideo(videoId: String) { deletedVideoIds += videoId; homeItems = emptyList(); videoShots = emptyList() }
    }

    /** 跟 `AppRootFoldersTest.kt`／`AppRootLightboxTest.kt` 一樣，各檔案各自私有一份（沒有共用的測試替身）。 */
    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val clipMuted = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun setClipMuted(value: Boolean) = Unit
        override val playerMuted = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun setPlayerMuted(value: Boolean) = Unit
        override val folderSort = flowOf(FolderSort.NAME_ASC)
        override suspend fun setFolderSort(value: FolderSort) = Unit
        override val restoreDecisionMade = flowOf(true)
        override suspend fun markRestoreDecisionMade() = Unit
    }

    private val loader = ThumbLoader(
        thumbs = object : Thumbs {
            override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
            override fun fileOf(key: ThumbKey) = java.io.File("/unused")
            override fun exists(key: ThumbKey) = false
            override suspend fun delete(key: ThumbKey) = Unit
            override suspend fun deleteVideo(videoId: String) = Unit
        },
        decodeFile = { null }, decodeBytes = { null }, cover = { null },
    )

    private fun deps(repo: Repo): AppRootDeps = object : AppRootDeps {
        override val libraryRepo: LibraryRepo = repo
        override val thumbLoader: ThumbLoader = loader
        override val facetUsage: FacetUsage by lazy { FacetUsage(libraryRepo, FakeCacheRepo()) { 0L } }
        override val shotDeleter: ShotDeleter = ShotDeleter(
            repo,
            thumbs = object : Thumbs {
                override suspend fun thumbFor(shot: ShotRow) = ThumbSource.Placeholder
                override fun fileOf(key: ThumbKey) = java.io.File("/unused")
                override fun exists(key: ThumbKey) = false
                override suspend fun delete(key: ThumbKey) = Unit
                override suspend fun deleteVideo(videoId: String) = Unit
            },
            cache = object : CacheRepo {
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
                override suspend fun saveClipDraft(draft: com.xenyaa.videoshot.core.timeline.ClipDraft, now: Long) = Unit
                override suspend fun currentClipDraft(): com.xenyaa.videoshot.core.timeline.ClipDraft? = null
                override suspend fun clearClipDraft() = Unit
                override suspend fun clearAll() = Unit
            },
            io = Dispatchers.Unconfined,
        )
        override val wizardData: WizardData = object : WizardData {
            override suspend fun watchPage(videoId: String) = WatchPage(
                FetchResult.OK,
                VideoMeta(videoId, "旅行影片", "c", "2026-03-05T00:00:00Z", 600, "public", true),
                null,
            )
            override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
            override suspend fun takenFrameIndexes(videoId: String, level: Int) = emptySet<Int>()
            override suspend fun distinctPlaces() = emptyList<String>()
            override suspend fun tagSuggestions() = emptyList<String>()
            override suspend fun cropThumbs(videoId: String, sbSpec: String?, frameIndexes: List<Int>, onProgress: (Int, Int) -> Unit) =
                CropOutcome(emptyList(), emptyList())
            override suspend fun commit(video: VideoEntity, picks: List<NewShot>) = emptyList<Long>()
            override suspend fun markThumbStates(videoId: String, level: Int, ok: List<Int>, missing: List<Int>) = Unit
            override suspend fun saveDraft(json: String) = Unit
            override suspend fun currentDraft(): String? = null
            override suspend fun clearDraft(videoId: String) = Unit
        }
        override val haptics = FakeHaptics()
        override val settings: ShellSettings = FakeShellSettings()
        override val queryResolver: QueryResolver = QueryResolver(
            gemini = FakeGeminiClient(null),
            geminiKey = { null },
            vocabulary = { QueryVocabulary(emptyList(), emptyList()) },
        )
        // 【重新取圖】會開精靈第二步
        override fun frameSourceFor(video: LoadedVideo): FrameSource = FakeFrameSource.of(frameCount = 1, intervalSec = 0.0)
        override fun manualImagesFor(videoId: String) = throw UnsupportedOperationException("測試不用到")
        override fun captureFor(player: com.xenyaa.videoshot.player.Player) = null
        override val accountDeps: AccountDeps = FakeAccountDeps()
        override suspend fun listBackups() = error("這組測試不碰還原")
        override suspend fun restore(backup: com.xenyaa.videoshot.backup.RemoteBackup) = error("這組測試不碰還原")
    }

    private fun openPlay(repo: Repo) {
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }
        // 首頁縮圖的 contentDescription 是「片段縮圖 MM:SS」（HomeScreen 用 labelOf），
        // row(1L, 30.0) 的 30 秒換算成 formatClock 是 00:30
        compose.onNodeWithContentDescription("片段縮圖 00:30").performClick()
        compose.onNodeWithText("原片播放").performClick()
    }

    @Test
    fun lightbox原片播放進全螢幕_上緣有標題沒有底部導覽() {
        openPlay(Repo())

        compose.onNodeWithText("旅行影片").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        compose.onNodeWithContentDescription("這支影片的更多操作").assertIsDisplayed()
        compose.onNodeWithText("照片").assertDoesNotExist()
        compose.onNodeWithContentDescription("全螢幕").assertDoesNotExist()
    }

    @Test
    fun 返回回到lightbox() {
        openPlay(Repo())

        compose.onNodeWithContentDescription("返回").performClick()

        compose.onNodeWithText("原片播放").assertIsDisplayed()
    }

    @Test
    fun 重新取圖_確認後刪掉整支收藏並開精靈() {
        val repo = Repo()
        openPlay(repo)

        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("重新取圖").performClick()
        compose.onNodeWithText("將刪除這支影片已收藏的 2 張，再從第二步重新挑畫面。").assertIsDisplayed()
        compose.onNodeWithText("重新取圖", substring = false).performClick()
        compose.waitForIdle()

        assertEquals(listOf("v1"), repo.deletedVideoIds)
        // 原片播放收掉、到了取圖分頁
        compose.onNodeWithContentDescription("這支影片的更多操作").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("旅行影片").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun 繼續取圖_不刪收藏直接開精靈() {
        val repo = Repo()
        openPlay(repo)

        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("繼續取圖").performClick()
        compose.waitForIdle()

        assertTrue(repo.deletedVideoIds.isEmpty())
        // 沒有確認框；原片播放收掉、到了取圖分頁
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.onNodeWithContentDescription("這支影片的更多操作").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("旅行影片").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun 重新取圖_按取消什麼都不刪() {
        val repo = Repo()
        openPlay(repo)

        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("重新取圖").performClick()
        compose.onNodeWithText("取消").performClick()

        assertTrue(repo.deletedVideoIds.isEmpty())
        compose.onNodeWithText("旅行影片").assertIsDisplayed()
    }
}
