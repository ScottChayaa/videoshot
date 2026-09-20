package com.xenyaa.videoshot.ui.shell

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.xenyaa.videoshot.core.folders.FolderSort
import com.xenyaa.videoshot.core.paging.FolderCursor
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
import com.xenyaa.videoshot.data.repo.model.FolderCard
import com.xenyaa.videoshot.data.repo.model.FolderNode
import com.xenyaa.videoshot.data.repo.model.FolderPage
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

private fun row(id: Long, atSec: Double) = ShotRow(
    id = id, videoId = "v1", atSec = atSec, source = "storyboard", frameIndex = id.toInt(), sbLevel = 3,
    eventDate = "2026-03-05", place = null, description = null,
)

/**
 * 分類分頁的接線：清單頁 → 資料夾頁 → Lightbox（來源切成資料夾本層）→ 加入分類。
 *
 * 跟 [AppRootLightboxTest] 用同一種手法組 fixture，但不共用——這兩組測試在意的假資料
 * 完全不同（那邊是首頁分頁與刪除／編輯，這邊是資料夾樹與加入分類）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppRootFoldersTest {

    @get:Rule val compose = createComposeRule()

    /** 首頁 5 張、根層一個「旅行」資料夾（本層 2 張，跟首頁的 id 不重疊）。 */
    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        val homeItems = (1L..5L).map { row(it, 30.0) }
        val folderItems = listOf(row(11L, 30.0), row(12L, 90.0))
        val added = mutableListOf<Pair<Long, Long>>()

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(homeItems, null)
        override suspend fun shotCount(upToMonth: String?) = homeItems.size

        override suspend fun folderCards(parentId: Long?): List<FolderCard> =
            if (parentId == null) listOf(FolderCard(1, "旅行", 2, 0, emptyList())) else emptyList()

        override suspend fun folderNode(id: Long): FolderNode? =
            if (id == 1L) FolderNode(1, null, "旅行", 1) else null

        override suspend fun folderShots(folderId: Long, after: FolderCursor?, limit: Int): FolderPage =
            if (folderId == 1L) FolderPage(folderItems, null) else FolderPage(emptyList(), null)

        override suspend fun folderShotCount(folderId: Long): Int = if (folderId == 1L) folderItems.size else 0

        override suspend fun folderTree(): List<FolderNode> = listOf(FolderNode(1, null, "旅行", 1))

        override suspend fun addShotToFolder(shotId: Long, folderId: Long, atSec: Long) {
            added += shotId to folderId
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
        override suspend fun thumbFor(shot: ShotRow) = ThumbSource.LocalFile(File("/fake/${shot.id}"))
        override fun fileOf(key: ThumbKey) = File("/unused")
        override fun exists(key: ThumbKey) = false
        override suspend fun delete(key: ThumbKey) = Unit
        override suspend fun deleteVideo(videoId: String) = Unit
    }

    private class NoopWizardData : WizardData {
        override suspend fun watchPage(videoId: String) = WatchPage(FetchResult.OK, null, null)
        override suspend fun recentVideos(limit: Int): List<RecentVideo> = emptyList()
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

    private class FakeShellSettings : ShellSettings {
        override val filterStrength = flowOf(FilterStrength.MEDIUM)
        override val gridHintSeen = flowOf(true)
        override suspend fun markGridHintSeen() = Unit
        override val lightboxHintSeen = flowOf(true)
        override suspend fun markLightboxHintSeen() = Unit
        override val folderSort = flowOf(FolderSort.NAME_ASC)
        override suspend fun setFolderSort(value: FolderSort) = Unit
    }

    private val repo = Repo()

    private val deps = object : AppRootDeps {
        private val cache = FakeCacheRepo()
        private val thumbs = FakeThumbs()

        override val libraryRepo: LibraryRepo = repo
        override val thumbLoader = ThumbLoader(
            thumbs = thumbs,
            decodeFile = { ImageBitmap(1, 1) },
            decodeBytes = { null },
            cover = { null },
        )
        override val shotDeleter = ShotDeleter(repo, thumbs, cache, Dispatchers.Default)
        override val wizardData: WizardData = NoopWizardData()
        override val haptics = FakeHaptics()
        override val settings: ShellSettings = FakeShellSettings()
        override fun frameSourceFor(video: LoadedVideo): FrameSource = error("這組測試不碰精靈第二步")
        override fun manualImagesFor(videoId: String) = error("這組測試不碰手動圖")
        override fun captureFor(player: com.xenyaa.videoshot.player.Player) = null
    }

    private fun show() {
        compose.setContent {
            VideoshotTheme {
                AppRoot(deps, onExitApp = {})
            }
        }
    }

    @Test
    fun 分類分頁列出資料夾點進去看得到內容() {
        show()
        compose.onNodeWithText("分類").performClick()
        compose.onNodeWithText("旅行").performClick()
        // 資料夾頁：標題是名稱，本層有兩張圖
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("片段縮圖 00:30").assertCountEquals(1)
    }

    @Test
    fun 從資料夾頁開的_Lightbox_共幾張是資料夾的張數() {
        show()
        compose.onNodeWithText("分類").performClick()
        compose.onNodeWithText("旅行").performClick()
        compose.onAllNodesWithContentDescription("片段縮圖 00:30")[0].performClick()
        // 首頁有 5 張、這個資料夾只有 2 張
        compose.onNodeWithText("第 1 / 共 2 張").assertIsDisplayed()
    }

    @Test
    fun 返回鍵從_Lightbox_退回資料夾頁再退回清單頁() {
        show()
        compose.onNodeWithText("分類").performClick()
        compose.onNodeWithText("旅行").performClick()
        compose.onAllNodesWithContentDescription("片段縮圖 00:30")[0].performClick()

        compose.onNodeWithContentDescription("關閉").performClick()
        compose.onNodeWithContentDescription("返回").performClick()

        compose.onNodeWithText("1 個分類").assertIsDisplayed()
    }

    @Test
    fun 加入分類會寫進_repo() {
        show()
        // 首頁 → 開第一張的 Lightbox → 加入分類 → 勾「旅行」
        compose.onAllNodesWithContentDescription("片段縮圖 00:30")[0].performClick()
        compose.onNodeWithContentDescription("加入分類").performClick()
        compose.onNodeWithContentDescription("旅行").performClick()

        assertEquals(listOf(1L to 1L), repo.added)
    }
}
