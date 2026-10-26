package com.xenyaa.videoshot.ui.shell

import com.xenyaa.videoshot.data.repo.FakeCacheRepo
import com.xenyaa.videoshot.data.FacetUsage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.xenyaa.videoshot.ui.clip.ClipDeps
import com.xenyaa.videoshot.media.MediaFilter
import com.xenyaa.videoshot.media.MediaItem
import com.xenyaa.videoshot.media.MediaKind
import com.xenyaa.videoshot.media.MediaLibrary
import com.xenyaa.videoshot.media.MediaThumbLoader
import com.xenyaa.videoshot.media.mediaPermissionsFor
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

/**
 * 剪輯入口的接線：【新增剪輯】→ 選方向 → 選素材（沒有底部導覽）→ 【下一步】→ 編輯佔位 → 返回選取還在。
 * fixture 手法同 [AppRootPlayTest]；`clipDeps` 用可控的假實作。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootClipTest {

    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(emptyList<ShotRow>(), null)
        override suspend fun monthCounts() = emptyList<com.xenyaa.videoshot.data.repo.model.MonthCount>()
    }

    private class FakeClipDeps(var granted: Set<String>) : ClipDeps {
        val all = (1L..3L).map { MediaItem(it, "content://media/$it", MediaKind.PHOTO, it * 1000, null, 1080, 1920, 0) }
        var listCalls = 0
        override val mediaLibrary = object : MediaLibrary {
            override suspend fun list(filter: MediaFilter): List<MediaItem> { listCalls++; return all }
        }
        override val mediaThumbLoader = MediaThumbLoader(decode = { null })
        override fun sdkInt() = 34
        override fun grantedMediaPermissions() = granted
    }

    private val full = mediaPermissionsFor(34).toSet()

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

    private val repo = Repo()

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

    private fun deps(clip: ClipDeps): AppRootDeps = object : AppRootDeps {
        override val libraryRepo: LibraryRepo = repo
        override val clipDeps: ClipDeps = clip
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

    private fun openPicker(clip: ClipDeps) {
        compose.setContent { VideoshotTheme { AppRoot(deps(clip)) {} } }
        compose.onNodeWithText("剪輯").performClick()
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("直式").performClick()
        compose.waitForIdle()
    }

    @Test
    fun 新增剪輯選直式後進到選素材且沒有底部導覽() {
        openPicker(FakeClipDeps(full))

        compose.onNodeWithText("選素材").assertIsDisplayed()
        compose.onNodeWithText("相簿").assertDoesNotExist()
    }

    @Test
    fun 選了素材才能下一步_進編輯佔位再返回選取還在() {
        openPicker(FakeClipDeps(full))
        compose.onNodeWithText("下一步").assertIsNotEnabled()

        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（2）").performClick()

        compose.onNodeWithText("編輯").assertIsDisplayed()
        compose.onNodeWithText("已選 2 個素材，時間軸編輯在下一個階段加入").assertIsDisplayed()
        compose.onNodeWithText("相簿").assertDoesNotExist()

        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("下一步（2）").assertIsDisplayed()
        compose.onNodeWithContentDescription("已選第 1 個").assertExists()
        compose.onNodeWithContentDescription("已選第 2 個").assertExists()
    }

    @Test
    fun 沒權限時選素材顯示允許存取() {
        openPicker(FakeClipDeps(emptySet()))

        compose.onNodeWithText("需要相片和影片的存取權").assertIsDisplayed()
    }

    @Test
    fun 重新從選方向進來會清掉上一次的選取() {
        openPicker(FakeClipDeps(full))
        compose.onAllNodesWithContentDescription("照片")[0].performClick()
        compose.onNodeWithText("下一步（1）").assertIsDisplayed()

        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("新增剪輯").performClick()
        compose.onNodeWithText("橫式").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("下一步").assertIsNotEnabled()
    }

    /** 系統權限視窗是用 startActivity 開的；Robolectric 記得最近一次開了什麼。 */
    private fun permissionRequests(): Int {
        val shadow = org.robolectric.Shadows.shadowOf(compose.activity)
        var n = 0
        while (shadow.nextStartedActivity != null) n++
        return n
    }

    @Test
    fun 沒權限進選素材會自動請求一次() {
        openPicker(FakeClipDeps(emptySet()))
        assertEquals(1, permissionRequests())
    }

    @Test
    fun 完整存取進選素材不請求權限() {
        openPicker(FakeClipDeps(full))
        assertEquals(0, permissionRequests())
    }

    @Test
    fun 回到前景會重查權限_從系統設定給了權限後格子出現() {
        val clip = FakeClipDeps(emptySet())
        openPicker(clip)
        compose.onNodeWithText("需要相片和影片的存取權").assertIsDisplayed()

        // 使用者在系統設定裡開了完整存取，回到 app
        clip.granted = full
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("需要相片和影片的存取權").assertDoesNotExist()
        assertEquals(3, compose.onAllNodesWithContentDescription("照片").fetchSemanticsNodes().size)
    }
}
