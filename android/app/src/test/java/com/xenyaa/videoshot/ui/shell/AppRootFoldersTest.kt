package com.xenyaa.videoshot.ui.shell

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import kotlinx.coroutines.withContext
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

    /**
     * 首頁 5 張、根層一個「旅行」資料夾（本層 2 張，跟首頁的 id 不重疊），
     * 「旅行」底下再掛一個子資料夾「宜蘭」（自己另外 2 張）——N4 已知 #13 的回歸測試要用到
     * 這一層巢狀，才驗得出「父資料夾頁的子卡片張數要在返回時重查」。
     */
    private class Repo : com.xenyaa.videoshot.data.repo.FakeLibraryRepo() {
        var homeItems = (1L..5L).map { row(it, 30.0) }
        var folderItems = listOf(row(11L, 30.0), row(12L, 90.0))
        var childItems = listOf(row(21L, 45.0), row(22L, 50.0))
        val added = mutableListOf<Pair<Long, Long>>()

        /** Important 2 的回滾測試用：逼 [addShotToFolder] 丟例外，不是靜靜地不寫。 */
        var addShotToFolderShouldFail = false

        override suspend fun homeFeed(after: ShotCursor?, limit: Int, upToMonth: String?) = Page(homeItems, null)
        override suspend fun shotCount(upToMonth: String?) = homeItems.size

        override suspend fun folderCards(parentId: Long?): List<FolderCard> = when (parentId) {
            // 「旅行」的張數含子孫（規格第六節）：自己 2 張 ＋「宜蘭」的張數
            null -> listOf(FolderCard(1, "旅行", folderItems.size + childItems.size, 0, emptyList()))
            1L -> listOf(FolderCard(2, "宜蘭", childItems.size, 0, emptyList()))
            else -> emptyList()
        }

        override suspend fun folderNode(id: Long): FolderNode? = when (id) {
            1L -> FolderNode(1, null, "旅行", 1)
            2L -> FolderNode(2, 1, "宜蘭", 2)
            else -> null
        }

        /** 就地移除（`onShotDeleted`）跟整頁重查（`reload()`）的分野就在這裡——前者不重打這支。 */
        var folderShotsCalls = 0

        override suspend fun folderShots(folderId: Long, after: FolderCursor?, limit: Int): FolderPage {
            folderShotsCalls++
            return when (folderId) {
                1L -> FolderPage(folderItems, null)
                2L -> FolderPage(childItems, null)
                else -> FolderPage(emptyList(), null)
            }
        }

        override suspend fun folderShotCount(folderId: Long): Int = when (folderId) {
            1L -> folderItems.size
            2L -> childItems.size
            else -> 0
        }

        override suspend fun folderTree(): List<FolderNode> =
            listOf(FolderNode(1, null, "旅行", 1), FolderNode(2, 1, "宜蘭", 2))

        override suspend fun addShotToFolder(shotId: Long, folderId: Long, atSec: Long) {
            if (addShotToFolderShouldFail) error("模擬寫入失敗（磁碟／SQLite 之類，不是名稱規則那種 IllegalArgumentException）")
            added += shotId to folderId
        }

        // N4 的回歸測試用：真的要能刪掉，folderCards／folderShotCount 才會跟著變，
        // 才驗得出「刪除之後，分類分頁與資料夾頁顯示的張數是新的」
        override suspend fun shotById(id: Long): ShotRow? =
            (homeItems + folderItems + childItems).find { it.id == id }

        override suspend fun shotsOfVideo(videoId: String): List<ShotRow> =
            (homeItems + folderItems + childItems).filter { it.videoId == videoId }

        override suspend fun deleteShot(id: Long) {
            homeItems = homeItems.filterNot { it.id == id }
            folderItems = folderItems.filterNot { it.id == id }
            childItems = childItems.filterNot { it.id == id }
        }

        // 【加入分類】取消勾選那條路徑的回歸測試用：要能反映真實的成員關係，
        // 「已經在裡面」才勾得起來，取消勾選也要真的從那個資料夾拔掉
        override suspend fun foldersOf(shotId: Long): Set<Long> = buildSet {
            if (folderItems.any { it.id == shotId }) add(1L)
            if (childItems.any { it.id == shotId }) add(2L)
        }

        val removed = mutableListOf<Pair<Long, Long>>()

        /**
         * `withContext(Dispatchers.Default)` 不是多餘的——真正的 `RoomLibraryRepo.removeShotFromFolder`
         * 就是這樣包的（見 `RoomLibraryRepo.kt`），這裡假的也要真的跳一次 dispatcher 才會有真正
         * 的掛起點。少了它，呼叫端那段 `scope.launch { ... }` 到 `folderVm.reload()` 整段會在
         * 同一輪執行完，Compose 沒有機會在 `items` 被清空的那一瞬間插進來重組，
         * 「Lightbox 誤判清單是空的自動關掉」這個 bug 就測不出來（假到看不出真的問題）。
         */
        override suspend fun removeShotFromFolder(shotId: Long, folderId: Long) = withContext(Dispatchers.Default) {
            removed += shotId to folderId
            when (folderId) {
                1L -> folderItems = folderItems.filterNot { it.id == shotId }
                2L -> childItems = childItems.filterNot { it.id == shotId }
            }
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

    /**
     * Important 2：寫失敗不能停在「畫面看起來勾上了，DB 其實沒寫」這種狀態——
     * 勾勾要退回 repo 的真實狀態（這裡就是退回沒勾），還要讓使用者知道要再試一次。
     */
    @Test
    fun 加入分類寫入失敗會把勾勾改回去並顯示錯誤() {
        show()
        repo.addShotToFolderShouldFail = true

        compose.onAllNodesWithContentDescription("片段縮圖 00:30")[0].performClick()
        compose.onNodeWithContentDescription("加入分類").performClick()
        compose.onNodeWithContentDescription("旅行").performClick()
        compose.waitForIdle()

        assertEquals("寫失敗就不該真的寫進去", emptyList<Pair<Long, Long>>(), repo.added)
        compose.onNodeWithContentDescription("旅行").assertIsOff()
        compose.onNodeWithText("加入分類失敗，請再試一次").assertIsDisplayed()
    }

    /**
     * N4 點 1 的回歸測試：`AppRoot.kt` 的刪除分支原本只呼叫 `homeVm.onShotDeleted` 與
     * `folderVm?.reload()`，漏了 `foldersVm.reload()`——加入分類那條路徑兩個都呼叫了，
     * 刪除分支沒對齊。於是首頁 Lightbox 刪掉一張屬於某個資料夾的圖，切到分類分頁，
     * 卡片張數還是舊的。
     */
    @Test
    fun 首頁刪圖後分類分頁的張數會更新() {
        // 讓首頁的第一張圖同時也是「旅行」資料夾的成員，刪除才會反映在資料夾張數上
        repo.folderItems = listOf(row(1L, 30.0), row(12L, 90.0))
        show()

        compose.onNodeWithText("分類").performClick()
        compose.onNodeWithText("4 張").assertIsDisplayed() // 旅行：folderItems(2) + childItems(2)
        compose.onNodeWithText("首頁").performClick()

        compose.onAllNodesWithContentDescription("片段縮圖 00:30")[0].performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刪除這張收藏").performClick()
        compose.onNodeWithText("刪除").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("關閉").performClick()

        compose.onNodeWithText("分類").performClick()
        compose.onNodeWithText("3 張").assertIsDisplayed()
    }

    /**
     * N4 點 2／已知 #13 的回歸測試：父資料夾頁 → 子資料夾頁 → 在子頁 Lightbox 刪圖 →
     * 連按兩次返回，父頁上半的子資料夾卡片與清單頁的卡片都要顯示新的張數，不能停在
     * 進入子資料夾之前查到的舊值（`backFromFolder()` 原本只在落回 `Dest.Root` 時重查
     * `foldersVm`，落回 `Dest.Folder`——回到父資料夾頁——完全沒有重查動作）。
     */
    @Test
    fun 子資料夾頁刪圖返回後父頁與清單頁的張數是新的() {
        show()
        compose.onNodeWithText("分類").performClick()
        compose.onNodeWithText("4 張").assertIsDisplayed() // 旅行：2（自己）+ 2（宜蘭）

        compose.onNodeWithText("旅行").performClick() // 進「旅行」
        compose.onNodeWithText("2 張").assertIsDisplayed() // 子卡片「宜蘭」

        compose.onNodeWithText("宜蘭").performClick() // 進「宜蘭」
        compose.onAllNodesWithContentDescription("片段縮圖 00:45")[0].performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刪除這張收藏").performClick()
        compose.onNodeWithText("刪除").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("關閉").performClick()

        // 連按兩次返回：先回「旅行」（父頁），再回清單頁
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("1 張").assertIsDisplayed() // 子卡片「宜蘭」剩 1 張

        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("3 張").assertIsDisplayed() // 清單頁「旅行」的聚合張數也是新的
    }

    /**
     * N3 的回歸測試（【加入分類】取消勾選路徑，跟 Lightbox【刪除】同一種形狀）：
     * 在資料夾頁開 Lightbox、取消勾選的正是目前開著的那個資料夾時，那張圖要從本層清單
     * 就地消失，不能整頁 `reload()`——`reload()` 會把 `items` 先清空再重撈，不只多打一次
     * `repo.folderShots`，清單在暫時變空的那一瞬間還可能被 `LightboxScreen` 的
     * `items.isEmpty()` 誤判成「沒東西可看」自動把自己關掉（同 N3 的刪除路徑）。
     * 這裡用「有沒有多打一次 `folderShots`」當判別依據——比等一個時機敏感的自動關閉
     * 更穩定，也直接對應「就地移除」跟「整頁重查」這兩種做法的本質差異。
     */
    @Test
    fun 資料夾頁取消勾選目前資料夾會就地移除不重打folderShots() {
        show()
        compose.onNodeWithText("分類").performClick()
        compose.onNodeWithText("旅行").performClick()
        compose.onAllNodesWithContentDescription("片段縮圖 00:30")[0].performClick()
        compose.onNodeWithText("第 1 / 共 2 張").assertIsDisplayed()
        val callsBeforeToggle = repo.folderShotsCalls

        compose.onNodeWithContentDescription("加入分類").performClick()
        compose.onNodeWithContentDescription("旅行").assertIsOn() // 這張圖本來就是「旅行」的成員
        compose.onNodeWithContentDescription("旅行").performClick() // 取消勾選
        compose.waitForIdle()

        assertEquals("repo 要真的收到移出", listOf(11L to 1L), repo.removed)
        assertEquals(
            "就地移除不該再打一次 folderShots——那是整頁 reload() 才會做的事",
            callsBeforeToggle,
            repo.folderShotsCalls,
        )
        // Lightbox 還留在原地，共 M 張也已經就地少一張
        compose.onNodeWithText("第 1 / 共 1 張").assertIsDisplayed()
    }
}
