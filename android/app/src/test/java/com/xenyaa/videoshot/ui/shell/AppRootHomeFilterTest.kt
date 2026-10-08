package com.xenyaa.videoshot.ui.shell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.paging.ShotCursor
import com.xenyaa.videoshot.core.query.QueryVocabulary
import com.xenyaa.videoshot.core.similarity.FilterStrength
import com.xenyaa.videoshot.data.ShotDeleter
import com.xenyaa.videoshot.data.cache.entity.ThumbStateEntity
import com.xenyaa.videoshot.data.library.entity.VideoEntity
import com.xenyaa.videoshot.data.repo.CacheRepo
import com.xenyaa.videoshot.data.repo.FakeLibraryRepo
import com.xenyaa.videoshot.data.repo.LibraryRepo
import com.xenyaa.videoshot.data.repo.model.FilterOption
import com.xenyaa.videoshot.data.repo.model.MonthFacet
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

private fun row(id: Long, date: String, place: String? = null) = ShotRow(
    id = id, videoId = "v1", atSec = id.toDouble(), source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
    eventDate = date, place = place, description = null,
)

/**
 * 首頁篩選的接線(階段 17,取代階段 10 的查詢分頁):開篩選抽屜、勾地點、套用後首頁只剩符合的圖,
 * 篩選中開 Lightbox;首頁月份標籤點擊在首頁套用只篩這一個,不切分頁。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootHomeFilterTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Repo : FakeLibraryRepo() {
        var homeItems = listOf(row(1L, "2026-03-01", place = "宜蘭"), row(2L, "2026-03-02", place = "礁溪"))
        var facets = listOf(MonthFacet("宜蘭", "place", 1))
        var searchResults = homeItems
        val deletedVideoIds = mutableListOf<String>()

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(homeItems, null)
        override suspend fun monthCounts() = emptyList<com.xenyaa.videoshot.data.repo.model.MonthCount>()
        override suspend fun monthFacets(month: String) = facets
        override suspend fun searchFacets(upToMonth: String?, limit: Int) = facets
        override suspend fun filterOptions(upToMonth: String?) =
            listOf(FilterOption("礁溪", true, "other", emptyList()))
        override suspend fun monthsMatching(places: Set<String>, tagNames: Set<String>) = listOf("2026-03")
        override suspend fun searchByFacets(places: Set<String>, tagNames: Set<String>, upToMonth: String?, after: ShotCursor?, limit: Int) =
            Page(searchResults.filter { it.place in places }, null)
        override suspend fun searchByFacetsCount(places: Set<String>, tagNames: Set<String>, upToMonth: String?) = searchResults.size
        // 詳情頁需要的兩個查詢——查詢分頁點進來的那張圖是 v1，跟首頁那張同一支影片
        override suspend fun shotsOfVideo(videoId: String) = searchResults.filter { it.videoId == videoId }
        override suspend fun videoById(videoId: String) =
            VideoEntity(videoId, "旅行影片", "c", "2026-03-01T00:00:00Z", 600, "public", null, 1L)
        override suspend fun deleteVideo(videoId: String) { deletedVideoIds += videoId }
    }

    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen = flowOf(true)
        override suspend fun markLightboxHintSeen() = Unit
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
                override suspend fun saveDraft(draft: com.xenyaa.videoshot.data.cache.entity.DraftEntity) = Unit
                override suspend fun currentDraft(): com.xenyaa.videoshot.data.cache.entity.DraftEntity? = null
                override suspend fun clearDraft() = Unit
                override suspend fun clearAll() = Unit
            },
            io = Dispatchers.Unconfined,
        )
        override val wizardData: WizardData = object : WizardData {
            // 詳情頁的播放器會在 init 就呼叫 loadPlayerInfo() → watchPage()（見 DetailViewModel）——
            // 本檔新增的刪除整支收藏測試會真的走到詳情頁，不能再丟 UnsupportedOperationException，
            // 跟著 AppRootDetailTest.kt 的手法回一個可用的 WatchPage。
            override suspend fun watchPage(videoId: String) = com.xenyaa.videoshot.core.youtube.WatchPage(
                com.xenyaa.videoshot.core.youtube.FetchResult.OK,
                com.xenyaa.videoshot.core.youtube.VideoMeta(videoId, "旅行影片", "c", "2026-03-01T00:00:00Z", 600, "public", true),
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
        override suspend fun listBackups() = error("這組測試不碰還原")
        override suspend fun restore(backup: com.xenyaa.videoshot.backup.RemoteBackup) = error("這組測試不碰還原")
    }

    private fun applyDrawerPlace(name: String) {
        compose.onNodeWithContentDescription("依地點與標籤篩選").performClick()
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithText("套用").performClick()
    }

    /** 設計決議 3、4：開抽屜勾一個地點、按【套用】，首頁縮圖牆只剩那個地點的圖。 */
    @Test
    fun 開篩選抽屜勾地點套用後首頁只剩符合的圖() {
        val repo = Repo()
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }
        compose.onNodeWithContentDescription("片段縮圖 00:01", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("片段縮圖 00:02", substring = true).assertIsDisplayed()

        applyDrawerPlace("礁溪")

        compose.onNodeWithContentDescription("依地點與標籤篩選")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已套用 1 個篩選條件"))
        compose.onNodeWithContentDescription("片段縮圖 00:02", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("片段縮圖 00:01", substring = true).assertDoesNotExist()
        // 還在首頁：沒有底部導覽的「查詢」格
        compose.onNodeWithText("查詢").assertDoesNotExist()
    }

    /** 篩選中點圖開 Lightbox（左右滑的範圍由首頁已篩過的清單決定）。 */
    @Test
    fun 篩選中點縮圖開啟Lightbox() {
        val repo = Repo()
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }

        applyDrawerPlace("礁溪")
        compose.onNodeWithContentDescription("片段縮圖 00:02", substring = true).performClick()

        compose.onNodeWithText("播放這一段").assertIsDisplayed()
    }

    /** 階段 17 設計決議 6：月份標籤點下去在首頁只篩這一個，不再跳到查詢分頁。 */
    @Test
    fun 首頁月份標籤點擊在首頁套用只篩這一個() {
        val repo = Repo()
        compose.setContent { VideoshotTheme { AppRoot(deps(repo)) {} } }

        // 首頁縮圖牆的月份標籤列——HomeScreen 的 MonthHeader,chip 文字就是地點名
        compose.onNodeWithText("宜蘭").performClick()

        // 還在首頁：篩選鈕帶「已套用 1 個篩選條件」，月份標籤隱藏，沒有跳到查詢結果
        compose.onNodeWithContentDescription("依地點與標籤篩選")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已套用 1 個篩選條件"))
        compose.onNodeWithText("宜蘭").assertDoesNotExist()
        compose.onNodeWithText("1 張", substring = true).assertDoesNotExist()
    }
}
