package com.xenyaa.videoshot.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import com.xenyaa.videoshot.data.repo.model.ShotPatch
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
 * 詳情頁的接線：Lightbox【播放這一段】→ 詳情頁 → `⋯`【刪除整支收藏】回首頁。
 * fixture 手法同 [AppRootFoldersTest]，這裡只在意詳情頁這一條路徑用得到的假資料。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootDetailTest {

    @get:Rule val compose = createComposeRule()

    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var homeItems = listOf(row(1L, 30.0))
        var videoShots = listOf(row(1L, 30.0), row(2L, 90.0))
        var video: VideoEntity? = VideoEntity("v1", "旅行影片", "c", "2026-03-05T00:00:00Z", 600, "public", null, 1L)
        var deletedVideoIds = mutableListOf<String>()

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(homeItems, null)
        override suspend fun monthCounts() = emptyList<com.xenyaa.videoshot.data.repo.model.MonthCount>()
        override suspend fun shotCount(upToMonth: String?) = homeItems.size
        override suspend fun shotsOfVideo(videoId: String) = videoShots
        override suspend fun videoById(videoId: String) = video
        override suspend fun deleteVideo(videoId: String) { deletedVideoIds += videoId; homeItems = emptyList(); videoShots = emptyList() }

        // 批次編輯的【完成】真的要寫回——Finding 1 的迴歸測試得看得出「重進一次批次編輯
        // 不會拿到上一輪的舊資料」，這代表 shotsOfVideo() 下一次要回傳更新後的值
        override suspend fun patchShots(ids: List<Long>, patch: ShotPatch) {
            videoShots = videoShots.map { row ->
                if (row.id in ids) row.copy(eventDate = patch.eventDate ?: row.eventDate, place = patch.place, description = patch.description) else row
            }
        }
    }

    /** 跟 `AppRootFoldersTest.kt`／`AppRootLightboxTest.kt` 一樣，各檔案各自私有一份（沒有共用的測試替身）。 */
    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen = flowOf(true)
        override suspend fun markLightboxHintSeen() = Unit
        override val folderSort = flowOf(FolderSort.NAME_ASC)
        override suspend fun setFolderSort(value: FolderSort) = Unit
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
                override suspend fun saveDraft(draft: DraftEntity) = Unit
                override suspend fun currentDraft(): DraftEntity? = null
                override suspend fun clearDraft() = Unit
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
            override suspend fun allTagNames() = emptyList<String>()
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
        override fun frameSourceFor(video: LoadedVideo): FrameSource = throw UnsupportedOperationException("測試不用到")
        override fun manualImagesFor(videoId: String) = throw UnsupportedOperationException("測試不用到")
        override fun captureFor(player: com.xenyaa.videoshot.player.Player) = null
        override val accountDeps: AccountDeps = FakeAccountDeps()
    }

    @Test
    fun lightbox播放這一段換成詳情頁() {
        val repo = Repo()
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }

        // 首頁縮圖的 contentDescription 是「片段縮圖 MM:SS」（HomeScreen 用 labelOf），
        // row(1L, 30.0) 的 30 秒換算成 formatClock 是 00:30
        compose.onNodeWithContentDescription("片段縮圖 00:30").performClick()
        compose.onNodeWithText("播放這一段").performClick()

        compose.onNodeWithText("旅行影片").assertIsDisplayed()
        compose.onNodeWithText("這支影片的收藏 (2)").assertIsDisplayed()
    }

    @Test
    fun 刪除整支收藏會回首頁() {
        val repo = Repo()
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }

        compose.onNodeWithContentDescription("片段縮圖 00:30").performClick()
        compose.onNodeWithText("播放這一段").performClick()
        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("刪除整支收藏").performClick()
        compose.onNodeWithText("刪除", substring = false).performClick()

        assertEquals(listOf("v1"), repo.deletedVideoIds)
    }

    /**
     * 最終審查 Finding 1 ＋ Finding 6：詳情頁 →【批次編輯圖資】→ 改一張的地點 → 套用 → 完成
     * → 回到詳情頁看得到新地點（`finished` 收集區塊真的 pop＋reload 了）；接著**再進一次**
     * 批次編輯——`BatchEditViewModel` 是用 videoId 當 key 快取的，同一支影片第二次進來拿到
     * 同一個實例，這裡驗證它沒有帶著上一輪「1 已完成」的舊 `Step3Store`（沒修好的話這裡會顯示
     * 「2 張 · 1 已完成」，而且按【完成】會把剛存的新地點蓋回舊值）。
     */
    @Test
    fun 批次編輯完成後圖資更新_再進一次不會殘留上一輪的套用狀態() {
        val repo = Repo()
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }

        compose.onNodeWithContentDescription("片段縮圖 00:30").performClick()
        compose.onNodeWithText("播放這一段").performClick()
        compose.onNodeWithText("旅行影片").assertIsDisplayed()

        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("批次編輯圖資").performClick()
        compose.onNodeWithText("2 張 · 0 已完成").assertIsDisplayed()

        // 只留第一張（00:30）勾選——把第二張（01:30）那一格點掉
        compose.onNodeWithContentDescription("01:30", substring = true).performClick()
        compose.onNodeWithContentDescription("地點").performTextReplacement("羅東")
        compose.onNodeWithText("套用到 1 張").performClick()
        compose.onNodeWithText("完成").performClick()

        // 回到詳情頁：finished 收集區塊 pop 掉批次編輯、detailVm 重查，聚焦張的地點是新值
        compose.onNodeWithText("旅行影片").assertIsDisplayed()
        compose.onNodeWithText("羅東").assertIsDisplayed()

        // 再進一次批次編輯——沒有第二次的「1 已完成」殘留
        compose.onNodeWithContentDescription("這支影片的更多操作").performClick()
        compose.onNodeWithText("批次編輯圖資").performClick()
        compose.onNodeWithText("2 張 · 0 已完成").assertIsDisplayed()
    }

    /**
     * 最終審查 Finding 2：Lightbox【播放這一段】帶著 `focusShotId` 進詳情頁；`DetailViewModel`
     * 用 videoId 當 key 快取，同一支影片第二次進來（播另一張截圖）拿到同一個實例。
     * 沒修好的話這裡第二次還是卡在第一張的秒數。
     */
    @Test
    fun lightbox播放同一支影片的另一張_詳情頁改聚焦新的那張() {
        val repo = Repo().apply { homeItems = listOf(row(1L, 30.0), row(2L, 90.0)) }
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }

        compose.onNodeWithContentDescription("片段縮圖 00:30").performClick()
        compose.onNodeWithText("播放這一段").performClick()
        compose.onNodeWithText("影片 00:30").assertIsDisplayed()

        // 返回首頁，播第二張——同一支影片（v1），detailVm 是同一個快取實例
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("片段縮圖 01:30").performClick()
        compose.onNodeWithText("播放這一段").performClick()

        compose.onNodeWithText("影片 01:30").assertIsDisplayed()
    }
}
